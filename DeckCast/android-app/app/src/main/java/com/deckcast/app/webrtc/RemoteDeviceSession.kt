package com.deckcast.app.webrtc

import android.content.Context
import android.os.Build
import android.util.Log
import com.deckcast.app.model.DiscoveredHost
import com.deckcast.app.model.InputPacket
import com.deckcast.app.model.SignalingMessage
import com.deckcast.app.network.SignalingClient
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Encapsulates a single Controller->Host WebRTC PeerConnection, TextureViewRenderer video sink,
 * unordered "input" DataChannel, and local WebSocket signaling client.
 */
class RemoteDeviceSession(
    context: Context,
    val sessionId: String = UUID.randomUUID().toString(),
    val host: DiscoveredHost,
    private val onStateChanged: (sessionId: String, state: SessionState) -> Unit,
    private val onClosed: (sessionId: String) -> Unit
) {

    enum class SessionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    companion object {
        private const val TAG = "DeckCastSession"
        const val INPUT_CHANNEL_NAME = "input"
    }

    private val peerFactory = WebRtcFactoryProvider.getFactory(context)
    private var peerConnection: PeerConnection? = null
    private var inputChannel: DataChannel? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var attachedRenderer: TextureViewRenderer? = null
    private var signalingClient: SignalingClient? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    @Volatile
    private var remoteDescriptionSet = false

    @Volatile
    private var isDisposed = false

    fun attachRenderer(renderer: TextureViewRenderer) {
        attachedRenderer = renderer
        renderer.init(WebRtcFactoryProvider.eglBase.eglBaseContext)
        remoteVideoTrack?.addSink(renderer)
    }

    fun start() {
        onStateChanged(sessionId, SessionState.CONNECTING)
        initPeerConnection()

        val client = SignalingClient(
            host = host.hostAddress,
            port = host.wsPort,
            onConnected = {
                signalingClient?.send(
                    SignalingMessage.Hello(
                        sessionId = sessionId,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                        osType = "android"
                    )
                )
                createAndSendOffer()
            },
            onMessage = { msg -> handleSignalingMessage(msg) },
            onDisconnected = { err ->
                if (!isDisposed) {
                    onStateChanged(
                        sessionId,
                        if (err != null) SessionState.FAILED else SessionState.DISCONNECTED
                    )
                }
            }
        )
        signalingClient = client
        client.connect()
    }

    fun sendInput(packet: InputPacket) {
        val dc = inputChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        val bytes = packet.toJson().toByteArray(StandardCharsets.UTF_8)
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun close() {
        if (isDisposed) return
        isDisposed = true

        signalingClient?.send(SignalingMessage.Bye(sessionId = sessionId, reason = "user_closed"))
        signalingClient?.close()
        signalingClient = null

        attachedRenderer?.let { renderer ->
            runCatching { remoteVideoTrack?.removeSink(renderer) }
            runCatching { renderer.release() }
        }
        attachedRenderer = null
        remoteVideoTrack = null

        runCatching { inputChannel?.close() }
        inputChannel = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        onClosed(sessionId)
    }

    private fun initPeerConnection() {
        // Zero-internet LAN policy: strictly empty ICE server list (Host candidates only)
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = peerFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED ->
                        onStateChanged(sessionId, SessionState.CONNECTED)
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED ->
                        if (!isDisposed) onStateChanged(sessionId, SessionState.DISCONNECTED)
                    PeerConnection.IceConnectionState.FAILED ->
                        if (!isDisposed) onStateChanged(sessionId, SessionState.FAILED)
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                signalingClient?.send(
                    SignalingMessage.IceCandidateMsg(
                        sessionId = sessionId,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        candidate = candidate.sdp
                    )
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dc: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    attachVideoTrack(track)
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    attachVideoTrack(track)
                }
            }
        })

        peerConnection = pc ?: return

        // Receive-only H.264 video transceiver from Host
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        // Low-latency unordered/unreliable DataChannel named "input"
        val dcInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }
        inputChannel = pc.createDataChannel(INPUT_CHANNEL_NAME, dcInit)
    }

    private fun attachVideoTrack(track: VideoTrack) {
        remoteVideoTrack = track
        track.setEnabled(true)
        attachedRenderer?.let { renderer ->
            track.addSink(renderer)
        }
    }

    private fun createAndSendOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(simpleSdpObserver("setLocalOffer"), desc)
                signalingClient?.send(SignalingMessage.Offer(sessionId = sessionId, sdp = desc.description))
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createOffer failed: $err")
            }
            override fun onSetFailure(err: String?) = Unit
        }, constraints)
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        val pc = peerConnection ?: return
        when (message) {
            is SignalingMessage.Answer -> {
                val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, message.sdp)
                pc.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        synchronized(pendingIceCandidates) {
                            pendingIceCandidates.forEach { pc.addIceCandidate(it) }
                            pendingIceCandidates.clear()
                        }
                    }

                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "setRemoteDescription failed: $err")
                    }

                    override fun onCreateSuccess(desc: SessionDescription?) = Unit
                    override fun onCreateFailure(err: String?) = Unit
                }, answerSdp)
            }

            is SignalingMessage.IceCandidateMsg -> {
                val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate)
                if (remoteDescriptionSet) {
                    pc.addIceCandidate(candidate)
                } else {
                    synchronized(pendingIceCandidates) {
                        pendingIceCandidates.add(candidate)
                    }
                }
            }

            is SignalingMessage.Bye -> close()
            is SignalingMessage.Error -> {
                Log.e(TAG, "Remote signaling error ${message.code}: ${message.message}")
                onStateChanged(sessionId, SessionState.FAILED)
            }

            else -> Unit
        }
    }

    private fun simpleSdpObserver(label: String) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(err: String?) {
            Log.e(TAG, "$label create failure: $err")
        }
        override fun onSetFailure(err: String?) {
            Log.e(TAG, "$label set failure: $err")
        }
    }
}
