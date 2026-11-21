"""Native desktop launcher for FloatPaint (AERO-BRUSH)."""

from __future__ import annotations

import asyncio
import base64
import os
import socket
import sys
import threading
import time
import webbrowser
from pathlib import Path

if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")

import webview

import bridge
import tracker

ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))


def _pick_tcp_port(preferred: int) -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        try:
            sock.bind(("127.0.0.1", preferred))
            return preferred
        except OSError:
            sock.bind(("127.0.0.1", 0))
            return int(sock.getsockname()[1])


def _pick_udp_port(preferred: int) -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            sock.bind(("127.0.0.1", preferred))
            return preferred
        except OSError:
            sock.bind(("127.0.0.1", 0))
            return int(sock.getsockname()[1])


def _is_port_in_use(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.3)
        return sock.connect_ex((host, port)) == 0


def _wait_for_port(host: str, port: int, timeout_s: float = 4.0) -> bool:
    deadline = time.perf_counter() + timeout_s
    while time.perf_counter() < deadline:
        if _is_port_in_use(host, port):
            return True
        time.sleep(0.05)
    return False


class StudioApi:
    """JS-callable desktop API for native Save As dialogs."""

    def save_file(self, filename: str, content: str, is_data_url: bool = False) -> bool:
        window = webview.active_window()
        if window is None:
            return False

        ext = Path(filename).suffix.lower()
        file_types = {
            ".png": ("PNG Image (*.png)",),
            ".obj": ("Wavefront OBJ (*.obj)",),
            ".gltf": ("glTF 2.0 JSON (*.gltf)",),
        }.get(ext, ("All files (*.*)",))

        result = window.create_file_dialog(
            webview.FileDialog.SAVE,
            save_filename=filename,
            file_types=file_types,
        )
        if not result:
            return False

        dest = Path(result[0] if isinstance(result, (list, tuple)) else result)
        if is_data_url and "," in content:
            _, b64 = content.split(",", 1)
            dest.write_bytes(base64.b64decode(b64))
        else:
            dest.write_text(content, encoding="utf-8")
        return True


def _start_bridge_thread(udp_port: int = 5055, ws_port: int = 8765, http_port: int = 8080) -> threading.Thread:
    def _runner() -> None:
        try:
            asyncio.run(bridge.run_bridge("127.0.0.1", udp_port, ws_port, http_port))
        except Exception as exc:
            print(f"[AERO-BRUSH] Bridge error: {exc}")

    thread = threading.Thread(target=_runner, name="AeroBrushBridge", daemon=True)
    thread.start()
    return thread


def _start_tracker_thread(
    tracker_args,
    stop_event: threading.Event,
) -> threading.Thread:
    def _runner() -> None:
        try:
            tracker.run_tracker(tracker_args, stop_event=stop_event)
        except RuntimeError as exc:
            # Keep the desktop studio open in Mouse Simulator / Browser Cam mode
            # when no hardware webcam is connected.
            print(f"[AERO-BRUSH] Camera notice: {exc}")
        except Exception as exc:
            print(f"[AERO-BRUSH] Tracker stopped: {exc}")

    thread = threading.Thread(target=_runner, name="AeroBrushTracker", daemon=True)
    thread.start()
    return thread


def main() -> None:
    raw_args = list(sys.argv[1:])
    use_browser = False
    if "--browser" in raw_args:
        use_browser = True
        raw_args.remove("--browser")

    udp_port = _pick_udp_port(5055)
    ws_port = _pick_tcp_port(8765)
    http_port = _pick_tcp_port(8080)

    _start_bridge_thread(udp_port=udp_port, ws_port=ws_port, http_port=http_port)
    _wait_for_port("127.0.0.1", http_port, timeout_s=4.0)

    stop_event = threading.Event()
    tracker_args = tracker.parse_args(raw_args)
    if "--port" not in raw_args:
        tracker_args.port = udp_port

    studio_url = f"http://127.0.0.1:{http_port}/?ws={ws_port}"

    if use_browser:
        webbrowser.open(studio_url)
        try:
            tracker.run_tracker(tracker_args, stop_event=stop_event)
        except KeyboardInterrupt:
            pass
        return

    tracker_thread = _start_tracker_thread(tracker_args, stop_event)

    api = StudioApi()
    window = webview.create_window(
        "FloatPaint — AERO-BRUSH",
        url=studio_url,
        js_api=api,
        width=1360,
        height=840,
        min_size=(960, 620),
        background_color="#0B0E14",
    )

    def _on_closing() -> None:
        stop_event.set()

    window.events.closing += _on_closing

    icon_path = ROOT / "assets" / "icon.ico"
    webview.start(icon=str(icon_path) if icon_path.exists() else None)

    stop_event.set()
    tracker_thread.join(timeout=2.0)


if __name__ == "__main__":
    main()
