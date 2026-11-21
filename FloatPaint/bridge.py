"""Zero-dependency asyncio UDP (:5055) -> WebSocket (:8765) + HTTP (:8080) bridge.

Forwards JSON telemetry datagrams from tracker.py to any connected browser
studio clients via RFC 6455 WebSockets, and serves the frontend static app.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import json
import mimetypes
import struct
import sys
from pathlib import Path
from typing import Any, Dict, Optional, Set, Tuple
from urllib.parse import parse_qs, urlsplit

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
PROJECT_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
FRONTEND_DIR = PROJECT_ROOT / "frontend"
THREE_LOCAL_DIR = PROJECT_ROOT / "node_modules" / "three"
THREE_CDN_BASE = "https://cdn.jsdelivr.net/npm/three@0.170.0"

EXTRA_MIME_TYPES: Dict[str, str] = {
    ".js": "application/javascript; charset=utf-8",
    ".mjs": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".html": "text/html; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
}


def encode_ws_text_frame(message: str) -> bytes:
    """Encode a UTF-8 string into an unmasked server-to-client RFC 6455 frame."""
    payload = message.encode("utf-8")
    length = len(payload)
    if length < 126:
        header = struct.pack("!BB", 0x81, length)
    elif length < 65536:
        header = struct.pack("!BBH", 0x81, 126, length)
    else:
        header = struct.pack("!BBQ", 0x81, 127, length)
    return header + payload


def encode_ws_pong_frame(payload: bytes) -> bytes:
    """Encode an unmasked RFC 6455 PONG control frame."""
    length = min(len(payload), 125)
    return struct.pack("!BB", 0x8A, length) + payload[:length]


def compute_ws_accept(sec_key: str) -> str:
    """Compute the Sec-WebSocket-Accept header value from Sec-WebSocket-Key."""
    digest = hashlib.sha1((sec_key.strip() + WS_GUID).encode("ascii")).digest()
    return base64.b64encode(digest).decode("ascii")


class TelemetryHub:
    """Thread/async-safe registry of connected WebSocket clients."""

    def __init__(self) -> None:
        self.clients: Set[asyncio.StreamWriter] = set()
        self.latest_payload: Optional[str] = None
        self.packets_received: int = 0

    def register(self, writer: asyncio.StreamWriter) -> None:
        self.clients.add(writer)

    def unregister(self, writer: asyncio.StreamWriter) -> None:
        self.clients.discard(writer)

    async def broadcast(self, message: str) -> None:
        self.latest_payload = message
        self.packets_received += 1
        if not self.clients:
            return
        frame = encode_ws_text_frame(message)
        stale: list[asyncio.StreamWriter] = []
        for writer in list(self.clients):
            try:
                writer.write(frame)
                await writer.drain()
            except Exception:
                stale.append(writer)
        for writer in stale:
            self.unregister(writer)
            try:
                writer.close()
            except Exception:
                pass


class UdpTelemetryProtocol(asyncio.DatagramProtocol):
    """Asyncio UDP datagram handler forwarding packets into TelemetryHub."""

    def __init__(self, hub: TelemetryHub, loop: asyncio.AbstractEventLoop) -> None:
        self.hub = hub
        self.loop = loop

    def datagram_received(self, data: bytes, addr: Tuple[str, int]) -> None:
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            return
        self.loop.create_task(self.hub.broadcast(text))


async def handle_ws_client(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
    hub: TelemetryHub,
) -> None:
    """Perform RFC 6455 upgrade handshake and keep connection alive until closed."""
    try:
        raw_headers = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=5.0)
    except Exception:
        writer.close()
        return

    header_lines = raw_headers.decode("latin1").split("\r\n")
    headers: Dict[str, str] = {}
    for line in header_lines[1:]:
        if ":" in line:
            k, v = line.split(":", 1)
            headers[k.strip().lower()] = v.strip()

    sec_key = headers.get("sec-websocket-key")
    if not sec_key:
        writer.write(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
        await writer.drain()
        writer.close()
        return

    accept_key = compute_ws_accept(sec_key)
    response = (
        "HTTP/1.1 101 Switching Protocols\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Accept: {accept_key}\r\n\r\n"
    )
    writer.write(response.encode("ascii"))
    await writer.drain()

    hub.register(writer)
    try:
        if hub.latest_payload:
            writer.write(encode_ws_text_frame(hub.latest_payload))
            await writer.drain()

        # Drain incoming client frames (e.g., pings or close frames).
        while True:
            header = await reader.readexactly(2)
            opcode = header[0] & 0x0F
            masked = bool(header[1] & 0x80)
            length = header[1] & 0x7F
            if length == 126:
                (length,) = struct.unpack("!H", await reader.readexactly(2))
            elif length == 127:
                (length,) = struct.unpack("!Q", await reader.readexactly(8))

            mask_key = await reader.readexactly(4) if masked else b""
            payload = bytearray(await reader.readexactly(length))
            if masked:
                for i in range(length):
                    payload[i] ^= mask_key[i % 4]

            if opcode == 0x8:  # CLOSE
                break
            elif opcode == 0x9:  # PING
                writer.write(encode_ws_pong_frame(bytes(payload)))
                await writer.drain()
    except (asyncio.IncompleteReadError, ConnectionError, OSError):
        pass
    finally:
        hub.unregister(writer)
        try:
            writer.close()
        except Exception:
            pass


def _resolve_static_path(req_path: str) -> Tuple[Optional[Path], Optional[str]]:
    """Map an HTTP request path to a local file or optional Three.js CDN redirect."""
    clean = req_path.split("?", 1)[0].split("#", 1)[0]
    if clean in ("", "/"):
        clean = "/index.html"

    if clean.startswith("/vendor/three/"):
        rel = clean[len("/vendor/three/"):]
        candidate = (THREE_LOCAL_DIR / rel).resolve()
        if str(candidate).startswith(str(THREE_LOCAL_DIR.resolve())) and candidate.is_file():
            return candidate, None
        return None, f"{THREE_CDN_BASE}/{rel}"

    candidate = (FRONTEND_DIR / clean.lstrip("/")).resolve()
    if str(candidate).startswith(str(FRONTEND_DIR.resolve())) and candidate.is_file():
        return candidate, None
    return None, None


async def _send_json(writer: asyncio.StreamWriter, status: str, payload: Dict[str, Any]) -> None:
    body = json.dumps(payload).encode("utf-8")
    resp = (
        f"HTTP/1.1 {status}\r\n"
        "Content-Type: application/json; charset=utf-8\r\n"
        f"Content-Length: {len(body)}\r\n"
        "Cache-Control: no-cache\r\n"
        "Connection: close\r\n\r\n"
    ).encode("ascii") + body
    writer.write(resp)
    await writer.drain()


async def handle_http_client(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
    camera_api: Optional[Any] = None,
) -> None:
    """Serve the AERO-BRUSH static frontend, local Three.js assets, and camera API."""
    try:
        raw = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=5.0)
        request_line = raw.decode("latin1").split("\r\n", 1)[0]
        parts = request_line.split(" ")
        method = parts[0].upper() if len(parts) >= 1 else "GET"
        req_path = parts[1] if len(parts) >= 2 else "/"
        parsed = urlsplit(req_path)

        if parsed.path == "/api/cameras" and method == "GET":
            if camera_api is None:
                await _send_json(writer, "200 OK", {"cameras": None, "activeIndex": None})
            else:
                cams = await asyncio.to_thread(camera_api.list_cameras)
                await _send_json(writer, "200 OK", {"cameras": cams, "activeIndex": camera_api.active_index})
            return

        if parsed.path == "/api/camera" and method in ("POST", "GET"):
            if camera_api is None:
                await _send_json(writer, "404 Not Found", {"ok": False, "error": "Camera controller unavailable"})
                return
            qs = parse_qs(parsed.query)
            raw_idx = (qs.get("index") or [None])[0]
            try:
                idx = int(raw_idx)
            except (TypeError, ValueError):
                await _send_json(writer, "400 Bad Request", {"ok": False, "error": "Invalid camera index"})
                return
            ok = await asyncio.to_thread(camera_api.select_camera, idx)
            await _send_json(writer, "200 OK", {"ok": ok, "activeIndex": camera_api.active_index})
            return

        if method not in ("GET", "HEAD"):
            writer.write(b"HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n")
            await writer.drain()
            return

        file_path, redirect_url = _resolve_static_path(req_path)
        if redirect_url:
            resp = (
                "HTTP/1.1 302 Found\r\n"
                f"Location: {redirect_url}\r\n"
                "Connection: close\r\n\r\n"
            )
            writer.write(resp.encode("ascii"))
            await writer.drain()
            return

        if file_path is None:
            body = b"404 Not Found"
            resp = (
                "HTTP/1.1 404 Not Found\r\n"
                "Content-Type: text/plain; charset=utf-8\r\n"
                f"Content-Length: {len(body)}\r\n"
                "Connection: close\r\n\r\n"
            ).encode("ascii") + body
            writer.write(resp)
            await writer.drain()
            return

        content = file_path.read_bytes()
        mime = EXTRA_MIME_TYPES.get(file_path.suffix.lower())
        if not mime:
            mime, _ = mimetypes.guess_type(str(file_path))
            mime = mime or "application/octet-stream"

        headers = (
            "HTTP/1.1 200 OK\r\n"
            f"Content-Type: {mime}\r\n"
            f"Content-Length: {len(content)}\r\n"
            "Cache-Control: no-cache\r\n"
            "Connection: close\r\n\r\n"
        ).encode("ascii")
        writer.write(headers if method == "HEAD" else headers + content)
        await writer.drain()
    except Exception:
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def run_bridge(
    host: str,
    udp_port: int,
    ws_port: int,
    http_port: int,
    camera_api: Optional[Any] = None,
) -> None:
    """Start UDP listener, WebSocket telemetry server, and static HTTP server."""
    loop = asyncio.get_running_loop()
    hub = TelemetryHub()

    udp_transport, _ = await loop.create_datagram_endpoint(
        lambda: UdpTelemetryProtocol(hub, loop),
        local_addr=(host, udp_port),
    )
    ws_server = await asyncio.start_server(
        lambda r, w: handle_ws_client(r, w, hub),
        host,
        ws_port,
    )
    http_server = await asyncio.start_server(
        lambda r, w: handle_http_client(r, w, camera_api=camera_api),
        host,
        http_port,
    )

    print(f"[AERO-BRUSH] UDP telemetry listener : udp://{host}:{udp_port}")
    print(f"[AERO-BRUSH] WebSocket bridge       : ws://{host}:{ws_port}")
    print(f"[AERO-BRUSH] Studio UI server       : http://{host}:{http_port}")

    try:
        await asyncio.gather(ws_server.serve_forever(), http_server.serve_forever())
    finally:
        udp_transport.close()
        ws_server.close()
        http_server.close()


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AERO-BRUSH UDP-to-WebSocket & HTTP Bridge")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address (default: 127.0.0.1)")
    parser.add_argument("--udp-port", type=int, default=5055, help="Incoming UDP telemetry port (default: 5055)")
    parser.add_argument("--ws-port", type=int, default=8765, help="Outgoing WebSocket port (default: 8765)")
    parser.add_argument("--http-port", type=int, default=8080, help="Frontend HTTP port (default: 8080)")
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()
    try:
        asyncio.run(run_bridge(args.host, args.udp_port, args.ws_port, args.http_port))
    except KeyboardInterrupt:
        print("\n[AERO-BRUSH] Bridge stopped.")
