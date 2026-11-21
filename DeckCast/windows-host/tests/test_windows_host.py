"""Unit and local-loopback integration tests for DeckCast Windows Host & Controller."""

from __future__ import annotations

import asyncio
import fractions
import json
import unittest

import av
import numpy as np
from aiortc import VideoStreamTrack

from deckcast_win.controller_client import RemoteControllerClient
from deckcast_win.discovery import build_service_info
from deckcast_win.host_server import WindowsHostServer
from deckcast_win.input_inject import WindowsInputInjector, scale_normalized_to_absolute
from deckcast_win.protocol import make_key_packet, make_pointer_packet, parse_ice_candidate, parse_json_message


class _SyntheticVideoTrack(VideoStreamTrack):
    kind = "video"

    def __init__(self) -> None:
        super().__init__()
        self._ts = 0

    async def recv(self) -> av.VideoFrame:
        await asyncio.sleep(0.02)
        self._ts += 3000
        arr = np.zeros((180, 320, 3), dtype=np.uint8)
        arr[:, :, 1] = 180
        frame = av.VideoFrame.from_ndarray(arr, format="rgb24").reformat(format="yuv420p")
        frame.pts = self._ts
        frame.time_base = fractions.Fraction(1, 90000)
        return frame


class TestDeckCastWindowsHost(unittest.TestCase):
    def test_sendinput_coordinate_scaling(self) -> None:
        self.assertEqual(scale_normalized_to_absolute(0.0, 0.0), (0, 0))
        self.assertEqual(scale_normalized_to_absolute(1.0, 1.0), (65535, 65535))
        self.assertEqual(scale_normalized_to_absolute(-0.5, 2.0), (0, 65535))
        ax, ay = scale_normalized_to_absolute(0.5, 0.5)
        self.assertIn(ax, (32767, 32768))
        self.assertIn(ay, (32767, 32768))

    def test_dry_run_injector_handles_pointer_and_key(self) -> None:
        injector = WindowsInputInjector(dry_run=True)
        injector.handle_packet(json.loads(make_pointer_packet("DOWN", 0.25, 0.75)))
        injector.handle_packet(json.loads(make_key_packet("DOWN", 66, "")))
        self.assertEqual(len(injector.recorded_events), 2)
        self.assertEqual(injector.recorded_events[0]["action"], "DOWN")
        self.assertEqual(injector.recorded_events[1]["keyCode"], 66)

    def test_mdns_service_info_metadata(self) -> None:
        info = build_service_info("TEST-PC", ws_port=9443, ip_address="192.168.1.50", os_type="windows")
        self.assertEqual(info.port, 9443)
        self.assertEqual(info.properties[b"deviceName"], b"TEST-PC")
        self.assertEqual(info.properties[b"osType"], b"windows")
        self.assertEqual(info.properties[b"wsPort"], b"9443")

    def test_ice_candidate_parsing(self) -> None:
        msg = parse_json_message(json.dumps({
            "type": "ice_candidate",
            "sessionId": "s1",
            "sdpMid": "0",
            "sdpMLineIndex": 0,
            "candidate": "candidate:1 1 udp 2130706431 192.168.1.20 50000 typ host",
        }))
        cand = parse_ice_candidate(msg)
        self.assertIsNotNone(cand)
        self.assertEqual(cand.ip, "192.168.1.20")
        self.assertEqual(cand.port, 50000)

    def test_local_webrtc_loopback_video_and_datachannel_input(self) -> None:
        async def run_loopback() -> None:
            injector = WindowsInputInjector(dry_run=True)
            host = WindowsHostServer(
                port=0,
                advertise_mdns=False,
                injector=injector,
                track_factory=_SyntheticVideoTrack,
            )
            port = await host.start()
            frames = []
            states = []

            client = RemoteControllerClient(
                host_address="127.0.0.1",
                ws_port=port,
                on_frame=lambda rgb: frames.append(rgb.shape),
                on_state_changed=lambda st: states.append(st),
            )
            loop = asyncio.get_running_loop()
            client.start(loop)

            try:
                for _ in range(80):
                    if frames and client.send_pointer("DOWN", 0.5, 0.5):
                        break
                    await asyncio.sleep(0.05)

                for _ in range(30):
                    if injector.recorded_events:
                        break
                    await asyncio.sleep(0.05)

                self.assertTrue(len(frames) >= 1, f"Expected video frames, states={states}")
                self.assertTrue(len(injector.recorded_events) >= 1, "Expected DataChannel input on host")
                self.assertEqual(injector.recorded_events[0]["action"], "DOWN")
            finally:
                await client.close()
                await host.stop()

        asyncio.run(run_loopback())


if __name__ == "__main__":
    unittest.main()
