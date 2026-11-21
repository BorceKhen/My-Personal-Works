package com.deckcast.app.model

import org.json.JSONObject

sealed interface SignalingMessage {
    val type: String
    val sessionId: String

    fun toJson(): String

    data class Hello(
        override val sessionId: String,
        val deviceName: String,
        val osType: String = "android",
        val protocolVersion: Int = 1
    ) : SignalingMessage {
        override val type: String = "hello"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("deviceName", deviceName)
            put("osType", osType)
            put("protocolVersion", protocolVersion)
        }.toString()
    }

    data class Offer(
        override val sessionId: String,
        val sdp: String
    ) : SignalingMessage {
        override val type: String = "offer"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("sdp", sdp)
        }.toString()
    }

    data class Answer(
        override val sessionId: String,
        val sdp: String
    ) : SignalingMessage {
        override val type: String = "answer"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("sdp", sdp)
        }.toString()
    }

    data class IceCandidateMsg(
        override val sessionId: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : SignalingMessage {
        override val type: String = "ice_candidate"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }.toString()
    }

    data class Bye(
        override val sessionId: String,
        val reason: String = "closed"
    ) : SignalingMessage {
        override val type: String = "bye"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("reason", reason)
        }.toString()
    }

    data class Error(
        override val sessionId: String,
        val code: String,
        val message: String
    ) : SignalingMessage {
        override val type: String = "error"
        override fun toJson(): String = JSONObject().apply {
            put("type", type)
            put("sessionId", sessionId)
            put("code", code)
            put("message", message)
        }.toString()
    }

    companion object {
        fun fromJson(raw: String): SignalingMessage? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val sessionId = json.optString("sessionId", "")
            return when (json.optString("type")) {
                "hello" -> Hello(
                    sessionId = sessionId,
                    deviceName = json.optString("deviceName", "Unknown"),
                    osType = json.optString("osType", "android"),
                    protocolVersion = json.optInt("protocolVersion", 1)
                )
                "offer" -> Offer(
                    sessionId = sessionId,
                    sdp = json.optString("sdp", "")
                )
                "answer" -> Answer(
                    sessionId = sessionId,
                    sdp = json.optString("sdp", "")
                )
                "ice_candidate" -> IceCandidateMsg(
                    sessionId = sessionId,
                    sdpMid = json.optString("sdpMid", ""),
                    sdpMLineIndex = json.optInt("sdpMLineIndex", 0),
                    candidate = json.optString("candidate", "")
                )
                "bye" -> Bye(
                    sessionId = sessionId,
                    reason = json.optString("reason", "closed")
                )
                "error" -> Error(
                    sessionId = sessionId,
                    code = json.optString("code", "UNKNOWN"),
                    message = json.optString("message", "")
                )
                else -> null
            }
        }
    }
}

sealed interface InputPacket {
    fun toJson(): String

    data class Pointer(
        val action: String, // "DOWN" | "MOVE" | "UP" | "SCROLL"
        val x: Float,
        val y: Float,
        val scrollY: Float = 0f
    ) : InputPacket {
        override fun toJson(): String = JSONObject().apply {
            put("type", "pointer")
            put("action", action)
            put("x", x.coerceIn(0f, 1f).toDouble())
            put("y", y.coerceIn(0f, 1f).toDouble())
            put("scrollY", scrollY.toDouble())
        }.toString()
    }

    data class Key(
        val action: String, // "DOWN" | "UP"
        val keyCode: Int,
        val text: String = ""
    ) : InputPacket {
        override fun toJson(): String = JSONObject().apply {
            put("type", "key")
            put("action", action)
            put("keyCode", keyCode)
            put("text", text)
        }.toString()
    }

    companion object {
        fun fromJson(raw: String): InputPacket? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            return when (json.optString("type")) {
                "pointer" -> Pointer(
                    action = json.optString("action", "MOVE"),
                    x = json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                    y = json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                    scrollY = json.optDouble("scrollY", 0.0).toFloat()
                )
                "key" -> Key(
                    action = json.optString("action", "DOWN"),
                    keyCode = json.optInt("keyCode", 0),
                    text = json.optString("text", "")
                )
                else -> null
            }
        }
    }
}
