"""Win32 SendInput pointer & keyboard injector for normalized DataChannel packets."""

from __future__ import annotations

import ctypes
import sys
from ctypes import wintypes
from typing import Dict, Tuple

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_VIRTUALDESK = 0x4000
MOUSEEVENTF_ABSOLUTE = 0x8000

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

WHEEL_DELTA = 120

# Map Android KeyEvent.KEYCODE_* constants from the unified protocol to Win32 VK codes.
ANDROID_TO_WIN_VK: Dict[int, int] = {
    3: 0x5B,   # KEYCODE_HOME -> VK_LWIN
    4: 0x1B,   # KEYCODE_BACK -> VK_ESCAPE
    19: 0x26,  # DPAD_UP -> VK_UP
    20: 0x28,  # DPAD_DOWN -> VK_DOWN
    21: 0x25,  # DPAD_LEFT -> VK_LEFT
    22: 0x27,  # DPAD_RIGHT -> VK_RIGHT
    61: 0x09,  # TAB -> VK_TAB
    62: 0x20,  # SPACE -> VK_SPACE
    66: 0x0D,  # ENTER -> VK_RETURN
    67: 0x08,  # DEL -> VK_BACK
    111: 0x1B, # ESCAPE -> VK_ESCAPE
    112: 0x2E, # FORWARD_DEL -> VK_DELETE
}


def scale_normalized_to_absolute(x: float, y: float) -> Tuple[int, int]:
    """Scale normalized [0.0, 1.0] coordinates into Win32 MOUSEEVENTF_ABSOLUTE [0, 65535]."""
    nx = max(0.0, min(1.0, float(x)))
    ny = max(0.0, min(1.0, float(y)))
    return int(round(nx * 65535)), int(round(ny * 65535))


if sys.platform == "win32":
    ULONG_PTR = ctypes.POINTER(wintypes.ULONG)

    class MOUSEINPUT(ctypes.Structure):
        _fields_ = [
            ("dx", wintypes.LONG),
            ("dy", wintypes.LONG),
            ("mouseData", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ctypes.c_size_t),
        ]

    class KEYBDINPUT(ctypes.Structure):
        _fields_ = [
            ("wVk", wintypes.WORD),
            ("wScan", wintypes.WORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ctypes.c_size_t),
        ]

    class HARDWAREINPUT(ctypes.Structure):
        _fields_ = [
            ("uMsg", wintypes.DWORD),
            ("wParamL", wintypes.WORD),
            ("wParamH", wintypes.WORD),
        ]

    class _INPUT_UNION(ctypes.Union):
        _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]

    class INPUT(ctypes.Structure):
        _anonymous_ = ("u",)
        _fields_ = [("type", wintypes.DWORD), ("u", _INPUT_UNION)]

    _user32 = ctypes.WinDLL("user32", use_last_error=True)
    _send_input = _user32.SendInput
    _send_input.argtypes = [wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
    _send_input.restype = wintypes.UINT
else:
    _send_input = None


class WindowsInputInjector:
    """Injects pointer and keyboard events into Windows via native SendInput."""

    def __init__(self, dry_run: bool = False) -> None:
        self.dry_run = dry_run or (sys.platform != "win32")
        self.recorded_events: list[dict] = []

    def handle_packet(self, msg: dict) -> None:
        pkt_type = msg.get("type")
        if pkt_type == "pointer":
            self._handle_pointer(msg)
        elif pkt_type == "key":
            self._handle_key(msg)

    def _handle_pointer(self, msg: dict) -> None:
        action = str(msg.get("action", "MOVE")).upper()
        ax, ay = scale_normalized_to_absolute(msg.get("x", 0.5), msg.get("y", 0.5))
        scroll_y = float(msg.get("scrollY", 0.0))

        if self.dry_run:
            self.recorded_events.append({"kind": "pointer", "action": action, "ax": ax, "ay": ay, "scrollY": scroll_y})
            return

        abs_flags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK
        if action == "DOWN":
            self._emit_mouse(ax, ay, abs_flags | MOUSEEVENTF_LEFTDOWN)
        elif action == "MOVE":
            self._emit_mouse(ax, ay, abs_flags)
        elif action == "UP":
            self._emit_mouse(ax, ay, abs_flags | MOUSEEVENTF_LEFTUP)
        elif action == "SCROLL":
            delta = int(round(scroll_y * WHEEL_DELTA))
            self._emit_mouse(ax, ay, abs_flags)
            if delta != 0:
                self._emit_mouse(0, 0, MOUSEEVENTF_WHEEL, mouse_data=delta & 0xFFFFFFFF)

    def _handle_key(self, msg: dict) -> None:
        action = str(msg.get("action", "DOWN")).upper()
        key_code = int(msg.get("keyCode", 0))
        text = str(msg.get("text", ""))

        if self.dry_run:
            self.recorded_events.append({"kind": "key", "action": action, "keyCode": key_code, "text": text})
            return

        is_up = action == "UP"
        if text and action == "DOWN":
            for ch in text:
                code = ord(ch)
                self._emit_key(0, code, KEYEVENTF_UNICODE)
                self._emit_key(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP)
            return

        vk = ANDROID_TO_WIN_VK.get(key_code)
        if vk is not None:
            flags = KEYEVENTF_KEYUP if is_up else 0
            self._emit_key(vk, 0, flags)

    def _emit_mouse(self, dx: int, dy: int, flags: int, mouse_data: int = 0) -> None:
        inp = INPUT(
            type=INPUT_MOUSE,
            mi=MOUSEINPUT(dx=dx, dy=dy, mouseData=mouse_data, dwFlags=flags, time=0, dwExtraInfo=0),
        )
        _send_input(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

    def _emit_key(self, vk: int, scan: int, flags: int) -> None:
        inp = INPUT(
            type=INPUT_KEYBOARD,
            ki=KEYBDINPUT(wVk=vk, wScan=scan, dwFlags=flags, time=0, dwExtraInfo=0),
        )
        _send_input(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
