package com.deckcast.app.network

import android.util.Log
import com.deckcast.app.model.SignalingMessage
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded Ktor CIO WebSocket signaling server running on the Android Host (`ws://<ip>:<port>/signal`).
 */
class LocalSignalingServer(
    private val port: Int = DEFAULT_WS_PORT,
    private val onClientMessage: suspend (clientId: String, message: SignalingMessage, send: suspend (SignalingMessage) -> Unit) -> Unit,
    private val onClientDisconnected: (clientId: String) -> Unit
) {

    companion object {
        const val DEFAULT_WS_PORT = 9443
        private const val TAG = "DeckCastSignalSrv"
    }

    private var server: ApplicationEngine? = null
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(WebSockets)
            routing {
                webSocket("/signal") {
                    val clientId = java.util.UUID.randomUUID().toString()
                    sessions[clientId] = this
                    Log.i(TAG, "Controller signaling connected: $clientId")

                    val sender: suspend (SignalingMessage) -> Unit = { msg ->
                        runCatching { send(Frame.Text(msg.toJson())) }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val parsed = SignalingMessage.fromJson(frame.readText())
                                if (parsed != null) {
                                    onClientMessage(clientId, parsed, sender)
                                }
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Clean close
                    } catch (e: Exception) {
                        Log.w(TAG, "Signaling client error ($clientId): ${e.message}")
                    } finally {
                        sessions.remove(clientId)
                        onClientDisconnected(clientId)
                    }
                }
            }
        }.start(wait = false)
        Log.i(TAG, "Ktor WebSocket signaling server listening on :$port/signal")
    }

    suspend fun sendToClient(clientId: String, message: SignalingMessage) {
        val ws = sessions[clientId] ?: return
        runCatching { ws.send(Frame.Text(message.toJson())) }
    }

    fun stop() {
        sessions.clear()
        (server as? CIOApplicationEngine)?.stop(200, 500) ?: server?.stop(200, 500)
        server = null
    }
}
