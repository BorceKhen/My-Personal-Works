"""Camera enumeration and capture helpers for FloatPaint."""

from __future__ import annotations

import ctypes
import sys
import uuid
from ctypes import wintypes
from typing import Dict, List

import cv2


def _enumerate_directshow_names() -> List[str]:
    """Return DirectShow video input device FriendlyNames in CAP_DSHOW index order."""
    if sys.platform != "win32":
        return []

    ole32 = ctypes.windll.ole32
    oleaut32 = ctypes.windll.oleaut32

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD),
            ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD),
            ("Data4", ctypes.c_ubyte * 8),
        ]

        @classmethod
        def from_str(cls, text: str) -> "GUID":
            u = uuid.UUID(text)
            return cls(
                u.time_low,
                u.time_mid,
                u.time_hi_version,
                (ctypes.c_ubyte * 8).from_buffer_copy(u.bytes[8:]),
            )

    class VARIANT(ctypes.Structure):
        _fields_ = [
            ("vt", wintypes.WORD),
            ("r1", wintypes.WORD),
            ("r2", wintypes.WORD),
            ("r3", wintypes.WORD),
            ("val", ctypes.c_void_p),
            ("pad", ctypes.c_void_p),
        ]

    def vtable_call(ptr: ctypes.c_void_p, index: int, restype, *argtypes):
        vtbl = ctypes.cast(ptr, ctypes.POINTER(ctypes.POINTER(ctypes.c_void_p))).contents
        fn = ctypes.WINFUNCTYPE(restype, ctypes.c_void_p, *argtypes)(vtbl[index])
        return lambda *args: fn(ptr, *args)

    clsid_sys_dev_enum = GUID.from_str("62BE5D10-60EB-11d0-BD3B-00A0C911CE86")
    iid_icreate_dev_enum = GUID.from_str("29840822-5B84-11D0-BD3B-00A0C911CE86")
    clsid_video_input = GUID.from_str("860BB310-5D01-11d0-BD3B-00A0C911CE86")
    iid_iproperty_bag = GUID.from_str("55272A00-42CB-11CE-8135-00AA004BB851")

    names: List[str] = []
    co_hr = ole32.CoInitialize(None)
    dev_enum = ctypes.c_void_p()
    try:
        hr = ole32.CoCreateInstance(
            ctypes.byref(clsid_sys_dev_enum),
            None,
            1,
            ctypes.byref(iid_icreate_dev_enum),
            ctypes.byref(dev_enum),
        )
        if hr != 0 or not dev_enum.value:
            return []

        enum_moniker = ctypes.c_void_p()
        hr_enum = vtable_call(
            dev_enum,
            3,
            ctypes.c_long,
            ctypes.POINTER(GUID),
            ctypes.POINTER(ctypes.c_void_p),
            wintypes.DWORD,
        )(ctypes.byref(clsid_video_input), ctypes.byref(enum_moniker), 0)

        if hr_enum == 0 and enum_moniker.value:
            idx = 0
            while True:
                moniker = ctypes.c_void_p()
                fetched = wintypes.ULONG(0)
                hr_next = vtable_call(
                    enum_moniker,
                    3,
                    ctypes.c_long,
                    wintypes.ULONG,
                    ctypes.POINTER(ctypes.c_void_p),
                    ctypes.POINTER(wintypes.ULONG),
                )(1, ctypes.byref(moniker), ctypes.byref(fetched))
                if hr_next != 0 or not moniker.value:
                    break

                prop_bag = ctypes.c_void_p()
                hr_bind = vtable_call(
                    moniker,
                    9,
                    ctypes.c_long,
                    ctypes.c_void_p,
                    ctypes.c_void_p,
                    ctypes.POINTER(GUID),
                    ctypes.POINTER(ctypes.c_void_p),
                )(None, None, ctypes.byref(iid_iproperty_bag), ctypes.byref(prop_bag))

                friendly = f"Camera {idx}"
                if hr_bind == 0 and prop_bag.value:
                    var = VARIANT()
                    oleaut32.VariantInit(ctypes.byref(var))
                    hr_read = vtable_call(
                        prop_bag,
                        3,
                        ctypes.c_long,
                        wintypes.LPCWSTR,
                        ctypes.POINTER(VARIANT),
                        ctypes.c_void_p,
                    )("FriendlyName", ctypes.byref(var), None)
                    if hr_read == 0 and var.vt == 8 and var.val:
                        friendly = ctypes.wstring_at(var.val)
                    oleaut32.VariantClear(ctypes.byref(var))
                    vtable_call(prop_bag, 2, wintypes.ULONG)()

                vtable_call(moniker, 2, wintypes.ULONG)()
                names.append(friendly)
                idx += 1

            vtable_call(enum_moniker, 2, wintypes.ULONG)()
    finally:
        if dev_enum.value:
            vtable_call(dev_enum, 2, wintypes.ULONG)()
        if co_hr in (0, 1):
            ole32.CoUninitialize()

    return names


def list_available_cameras(max_probe: int = 6) -> List[Dict[str, object]]:
    """Return available cameras as [{'index': int, 'name': str}, ...]."""
    if sys.platform == "win32":
        dshow_names = _enumerate_directshow_names()
        if dshow_names:
            return [{"index": idx, "name": name} for idx, name in enumerate(dshow_names)]

    cameras: List[Dict[str, object]] = []
    for idx in range(max_probe):
        cap = cv2.VideoCapture(idx)
        if cap.isOpened():
            cameras.append({"index": idx, "name": f"Camera {idx}"})
            cap.release()
    return cameras


def open_camera(index: int, width: int, height: int, hz: int) -> cv2.VideoCapture:
    """Open a VideoCapture device using CAP_DSHOW first on Windows."""
    if sys.platform == "win32":
        cap = cv2.VideoCapture(index, cv2.CAP_DSHOW)
        if not cap.isOpened():
            cap.release()
            cap = cv2.VideoCapture(index)
    else:
        cap = cv2.VideoCapture(index)

    if cap.isOpened():
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        cap.set(cv2.CAP_PROP_FPS, hz)
    return cap
