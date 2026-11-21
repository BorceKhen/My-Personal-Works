package com.deckcast.app.controller

import android.content.Context
import com.deckcast.app.model.DiscoveredHost
import com.deckcast.app.model.InputPacket
import com.deckcast.app.webrtc.RemoteDeviceSession
import com.deckcast.app.webrtc.TextureViewRenderer
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages multiple simultaneous Controller -> Host WebRTC sessions keyed by sessionId.
 */
class SessionManager(private val context: Context) {

    private val sessions = ConcurrentHashMap<String, RemoteDeviceSession>()
    private val sessionStates = ConcurrentHashMap<String, RemoteDeviceSession.SessionState>()
    private var listener: ((Map<String, RemoteDeviceSession.SessionState>) -> Unit)? = null

    fun setSessionListListener(onChanged: (Map<String, RemoteDeviceSession.SessionState>) -> Unit) {
        listener = onChanged
        onChanged(sessionStates.toMap())
    }

    fun openSession(
        host: DiscoveredHost,
        renderer: TextureViewRenderer,
        onStateChanged: (RemoteDeviceSession.SessionState) -> Unit,
        onClosed: () -> Unit
    ): RemoteDeviceSession {
        val session = RemoteDeviceSession(
            context = context.applicationContext,
            host = host,
            onStateChanged = { id, state ->
                sessionStates[id] = state
                onStateChanged(state)
                listener?.invoke(sessionStates.toMap())
            },
            onClosed = { id ->
                sessions.remove(id)
                sessionStates.remove(id)
                onClosed()
                listener?.invoke(sessionStates.toMap())
            }
        )
        sessions[session.sessionId] = session
        sessionStates[session.sessionId] = RemoteDeviceSession.SessionState.CONNECTING
        session.attachRenderer(renderer)
        session.start()
        listener?.invoke(sessionStates.toMap())
        return session
    }

    fun sendInput(sessionId: String, packet: InputPacket) {
        sessions[sessionId]?.sendInput(packet)
    }

    fun closeSession(sessionId: String) {
        sessions[sessionId]?.close()
    }

    fun closeAll() {
        sessions.values.toList().forEach { it.close() }
        sessions.clear()
        sessionStates.clear()
        listener?.invoke(emptyMap())
    }

    fun activeCount(): Int = sessions.size
}
