"""DeckCast Windows Companion entrypoint (GUI, headless --host, or --connect IP:PORT)."""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from pathlib import Path

if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")

from deckcast_win.host_server import WindowsHostServer
from deckcast_win.protocol import DEFAULT_WS_PORT, DiscoveredHost, get_primary_lan_ip


def _resolve_icon_path() -> Path:
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return base / "assets" / "icon.ico"


async def _run_headless_host(port: int, width: int, height: int, fps: int) -> None:
    srv = WindowsHostServer(port=port, max_width=width, max_height=height, fps=fps)
    bound_port = await srv.start()
    print(f"[DeckCast] Windows Host streaming at ws://{get_primary_lan_ip()}:{bound_port}/signal ({width}x{height}@{fps})")
    print("[DeckCast] Advertising _remotedisplay._tcp. on LAN. Press Ctrl+C to stop.")
    try:
        while True:
            await asyncio.sleep(1.0)
    except (KeyboardInterrupt, asyncio.CancelledError):
        pass
    finally:
        await srv.stop()


def main() -> None:
    parser = argparse.ArgumentParser(description="DeckCast Windows Host & Controller")
    parser.add_argument("--host", action="store_true", help="Run headless Windows->Android Host server")
    parser.add_argument("--connect", default="", help="Open Android->Windows viewer for IP[:PORT]")
    parser.add_argument("--port", type=int, default=DEFAULT_WS_PORT, help="WebSocket signaling port")
    parser.add_argument("--width", type=int, default=1280, help="Max capture width")
    parser.add_argument("--height", type=int, default=720, help="Max capture height")
    parser.add_argument("--fps", type=int, default=60, help="Target capture FPS")
    args = parser.parse_args()

    if args.host:
        try:
            asyncio.run(_run_headless_host(args.port, args.width, args.height, args.fps))
        except KeyboardInterrupt:
            pass
        return

    from PyQt6.QtGui import QIcon
    from PyQt6.QtWidgets import QApplication
    from deckcast_win.ui_app import DeckCastControlWindow, RemoteViewerWindow, _AsyncBridge

    app = QApplication(sys.argv)
    icon_path = _resolve_icon_path()
    if icon_path.exists():
        app.setWindowIcon(QIcon(str(icon_path)))

    if args.connect:
        bridge = _AsyncBridge()
        parts = args.connect.strip().split(":")
        ip = parts[0]
        port = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else DEFAULT_WS_PORT
        host = DiscoveredHost(f"CLI-{ip}", ip, "android", ip, port)
        viewer = RemoteViewerWindow(host, bridge, on_closed=lambda _: app.quit())
        viewer.show()
        rc = app.exec()
        bridge.shutdown()
        sys.exit(rc)

    win = DeckCastControlWindow()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
