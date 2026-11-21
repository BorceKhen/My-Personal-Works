"""Integration tests for tracker telemetry pipeline and UDP->WebSocket bridge."""

import asyncio
import json
import socket
import struct
import unittest

import bridge
from tracker import HandTrackingPipeline, _generate_simulated_hand


class TestBridgeAndTelemetry(unittest.TestCase):
    def test_pipeline_emits_required_json_schema(self) -> None:
        pipeline = HandTrackingPipeline(debounce_frames=5)
        lm = _generate_simulated_hand(1.0)
        packet = None
        for i in range(6):
            packet = pipeline.process_landmarks(lm, timestamp_s=i / 60.0, fps=60)

        payload = json.loads(packet.to_json())
        self.assertEqual(payload["state"], "DRAW")
        self.assertEqual(len(payload["cursor"]), 3)
        self.assertGreaterEqual(payload["cursor"][2], -1.0)
        self.assertLessEqual(payload["cursor"][2], 1.0)
        self.assertIsInstance(payload["pinch_dist"], float)
        self.assertEqual(len(payload["landmarks"]), 21)
        self.assertEqual(payload["fps"], 60)

    def test_bridge_ws_accept_and_static_resolution(self) -> None:
        # RFC 6455 vector from standard spec
        accept = bridge.compute_ws_accept("dGhlIHNhbXBsZSBub25jZQ==")
        self.assertEqual(accept, "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")

        index_path, redirect = bridge._resolve_static_path("/")
        self.assertIsNone(redirect)
        self.assertIsNotNone(index_path)
        self.assertTrue(index_path.name == "index.html")

    def test_udp_to_websocket_live_forwarding(self) -> None:
        async def run_flow():
            hub = bridge.TelemetryHub()
            loop = asyncio.get_running_loop()

            udp_transport, _ = await loop.create_datagram_endpoint(
                lambda: bridge.UdpTelemetryProtocol(hub, loop),
                local_addr=("127.0.0.1", 0),
            )
            udp_port = udp_transport.get_extra_info("sockname")[1]

            ws_server = await asyncio.start_server(
                lambda r, w: bridge.handle_ws_client(r, w, hub),
                "127.0.0.1",
                0,
            )
            ws_port = ws_server.sockets[0].getsockname()[1]

            reader, writer = await asyncio.open_connection("127.0.0.1", ws_port)
            handshake = (
                f"GET / HTTP/1.1\r\n"
                f"Host: 127.0.0.1:{ws_port}\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                "Sec-WebSocket-Version: 13\r\n\r\n"
            ).encode("ascii")
            writer.write(handshake)
            await writer.drain()
            resp = await reader.readuntil(b"\r\n\r\n")
            self.assertIn(b"101 Switching Protocols", resp)

            # Send a UDP datagram and verify WebSocket client receives the JSON frame
            test_msg = json.dumps({"state": "DRAW", "cursor": [0.5, 0.5, 0.1], "pinch_dist": 0.12, "landmarks": [], "fps": 60})
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.sendto(test_msg.encode("utf-8"), ("127.0.0.1", udp_port))
            sock.close()

            hdr = await asyncio.wait_for(reader.readexactly(2), timeout=2.0)
            self.assertEqual(hdr[0], 0x81)
            length = hdr[1] & 0x7F
            if length == 126:
                (length,) = struct.unpack("!H", await reader.readexactly(2))
            body = (await reader.readexactly(length)).decode("utf-8")
            self.assertEqual(json.loads(body)["state"], "DRAW")

            writer.close()
            ws_server.close()
            udp_transport.close()

        asyncio.run(run_flow())


if __name__ == "__main__":
    unittest.main()
