package com.deckcast.app.webrtc

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * Shared WebRTC PeerConnectionFactory & EGL context configured for hardware H.264 acceleration.
 */
object WebRtcFactoryProvider {

    @Volatile
    private var initialized = false

    val eglBase: EglBase by lazy { EglBase.create() }

    @Synchronized
    fun getFactory(context: Context): PeerConnectionFactory {
        if (!initialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            initialized = true
        }

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,  // enableIntelVp8Encoder
            true   // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
}
