"""Windows Host service: local WebSocket signaling + aiortc WebRTC + SendInput."""

from __future__ import annotations

import asyncio
import json
import socket
from typing import Callable, Dict, Optional

import websockets
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription
from aiortc.contrib.media import MediaRelay

from .capture import ScreenVideoTrack
from .discovery import MdnsAdvertiser
from .input_inject import WindowsInputInjector
from .protocol import (
    DEFAULT_WS_PORT,
    INPUT_CHANNEL_NAME,
    make_answer,
    make_hello,
    parse_ice_candidate,
    parse_json_message,
)


class WindowsHostServer:
    """Streams the Windows desktop over LAN WebRTC (`iceServers=[]`) and injects input."""

    def __init__(
        self,
        port: int = DEFAULT_WS_PORT,
        max_width: int = 1280,
        max_height: int = 720,
        fps: int = 60,
        advertise_mdns: bool = True,
        injector: Optional[WindowsInputInjector] = None,
        track_factory: Optional[Callable[[], ScreenVideoTrack]] = None,
        on_clients_changed: Optional[Callable[[int], None]] = None,
    ) -> None:
        self.port = port
        self.max_width = max_width
        self.max_height = max_height
        self.fps = fps
        self.advertise_mdns = advertise_mdns
        self.injector = injector or WindowsInputInjector()
        self._track_factory = track_factory or (
            lambda: ScreenVideoTrack(max_width=self.max_width, max_height=self.max_height, fps=self.fps)
        )
        self._on_clients_changed = on_clients_changed

        self._ws_server = None
        self._advertiser: Optional[MdnsAdvertiser] = None
        self._relay: Optional[MediaRelay] = None
        self._base_track: Optional[ScreenVideoTrack] = None
        self._peers: Dict[str, RTCPeerConnection] = {}

    async def start(self) -> int:
        if self._ws_server is not None:
            return self.port

        self._relay = MediaRelay()
        self._base_track = self._track_factory()
        self._ws_server = await websockets.serve(self._handle_ws, "0.0.0.0", self.port)

        # Resolve actual bound port if port == 0 (used in tests)
        if self._ws_server.sockets:
            self.port = int(self._ws_server.sockets[0].getsockname()[1])

        if self.advertise_mdns:
            self._advertiser = MdnsAdvertiser(device_name=socket.gethostname(), ws_port=self.port)
            await asyncio.to_thread(self._advertiser.start)

        return self.port

    async def stop(self) -> None:
        if self._advertiser is not None:
            await asyncio.to_thread(self._advertiser.stop)
            self._advertiser = None

        for pc in list(self._peers.values()):
            await pc.close()
        self._peers.clear()
        self._emit_client_count()

        if self._base_track is not None:
            self._base_track.stop()
            self._base_track = None
        self._relay = None

        if self._ws_server is not None:
            self._ws_server.close()
            await self._ws_server.wait_closed()
            self._ws_server = None

    async def _handle_ws(self, websocket) -> None:
        req_path = getattr(getattr(websocket, "request", None), "path", "/signal")
        if req_path not in ("/signal", "/"):
            await websocket.close(code=1008, reason="Invalid path")
            return

        peer_key: Optional[str] = None
        try:
            async for raw in websocket:
                if not isinstance(raw, str):
                    continue
                msg = parse_json_message(raw)
                if not msg:
                    continue

                mtype = msg.get("type")
                session_id = str(msg.get("sessionId", ""))

                if mtype == "hello":
                    await websocket.send(make_hello(session_id, socket.gethostname(), "windows"))

                elif mtype == "offer":
                    peer_key = session_id or str(id(websocket))
                    pc = await self._create_peer(peer_key)
                    offer = RTCSessionDescription(sdp=str(msg.get("sdp", "")), type="offer")
                    await pc.setRemoteDescription(offer)
                    answer = await pc.createAnswer()
                    await pc.setLocalDescription(answer)
                    await self._wait_ice_gathering(pc)
                    await websocket.send(make_answer(session_id, pc.localDescription.sdp))

                elif mtype == "ice_candidate" and peer_key and peer_key in self._peers:
                    cand = parse_ice_candidate(msg)
                    if cand is not None:
                        await self._peers[peer_key].addIceCandidate(cand)

                elif mtype == "bye" and peer_key:
                    break
        except websockets.ConnectionClosed:
            pass
        finally:
            if peer_key and peer_key in self._peers:
                pc = self._peers.pop(peer_key)
                await pc.close()
                self._emit_client_count()

    async def _create_peer(self, peer_key: str) -> RTCPeerConnection:
        if peer_key in self._peers:
            await self._peers.pop(peer_key).close()

        # Zero-internet LAN policy: strictly empty iceServers
        pc = RTCPeerConnection(configuration=RTCConfiguration(iceServers=[]))
        self._peers[peer_key] = pc
        self._emit_client_count()

        if self._relay is not None and self._base_track is not None:
            pc.addTrack(self._relay.subscribe(self._base_track))

        @pc.on("datachannel")
        def on_datachannel(channel) -> None:
            if channel.label != INPUT_CHANNEL_NAME:
                return

            @channel.on("message")
            def on_message(message) -> None:
                if isinstance(message, bytes):
                    message = message.decode("utf-8", errors="ignore")
                if not isinstance(message, str):
                    return
                pkt = parse_json_message(message)
                if pkt is not None:
                    self.injector.handle_packet(pkt)

        @pc.on("connectionstatechange")
        async def on_connectionstatechange() -> None:
            if pc.connectionState in ("failed", "closed"):
                if peer_key in self._peers and self._peers[peer_key] is pc:
                    self._peers.pop(peer_key, None)
                    self._emit_client_count()
                    await pc.close()

        return pc

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

    def _emit_client_count(self) -> None:
        if self._on_clients_changed is not None:
            self._on_clients_changed(len(self._peers))
