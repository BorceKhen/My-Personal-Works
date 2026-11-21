package com.deckcast.app.model

data class DiscoveredHost(
    val serviceName: String,
    val deviceName: String,
    val osType: String,
    val hostAddress: String,
    val wsPort: Int
) {
    val sessionKey: String
        get() = "$hostAddress:$wsPort"
}
