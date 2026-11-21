package com.deckcast.app.host

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deckcast.app.R
import com.deckcast.app.model.InputPacket
import com.deckcast.app.model.SignalingMessage
import com.deckcast.app.network.LocalSignalingServer
import com.deckcast.app.network.NsdDiscoveryManager
import com.deckcast.app.webrtc.WebRtcFactoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Host Mode Foreground Service (`mediaProjection`) capturing the device screen via
 * ScreenCapturerAndroid (capped at 720p @ 60fps for low-latency multi-window streaming),
 * embedding a Ktor WebSocket signaling server, advertising `_remotedisplay._tcp.`, and
 * forwarding DataChannel "input" events to RemoteAccessibilityService.
 */
class ScreenHostService : Service() {

    companion object {
        private const val TAG = "DeckCastHostSvc"
        private const val CHANNEL_ID = "deckcast_host_channel"
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "capture_width"
        const val EXTRA_HEIGHT = "capture_height"
        const val EXTRA_FPS = "capture_fps"
        const val ACTION_STOP_HOST = "com.deckcast.app.action.STOP_HOST"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private data class HostPeer(
        val clientId: String,
        val sessionId: String,
        val peerConnection: PeerConnection,
        var remoteDescriptionSet: Boolean = false,
        val pendingCandidates: MutableList<IceCandidate> = mutableListOf()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peersByClient = ConcurrentHashMap<String, HostPeer>()

    private var signalingServer: LocalSignalingServer? = null
    private var nsdManager: NsdDiscoveryManager? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_HOST) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        val width = intent?.getIntExtra(EXTRA_WIDTH, 1280) ?: 1280
        val height = intent?.getIntExtra(EXTRA_HEIGHT, 720) ?: 720
        val fps = intent?.getIntExtra(EXTRA_FPS, 60) ?: 60

        startScreenCapture(resultData, width, height, fps)
        startSignalingAndNsd()
        isRunning = true

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        nsdManager?.unregisterHost()
        nsdManager = null

        signalingServer?.stop()
        signalingServer = null

        peersByClient.values.forEach {
            runCatching { it.peerConnection.close() }
            runCatching { it.peerConnection.dispose() }
        }
        peersByClient.clear()

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null

        runCatching { videoSource?.dispose() }
        videoSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        scope.cancel()
        super.onDestroy()
    }

    private fun startScreenCapture(permissionData: Intent, width: Int, height: Int, fps: Int) {
        val factory = WebRtcFactoryProvider.getFactory(applicationContext)
        val capturer = ScreenCapturerAndroid(
            permissionData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    stopSelf()
                }
            }
        )
        videoCapturer = capturer

        val texHelper = SurfaceTextureHelper.create(
            "DeckCastCaptureThread",
            WebRtcFactoryProvider.eglBase.eglBaseContext
        )
        surfaceTextureHelper = texHelper

        val source = factory.createVideoSource(capturer.isScreencast)
        videoSource = source
        capturer.initialize(texHelper, applicationContext, source.capturerObserver)
        capturer.startCapture(width, height, fps)

        localVideoTrack = factory.createVideoTrack("deckcast_screen_video", source).apply {
            setEnabled(true)
        }
    }

    private fun startSignalingAndNsd() {
        val srv = LocalSignalingServer(
            port = LocalSignalingServer.DEFAULT_WS_PORT,
            onClientMessage = { clientId, msg, send ->
                handleClientSignaling(clientId, msg, send)
            },
            onClientDisconnected = { clientId ->
                peersByClient.remove(clientId)?.let { hostPeer ->
                    runCatching { hostPeer.peerConnection.close() }
                    runCatching { hostPeer.peerConnection.dispose() }
                }
            }
        )
        signalingServer = srv
        srv.start()

        val nsd = NsdDiscoveryManager(applicationContext)
        nsdManager = nsd
        nsd.registerHost(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            wsPort = LocalSignalingServer.DEFAULT_WS_PORT
        )
    }

    private suspend fun handleClientSignaling(
        clientId: String,
        message: SignalingMessage,
        send: suspend (SignalingMessage) -> Unit
    ) {
        when (message) {
            is SignalingMessage.Hello -> {
                send(
                    SignalingMessage.Hello(
                        sessionId = message.sessionId,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                        osType = "android"
                    )
                )
            }

            is SignalingMessage.Offer -> {
                val hostPeer = createPeerForClient(clientId, message.sessionId)
                val offerSdp = SessionDescription(SessionDescription.Type.OFFER, message.sdp)
                hostPeer.peerConnection.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        hostPeer.remoteDescriptionSet = true
                        synchronized(hostPeer.pendingCandidates) {
                            hostPeer.pendingCandidates.forEach {
                                hostPeer.peerConnection.addIceCandidate(it)
                            }
                            hostPeer.pendingCandidates.clear()
                        }
                        createAndSendAnswer(hostPeer, send)
                    }

                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Host setRemoteOffer failed: $err")
                    }

                    override fun onCreateSuccess(desc: SessionDescription?) = Unit
                    override fun onCreateFailure(err: String?) = Unit
                }, offerSdp)
            }

            is SignalingMessage.IceCandidateMsg -> {
                val hostPeer = peersByClient[clientId] ?: return
                val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate)
                if (hostPeer.remoteDescriptionSet) {
                    hostPeer.peerConnection.addIceCandidate(candidate)
                } else {
                    synchronized(hostPeer.pendingCandidates) {
                        hostPeer.pendingCandidates.add(candidate)
                    }
                }
            }

            is SignalingMessage.Bye -> {
                peersByClient.remove(clientId)?.let {
                    runCatching { it.peerConnection.close() }
                    runCatching { it.peerConnection.dispose() }
                }
            }

            else -> Unit
        }
    }

    private fun createPeerForClient(clientId: String, sessionId: String): HostPeer {
        val factory = WebRtcFactoryProvider.getFactory(applicationContext)
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        lateinit var hostPeer: HostPeer
        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                scope.launch {
                    signalingServer?.sendToClient(
                        clientId,
                        SignalingMessage.IceCandidateMsg(
                            sessionId = sessionId,
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            candidate = candidate.sdp
                        )
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onTrack(transceiver: RtpTransceiver?) = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit

            override fun onDataChannel(dc: DataChannel?) {
                if (dc == null || dc.label() != "input") return
                dc.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) = Unit
                    override fun onStateChange() = Unit
                    override fun onMessage(buffer: DataChannel.Buffer?) {
                        if (buffer == null) return
                        val bytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(bytes)
                        val json = String(bytes, StandardCharsets.UTF_8)
                        val packet = InputPacket.fromJson(json) ?: return
                        RemoteAccessibilityService.getInstance()?.handleInputPacket(packet)
                    }
                })
            }
        }) ?: error("Failed to create Host PeerConnection")

        localVideoTrack?.let { track ->
            pc.addTrack(track, listOf("deckcast_stream"))
        }

        hostPeer = HostPeer(clientId = clientId, sessionId = sessionId, peerConnection = pc)
        peersByClient[clientId] = hostPeer
        return hostPeer
    }

    private fun createAndSendAnswer(
        hostPeer: HostPeer,
        send: suspend (SignalingMessage) -> Unit
    ) {
        hostPeer.peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                hostPeer.peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        scope.launch {
                            send(SignalingMessage.Answer(sessionId = hostPeer.sessionId, sdp = desc.description))
                        }
                    }

                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Host setLocalAnswer failed: $err")
                    }

                    override fun onCreateSuccess(d: SessionDescription?) = Unit
                    override fun onCreateFailure(err: String?) = Unit
                }, desc)
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Host createAnswer failed: $err")
            }

            override fun onSetFailure(err: String?) = Unit
        }, MediaConstraints())
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeckCast Screen Host Active")
            .setContentText("Streaming screen over local LAN WebRTC")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.host_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
