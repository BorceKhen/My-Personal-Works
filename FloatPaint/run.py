"""Native desktop launcher for FloatPaint (AERO-BRUSH)."""

from __future__ import annotations

import asyncio
import base64
import copy
import os
import socket
import sys
import threading
import time
import webbrowser
from pathlib import Path
from typing import Dict, List, Optional

if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")

import webview

import bridge
import tracker
from backend.cameras import list_available_cameras

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


class CameraController:
    """Manages available camera enumeration and switching the tracker thread."""

    def __init__(self, base_args) -> None:
        self._base_args = copy.copy(base_args)
        self._lock = threading.Lock()
        self._cameras: List[Dict[str, object]] = list_available_cameras()
        self._stop_event: Optional[threading.Event] = None
        self._thread: Optional[threading.Thread] = None
        self.active_index: Optional[int] = None

        if self._cameras:
            available_ids = [int(cam["index"]) for cam in self._cameras]
            if self._base_args.camera in available_ids:
                self.active_index = self._base_args.camera
            else:
                self.active_index = available_ids[0]
        elif self._base_args.simulate:
            self.active_index = self._base_args.camera

    def list_cameras(self) -> List[Dict[str, object]]:
        with self._lock:
            if self._cameras:
                return list(self._cameras)
            self._cameras = list_available_cameras()
            return list(self._cameras)

    def start_initial(self) -> None:
        with self._lock:
            if self.active_index is None and not self._base_args.simulate:
                return
            self._launch_locked(self.active_index if self.active_index is not None else 0)

    def select_camera(self, index: int) -> bool:
        with self._lock:
            if self._cameras and index not in [int(c["index"]) for c in self._cameras]:
                return False
            self._stop_locked()
            self.active_index = index
            self._launch_locked(index)
            return True

    def stop(self) -> None:
        with self._lock:
            self._stop_locked()

    def _stop_locked(self) -> None:
        if self._stop_event is not None:
            self._stop_event.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=2.5)
        self._stop_event = None
        self._thread = None

    def _launch_locked(self, cam_index: int) -> None:
        args = copy.copy(self._base_args)
        args.camera = cam_index
        stop_ev = threading.Event()
        self._stop_event = stop_ev

        def _runner() -> None:
            try:
                tracker.run_tracker(args, stop_event=stop_ev)
            except RuntimeError as exc:
                print(f"[AERO-BRUSH] Camera notice: {exc}")
            except Exception as exc:
                print(f"[AERO-BRUSH] Tracker stopped: {exc}")

        thread = threading.Thread(target=_runner, name=f"AeroBrushTracker-{cam_index}", daemon=True)
        thread.start()
        self._thread = thread


def _start_bridge_thread(
    udp_port: int,
    ws_port: int,
    http_port: int,
    camera_api: CameraController,
) -> threading.Thread:
    def _runner() -> None:
        try:
            asyncio.run(bridge.run_bridge("127.0.0.1", udp_port, ws_port, http_port, camera_api=camera_api))
        except Exception as exc:
            print(f"[AERO-BRUSH] Bridge error: {exc}")

    thread = threading.Thread(target=_runner, name="AeroBrushBridge", daemon=True)
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

    tracker_args = tracker.parse_args(raw_args)
    if "--port" not in raw_args:
        tracker_args.port = udp_port

    camera_ctrl = CameraController(tracker_args)

    _start_bridge_thread(udp_port=udp_port, ws_port=ws_port, http_port=http_port, camera_api=camera_ctrl)
    _wait_for_port("127.0.0.1", http_port, timeout_s=4.0)

    camera_ctrl.start_initial()
    studio_url = f"http://127.0.0.1:{http_port}/?ws={ws_port}"

    if use_browser:
        webbrowser.open(studio_url)
        try:
            while True:
                time.sleep(0.5)
        except KeyboardInterrupt:
            camera_ctrl.stop()
        return

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

    window.events.closing += lambda: camera_ctrl.stop()

    icon_path = ROOT / "assets" / "icon.ico"
    webview.start(icon=str(icon_path) if icon_path.exists() else None)
    camera_ctrl.stop()


if __name__ == "__main__":
    main()
