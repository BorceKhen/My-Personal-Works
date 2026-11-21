package com.deckcast.app.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.deckcast.app.R
import com.deckcast.app.model.DiscoveredHost
import com.deckcast.app.model.InputPacket
import com.deckcast.app.webrtc.RemoteDeviceSession
import com.deckcast.app.webrtc.TextureViewRenderer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Manages independent, draggable, resizable floating overlay windows (`TYPE_APPLICATION_OVERLAY`)
 * for each simultaneous RemoteDeviceSession using TextureViewRenderer.
 */
class OverlayManager(
    context: Context,
    private val sessionManager: SessionManager
) {

    private data class OverlayEntry(
        val sessionId: String,
        val host: DiscoveredHost,
        val rootView: View,
        val expandedContainer: View,
        val minimizedBubble: View,
        val renderer: TextureViewRenderer,
        val statusDot: View,
        val keyboardInput: EditText,
        val keyboardBtn: TextView,
        val layoutParams: WindowManager.LayoutParams,
        var savedWidth: Int,
        var savedHeight: Int,
        var isMinimized: Boolean = false,
        var isKeyboardEnabled: Boolean = false
    )

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inputMethodManager =
        appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val entries = ConcurrentHashMap<String, OverlayEntry>()

    private val density = appContext.resources.displayMetrics.density
    private val minWidthPx = (240 * density).toInt()
    private val minHeightPx = (180 * density).toInt()
    private val defaultWidthPx = (320 * density).toInt()
    private val defaultHeightPx = (480 * density).toInt()
    private val bubbleSizePx = (56 * density).toInt()

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun openFloatingWindow(host: DiscoveredHost) {
        mainHandler.post {
            val inflater = LayoutInflater.from(appContext)
            val rootView = inflater.inflate(R.layout.overlay_window, null)

            val expandedContainer = rootView.findViewById<View>(R.id.expanded_container)
            val minimizedBubble = rootView.findViewById<View>(R.id.minimized_bubble)
            val dragBar = rootView.findViewById<View>(R.id.drag_handle_bar)
            val statusDot = rootView.findViewById<View>(R.id.view_status_dot)
            val hostTitle = rootView.findViewById<TextView>(R.id.tv_host_name)
            val bubbleLabel = rootView.findViewById<TextView>(R.id.tv_bubble_label)
            val keyboardBtn = rootView.findViewById<TextView>(R.id.btn_keyboard)
            val minimizeBtn = rootView.findViewById<TextView>(R.id.btn_minimize)
            val closeBtn = rootView.findViewById<TextView>(R.id.btn_close)
            val resizeHandle = rootView.findViewById<View>(R.id.resize_handle)
            val renderer = rootView.findViewById<TextureViewRenderer>(R.id.texture_video_renderer)
            val keyboardInput = rootView.findViewById<EditText>(R.id.et_keyboard_input)

            hostTitle.text = host.deviceName
            bubbleLabel.text = host.deviceName.take(2).uppercase().ifEmpty { "DC" }

            // Stagger each new window slightly so multiple floating hosts stay visibly distinct
            val offset = (entries.size % 5) * (24 * density).toInt()
            val params = WindowManager.LayoutParams(
                defaultWidthPx,
                defaultHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (24 * density).toInt() + offset
                y = (80 * density).toInt() + offset
            }

            val session = sessionManager.openSession(
                host = host,
                renderer = renderer,
                onStateChanged = { state ->
                    mainHandler.post {
                        val color = when (state) {
                            RemoteDeviceSession.SessionState.CONNECTED -> 0xFF2EF2A0.toInt()
                            RemoteDeviceSession.SessionState.CONNECTING -> 0xFFFFB020.toInt()
                            RemoteDeviceSession.SessionState.DISCONNECTED,
                            RemoteDeviceSession.SessionState.FAILED -> 0xFFFF4D6D.toInt()
                        }
                        statusDot.background?.setTint(color)
                    }
                },
                onClosed = { }
            )

            val entry = OverlayEntry(
                sessionId = session.sessionId,
                host = host,
                rootView = rootView,
                expandedContainer = expandedContainer,
                minimizedBubble = minimizedBubble,
                renderer = renderer,
                statusDot = statusDot,
                keyboardInput = keyboardInput,
                keyboardBtn = keyboardBtn,
                layoutParams = params,
                savedWidth = defaultWidthPx,
                savedHeight = defaultHeightPx
            )
            entries[session.sessionId] = entry

            windowManager.addView(rootView, params)

            wireDragBehavior(dragBar, entry)
            wireBubbleBehavior(minimizedBubble, entry)
            wireResizeBehavior(resizeHandle, entry)
            wireTouchForwarding(renderer, entry)
            wireKeyboardInput(keyboardInput, entry)

            minimizeBtn.setOnClickListener { toggleMinimize(entry, minimize = true) }
            keyboardBtn.setOnClickListener { toggleKeyboard(entry) }
            closeBtn.setOnClickListener { closeWindow(entry.sessionId) }
        }
    }

    fun closeWindow(sessionId: String) {
        mainHandler.post {
            val entry = entries.remove(sessionId) ?: return@post
            if (entry.isKeyboardEnabled) {
                inputMethodManager.hideSoftInputFromWindow(entry.keyboardInput.windowToken, 0)
            }
            runCatching { windowManager.removeViewImmediate(entry.rootView) }
            sessionManager.closeSession(sessionId)
        }
    }

    fun closeAll() {
        entries.keys.toList().forEach { closeWindow(it) }
    }

    private fun bringToFront(entry: OverlayEntry) {
        if (!entries.containsKey(entry.sessionId)) return
        // Re-adding the view moves it to the top of WindowManager's overlay Z-order.
        if (entry.rootView.isAttachedToWindow) {
            runCatching {
                windowManager.removeViewImmediate(entry.rootView)
                windowManager.addView(entry.rootView, entry.layoutParams)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireDragBehavior(handle: View, entry: OverlayEntry) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bringToFront(entry)
                    startX = entry.layoutParams.x
                    startY = entry.layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    entry.layoutParams.x = startX + (event.rawX - touchStartX).toInt()
                    entry.layoutParams.y = startY + (event.rawY - touchStartY).toInt()
                    runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireBubbleBehavior(bubble: View, entry: OverlayEntry) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var dragged = false

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bringToFront(entry)
                    startX = entry.layoutParams.x
                    startY = entry.layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        dragged = true
                        entry.layoutParams.x = startX + dx.toInt()
                        entry.layoutParams.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        toggleMinimize(entry, minimize = false)
                    }
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireResizeBehavior(handle: View, entry: OverlayEntry) {
        var startW = 0
        var startH = 0
        var touchStartX = 0f
        var touchStartY = 0f

        handle.setOnTouchListener { _, event ->
            val maxW = appContext.resources.displayMetrics.widthPixels
            val maxH = appContext.resources.displayMetrics.heightPixels

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bringToFront(entry)
                    startW = entry.layoutParams.width
                    startH = entry.layoutParams.height
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val nextW = (startW + (event.rawX - touchStartX).toInt()).coerceIn(minWidthPx, maxW)
                    val nextH = (startH + (event.rawY - touchStartY).toInt()).coerceIn(minHeightPx, maxH)
                    entry.layoutParams.width = nextW
                    entry.layoutParams.height = nextH
                    entry.savedWidth = nextW
                    entry.savedHeight = nextH
                    runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireTouchForwarding(renderer: TextureViewRenderer, entry: OverlayEntry) {
        renderer.setOnTouchListener { v, event ->
            val w = v.width.coerceAtLeast(1).toFloat()
            val h = v.height.coerceAtLeast(1).toFloat()
            val normX = (event.x / w).coerceIn(0f, 1f)
            val normY = (event.y / h).coerceIn(0f, 1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bringToFront(entry)
                    sessionManager.sendInput(
                        entry.sessionId,
                        InputPacket.Pointer(action = "DOWN", x = normX, y = normY)
                    )
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    sessionManager.sendInput(
                        entry.sessionId,
                        InputPacket.Pointer(action = "MOVE", x = normX, y = normY)
                    )
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    sessionManager.sendInput(
                        entry.sessionId,
                        InputPacket.Pointer(action = "UP", x = normX, y = normY)
                    )
                    true
                }

                else -> false
            }
        }
    }

    private fun toggleMinimize(entry: OverlayEntry, minimize: Boolean) {
        entry.isMinimized = minimize
        if (minimize) {
            if (entry.isKeyboardEnabled) {
                toggleKeyboard(entry)
            }
            entry.savedWidth = entry.layoutParams.width
            entry.savedHeight = entry.layoutParams.height
            entry.expandedContainer.visibility = View.GONE
            entry.minimizedBubble.visibility = View.VISIBLE
            entry.layoutParams.width = bubbleSizePx
            entry.layoutParams.height = bubbleSizePx
        } else {
            entry.minimizedBubble.visibility = View.GONE
            entry.expandedContainer.visibility = View.VISIBLE
            entry.layoutParams.width = entry.savedWidth
            entry.layoutParams.height = entry.savedHeight
        }
        runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
    }

    private fun toggleKeyboard(entry: OverlayEntry) {
        bringToFront(entry)
        entry.isKeyboardEnabled = !entry.isKeyboardEnabled
        if (entry.isKeyboardEnabled) {
            // Clear FLAG_NOT_FOCUSABLE for this window only so IME targets its EditText
            entry.layoutParams.flags =
                entry.layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            entry.keyboardInput.visibility = View.VISIBLE
            entry.keyboardBtn.setTextColor(0xFF00E5FF.toInt())
            runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
            entry.keyboardInput.requestFocus()
            mainHandler.postDelayed({
                inputMethodManager.showSoftInput(entry.keyboardInput, InputMethodManager.SHOW_IMPLICIT)
            }, 80L)
        } else {
            inputMethodManager.hideSoftInputFromWindow(entry.keyboardInput.windowToken, 0)
            entry.keyboardInput.clearFocus()
            entry.keyboardInput.visibility = View.GONE
            entry.keyboardBtn.setTextColor(0xFF94A3B8.toInt())
            entry.layoutParams.flags =
                entry.layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { windowManager.updateViewLayout(entry.rootView, entry.layoutParams) }
        }
    }

    private fun wireKeyboardInput(editText: EditText, entry: OverlayEntry) {
        var suppressWatcher = false

        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BACK -> {
                        sessionManager.sendInput(
                            entry.sessionId,
                            InputPacket.Key(action = "DOWN", keyCode = keyCode, text = "")
                        )
                        sessionManager.sendInput(
                            entry.sessionId,
                            InputPacket.Key(action = "UP", keyCode = keyCode, text = "")
                        )
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressWatcher || count <= 0 || s == null) return
                val inserted = s.subSequence(start, start + count).toString()
                if (inserted.isNotEmpty()) {
                    sessionManager.sendInput(
                        entry.sessionId,
                        InputPacket.Key(action = "DOWN", keyCode = 0, text = inserted)
                    )
                    sessionManager.sendInput(
                        entry.sessionId,
                        InputPacket.Key(action = "UP", keyCode = 0, text = inserted)
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher || s == null || s.isEmpty()) return
                suppressWatcher = true
                s.clear()
                suppressWatcher = false
            }
        })
    }
}
