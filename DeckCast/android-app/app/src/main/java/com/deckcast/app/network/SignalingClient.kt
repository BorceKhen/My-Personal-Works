package com.deckcast.app.network

import android.util.Log
import com.deckcast.app.model.SignalingMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * WebSocket signaling client connecting a Controller session directly to a Host's LAN IP:Port.
 */
class SignalingClient(
    private val host: String,
    private val port: Int,
    private val onConnected: suspend () -> Unit,
    private val onMessage: (SignalingMessage) -> Unit,
    private val onDisconnected: (Throwable?) -> Unit
) {

    companion object {
        private const val TAG = "DeckCastSignalCli"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private var socketSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob != null) return
        connectionJob = scope.launch {
            var disconnectError: Throwable? = null
            try {
                client.webSocket(host = host, port = port, path = "/signal") {
                    socketSession = this
                    onConnected()
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val parsed = SignalingMessage.fromJson(frame.readText())
                            if (parsed != null) {
                                onMessage(parsed)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                disconnectError = t
                Log.w(TAG, "Signaling WebSocket closed ($host:$port): ${t.message}")
            } finally {
                socketSession = null
                onDisconnected(disconnectError)
            }
        }
    }

    fun send(message: SignalingMessage) {
        val ws = socketSession ?: return
        scope.launch {
            runCatching { ws.send(Frame.Text(message.toJson())) }
        }
    }

    fun close() {
        scope.launch {
            runCatching { socketSession?.close() }
            runCatching { client.close() }
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
}
