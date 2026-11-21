package com.deckcast.app.webrtc

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch

/**
 * TextureView-backed WebRTC VideoSink using org.webrtc.EglRenderer.
 * Prevents SurfaceView Z-order punching/clipping when multiple floating overlay windows overlap.
 */
class TextureViewRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener, VideoSink {

    private val eglRenderer = EglRenderer("DeckCastTextureRenderer")
    private var rendererEvents: RendererCommon.RendererEvents? = null
    private var rotatedFrameWidth = 0
    private var rotatedFrameHeight = 0
    private var isInitialized = false

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun init(
        sharedContext: EglBase.Context,
        events: RendererCommon.RendererEvents? = null
    ) {
        if (isInitialized) return
        ThreadUtils.checkIsOnMainThread()
        this.rendererEvents = events
        rotatedFrameWidth = 0
        rotatedFrameHeight = 0

        eglRenderer.init(
            sharedContext,
            EglBase.CONFIG_PLAIN,
            GlRectDrawer()
        )
        isInitialized = true

        if (isAvailable && surfaceTexture != null) {
            eglRenderer.createEglSurface(surfaceTexture)
            updateAspectRatio(width, height)
        }
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    fun release() {
        if (!isInitialized) return
        isInitialized = false
        eglRenderer.release()
    }

    override fun onFrame(frame: VideoFrame) {
        if (!isInitialized) return
        val rotatedW = frame.rotatedWidth
        val rotatedH = frame.rotatedHeight
        if (rotatedFrameWidth != rotatedW || rotatedFrameHeight != rotatedH) {
            val isFirst = rotatedFrameWidth == 0 && rotatedFrameHeight == 0
            rotatedFrameWidth = rotatedW
            rotatedFrameHeight = rotatedH
            post {
                if (isFirst) {
                    rendererEvents?.onFirstFrameRendered()
                }
                rendererEvents?.onFrameResolutionChanged(rotatedW, rotatedH, frame.rotation)
            }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        if (isInitialized) {
            eglRenderer.createEglSurface(surface)
        }
        updateAspectRatio(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        updateAspectRatio(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        ThreadUtils.checkIsOnMainThread()
        if (isInitialized) {
            val latch = CountDownLatch(1)
            eglRenderer.releaseEglSurface { latch.countDown() }
            ThreadUtils.awaitUninterruptibly(latch)
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun updateAspectRatio(viewWidth: Int, viewHeight: Int) {
        if (viewWidth > 0 && viewHeight > 0 && isInitialized) {
            eglRenderer.setLayoutAspectRatio(viewWidth.toFloat() / viewHeight.toFloat())
        }
    }
}
