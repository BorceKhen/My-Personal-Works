"""One-command launcher for AERO-BRUSH / FloatPaint (bridge + vision tracker)."""

from __future__ import annotations

import asyncio
import socket
import sys
import threading
import time
import webbrowser
from pathlib import Path

import bridge
import tracker

ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))


def _is_port_in_use(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.3)
        return sock.connect_ex((host, port)) == 0


def _wait_for_port(host: str, port: int, timeout_s: float = 3.0) -> bool:
    deadline = time.perf_counter() + timeout_s
    while time.perf_counter() < deadline:
        if _is_port_in_use(host, port):
            return True
        time.sleep(0.05)
    return False


def _start_bridge_thread() -> threading.Thread:
    def _runner() -> None:
        try:
            asyncio.run(bridge.run_bridge("127.0.0.1", 5055, 8765, 8080))
        except Exception as exc:
            print(f"[AERO-BRUSH] Bridge error: {exc}")

    thread = threading.Thread(target=_runner, name="AeroBrushBridge", daemon=True)
    thread.start()
    return thread


def main() -> None:
    if _is_port_in_use("127.0.0.1", 8080) or _is_port_in_use("127.0.0.1", 8765):
        print("[AERO-BRUSH] Existing bridge detected on :8080/:8765 — reusing it.")
    else:
        _start_bridge_thread()
        _wait_for_port("127.0.0.1", 8080, timeout_s=3.0)

    tracker_args = tracker.parse_args(["--preview", *sys.argv[1:]])
    webbrowser.open("http://127.0.0.1:8080")
    print("[AERO-BRUSH] Studio running at http://127.0.0.1:8080 — press Q in camera window or Ctrl+C to stop.")

    try:
        tracker.run_tracker(tracker_args)
    except RuntimeError as exc:
        print(f"\n[AERO-BRUSH] Notice: {exc}")
        print("[AERO-BRUSH] Studio UI remains active at http://127.0.0.1:8080 (use Mouse Simulator or Browser Cam).")
        try:
            input("Press Enter or close this window to exit FloatPaint...")
        except (EOFError, KeyboardInterrupt):
            pass
    except KeyboardInterrupt:
        pass
    finally:
        print("[AERO-BRUSH] Shutting down.")


if __name__ == "__main__":
    main()
