package com.deckcast.app

import com.deckcast.app.model.InputPacket
import com.deckcast.app.model.SignalingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolModelsTest {
    @Test
    fun pointerPacketRoundTripsWithClamping() {
        val raw = InputPacket.Pointer(action = "MOVE", x = 1.5f, y = -0.2f, scrollY = 0f).toJson()
        val parsed = InputPacket.fromJson(raw)
        assertTrue(parsed is InputPacket.Pointer)
        val p = parsed as InputPacket.Pointer
        assertEquals("MOVE", p.action)
        assertEquals(1.0f, p.x, 0.0001f)
        assertEquals(0.0f, p.y, 0.0001f)
    }

    @Test
    fun signalingOfferRoundTrips() {
        val raw = SignalingMessage.Offer(sessionId = "s-1", sdp = "v=0\r\n").toJson()
        val parsed = SignalingMessage.fromJson(raw)
        assertTrue(parsed is SignalingMessage.Offer)
        assertEquals("s-1", parsed?.sessionId)
        assertEquals("v=0\r\n", (parsed as SignalingMessage.Offer).sdp)
    }
}
