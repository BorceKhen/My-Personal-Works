package com.deckcast.app.host

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.deckcast.app.model.InputPacket
import java.util.concurrent.atomic.AtomicReference

/**
 * AccessibilityService executing remote normalized pointer gestures (`dispatchGesture()`)
 * and keyboard/navigation actions on Android Host devices.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DeckCastA11y"
        private val instanceRef = AtomicReference<RemoteAccessibilityService?>(null)

        fun getInstance(): RemoteAccessibilityService? = instanceRef.get()
        fun isEnabled(): Boolean = instanceRef.get() != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Active continuous gesture tracking (API 26+ StrokeDescription.continueStroke)
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var lastX = 0f
    private var lastY = 0f
    private var isGestureDown = false
    private var isDispatching = false
    private var pendingEndAfterSegment = false
    private val pendingMovePoints = ArrayDeque<Pair<Float, Float>>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
        Log.i(TAG, "RemoteAccessibilityService connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instanceRef.compareAndSet(this, null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instanceRef.compareAndSet(this, null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun handleInputPacket(packet: InputPacket) {
        when (packet) {
            is InputPacket.Pointer -> mainHandler.post { handlePointer(packet) }
            is InputPacket.Key -> mainHandler.post { handleKey(packet) }
        }
    }

    private fun handlePointer(packet: InputPacket.Pointer) {
        val (screenW, screenH) = getScreenDimensions()
        val px = (packet.x.coerceIn(0f, 1f) * screenW).coerceIn(1f, screenW - 1f)
        val py = (packet.y.coerceIn(0f, 1f) * screenH).coerceIn(1f, screenH - 1f)

        when (packet.action) {
            "DOWN" -> {
                resetGestureState()
                isGestureDown = true
                lastX = px
                lastY = py
                val path = Path().apply { moveTo(px, py) }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 40L, true)
                dispatchStroke(stroke, isFinal = false)
            }

            "MOVE" -> {
                if (!isGestureDown) return
                if (isDispatching || activeStroke == null) {
                    if (pendingMovePoints.size < 32) {
                        pendingMovePoints.addLast(px to py)
                    }
                } else {
                    continueToPoint(px, py, willContinue = true)
                }
            }

            "UP" -> {
                if (!isGestureDown) return
                isGestureDown = false
                if (isDispatching || activeStroke == null) {
                    pendingMovePoints.addLast(px to py)
                    pendingEndAfterSegment = true
                } else {
                    continueToPoint(px, py, willContinue = false)
                }
            }

            "SCROLL" -> {
                val deltaY = packet.scrollY * 180f
                val endY = (py + deltaY).coerceIn(1f, screenH - 1f)
                val path = Path().apply {
                    moveTo(px, py)
                    lineTo(px, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 120L, false)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
            }
        }
    }

    private fun continueToPoint(nx: Float, ny: Float, willContinue: Boolean) {
        val prev = activeStroke ?: return
        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(nx, ny)
        }
        lastX = nx
        lastY = ny
        val nextStroke = prev.continueStroke(path, 0L, 25L, willContinue)
        dispatchStroke(nextStroke, isFinal = !willContinue)
    }

    private fun dispatchStroke(stroke: GestureDescription.StrokeDescription, isFinal: Boolean) {
        isDispatching = true
        activeStroke = if (isFinal) null else stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                isDispatching = false
                drainPendingMoves(isFinal)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                isDispatching = false
                resetGestureState()
            }
        }, mainHandler)

        if (!accepted) {
            isDispatching = false
            resetGestureState()
        }
    }

    private fun drainPendingMoves(justFinished: Boolean) {
        if (justFinished) {
            resetGestureState()
            return
        }
        val nextPoint = pendingMovePoints.removeFirstOrNull()
        if (nextPoint != null) {
            val shouldEnd = pendingEndAfterSegment && pendingMovePoints.isEmpty()
            continueToPoint(nextPoint.first, nextPoint.second, willContinue = !shouldEnd)
        } else if (pendingEndAfterSegment && activeStroke != null) {
            pendingEndAfterSegment = false
            continueToPoint(lastX, lastY, willContinue = false)
        }
    }

    private fun resetGestureState() {
        activeStroke = null
        isGestureDown = false
        isDispatching = false
        pendingEndAfterSegment = false
        pendingMovePoints.clear()
    }

    private fun handleKey(packet: InputPacket.Key) {
        if (packet.action != "DOWN") return

        when (packet.keyCode) {
            KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_APP_SWITCH -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            KeyEvent.KEYCODE_ENTER -> {
                val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
            }
            KeyEvent.KEYCODE_DEL -> {
                val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
                val current = focused.text?.toString().orEmpty()
                if (current.isNotEmpty()) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            current.dropLast(1)
                        )
                    }
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
            else -> {
                if (packet.text.isNotEmpty()) {
                    val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
                    val current = focused.text?.toString().orEmpty()
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            current + packet.text
                        )
                    }
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
        }
    }

    private fun getScreenDimensions(): Pair<Float, Float> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
        }
    }
}
