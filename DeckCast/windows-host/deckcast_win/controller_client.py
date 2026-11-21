"""Windows Controller WebRTC client for viewing & controlling Android/Windows Hosts."""

from __future__ import annotations

import asyncio
import socket
import uuid
from typing import Callable, Optional

import numpy as np
import websockets
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription

from .protocol import (
    INPUT_CHANNEL_NAME,
    make_bye,
    make_hello,
    make_key_packet,
    make_offer,
    make_pointer_packet,
    parse_ice_candidate,
    parse_json_message,
)


class RemoteControllerClient:
    """Connects from Windows to an Android (or Windows) DeckCast Host over LAN WebRTC."""

    def __init__(
        self,
        host_address: str,
        ws_port: int,
        on_frame: Callable[[np.ndarray], None],
        on_state_changed: Optional[Callable[[str], None]] = None,
    ) -> None:
        self.host_address = host_address
        self.ws_port = ws_port
        self.session_id = str(uuid.uuid4())
        self._on_frame = on_frame
        self._on_state_changed = on_state_changed

        self._pc: Optional[RTCPeerConnection] = None
        self._dc = None
        self._ws = None
        self._video_task: Optional[asyncio.Task] = None
        self._run_task: Optional[asyncio.Task] = None
        self._closed = False

    def start(self, loop: asyncio.AbstractEventLoop) -> None:
        self._run_task = loop.create_task(self._run())

    async def close(self) -> None:
        self._closed = True
        if self._video_task is not None:
            self._video_task.cancel()
            self._video_task = None
        if self._ws is not None:
            try:
                await self._ws.send(make_bye(self.session_id))
                await self._ws.close()
            except Exception:
                pass
            self._ws = None
        if self._pc is not None:
            await self._pc.close()
            self._pc = None
        self._emit_state("disconnected")

    def send_pointer(self, action: str, x: float, y: float, scroll_y: float = 0.0) -> bool:
        if self._dc is None or self._dc.readyState != "open":
            return False
        self._dc.send(make_pointer_packet(action, x, y, scroll_y))
        return True

    def send_key(self, action: str, key_code: int, text: str = "") -> bool:
        if self._dc is None or self._dc.readyState != "open":
            return False
        self._dc.send(make_key_packet(action, key_code, text))
        return True

    async def _run(self) -> None:
        self._emit_state("connecting")
        pc = RTCPeerConnection(configuration=RTCConfiguration(iceServers=[]))
        self._pc = pc

        pc.addTransceiver("video", direction="recvonly")
        self._dc = pc.createDataChannel(INPUT_CHANNEL_NAME, ordered=False, maxRetransmits=0)

        @pc.on("track")
        def on_track(track) -> None:
            if track.kind == "video":
                self._video_task = asyncio.create_task(self._consume_video(track))

        @pc.on("connectionstatechange")
        def on_conn_state() -> None:
            self._emit_state(pc.connectionState)

        ws_url = f"ws://{self.host_address}:{self.ws_port}/signal"
        try:
            async with websockets.connect(ws_url) as ws:
                self._ws = ws
                await ws.send(make_hello(self.session_id, socket.gethostname(), "windows"))

                offer = await pc.createOffer()
                await pc.setLocalDescription(offer)
                await self._wait_ice_gathering(pc)
                await ws.send(make_offer(self.session_id, pc.localDescription.sdp))

                async for raw in ws:
                    if self._closed:
                        break
                    if not isinstance(raw, str):
                        continue
                    msg = parse_json_message(raw)
                    if not msg:
                        continue
                    mtype = msg.get("type")
                    if mtype == "answer":
                        ans = RTCSessionDescription(sdp=str(msg.get("sdp", "")), type="answer")
                        await pc.setRemoteDescription(ans)
                    elif mtype == "ice_candidate":
                        cand = parse_ice_candidate(msg)
                        if cand is not None:
                            await pc.addIceCandidate(cand)
                    elif mtype == "bye":
                        break
        except Exception:
            if not self._closed:
                self._emit_state("failed")
        finally:
            await self.close()

    async def _consume_video(self, track) -> None:
        try:
            while not self._closed:
                frame = await track.recv()
                rgb = frame.to_ndarray(format="rgb24")
                self._on_frame(rgb)
        except asyncio.CancelledError:
            pass
        except Exception:
            pass

    async def _wait_ice_gathering(self, pc: RTCPeerConnection, timeout: float = 2.0) -> None:
        if pc.iceGatheringState == "complete":
            return
        done = asyncio.Event()

        @pc.on("icegatheringstatechange")
        def _on_state() -> None:
            if pc.iceGatheringState == "complete":
                done.set()

        try:
            await asyncio.wait_for(done.wait(), timeout=timeout)
        except asyncio.TimeoutError:
            pass

    def _emit_state(self, state: str) -> None:
        if self._on_state_changed is not None:
            self._on_state_changed(state)
