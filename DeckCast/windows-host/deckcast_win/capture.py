"""Windows screen capture VideoStreamTrack for aiortc (dxcam DXGI with mss fallback)."""

from __future__ import annotations

import asyncio
import fractions
import threading
import time
from typing import Optional

import av
import mss
import numpy as np
from aiortc import VideoStreamTrack

try:
    import dxcam  # type: ignore
except Exception:
    dxcam = None


class ScreenVideoTrack(VideoStreamTrack):
    """Captures the Windows desktop and emits capped 720p/1080p frames for WebRTC."""

    kind = "video"

    def __init__(
        self,
        max_width: int = 1280,
        max_height: int = 720,
        fps: int = 60,
        monitor_index: int = 1,
    ) -> None:
        super().__init__()
        self.max_width = max_width
        self.max_height = max_height
        self.fps = max(1, min(60, fps))
        self.monitor_index = monitor_index

        self._frame_interval = 1.0 / self.fps
        self._start_time: Optional[float] = None
        self._timestamp = 0
        self._clock_rate = 90000
        self._dx_cam = None
        # mss holds thread-local GDI handles on Windows, so keep one instance per worker thread.
        self._thread_local = threading.local()
        self._last_frame: Optional[np.ndarray] = None

        if dxcam is not None:
            try:
                cam = dxcam.create(output_color="BGR")
                if cam is not None:
                    cam.start(target_fps=self.fps, video_mode=True)
                    self._dx_cam = cam
            except Exception:
                self._dx_cam = None

    def stop(self) -> None:
        if self._dx_cam is not None:
            try:
                self._dx_cam.stop()
            except Exception:
                pass
            self._dx_cam = None
        super().stop()

    def _grab_bgr_frame(self) -> np.ndarray:
        if self._dx_cam is not None:
            frame = self._dx_cam.get_latest_frame()
            if frame is not None:
                self._last_frame = frame
                return frame

        sct = getattr(self._thread_local, "sct", None)
        if sct is None:
            sct = mss.mss()
            self._thread_local.sct = sct

        try:
            monitors = sct.monitors
            idx = self.monitor_index if 0 <= self.monitor_index < len(monitors) else 1
            shot = sct.grab(monitors[idx])
            bgra = np.asarray(shot, dtype=np.uint8)
            bgr = bgra[:, :, :3].copy()
            self._last_frame = bgr
            return bgr
        except Exception:
            if self._last_frame is not None:
                return self._last_frame
            return np.zeros((self.max_height, self.max_width, 3), dtype=np.uint8)

    async def recv(self) -> av.VideoFrame:
        now = time.perf_counter()
        if self._start_time is None:
            self._start_time = now
        else:
            next_pts_time = self._start_time + (self._timestamp / self._clock_rate) + self._frame_interval
            delay = next_pts_time - now
            if delay > 0:
                await asyncio.sleep(delay)

        self._timestamp += int(self._clock_rate / self.fps)
        bgr = await asyncio.to_thread(self._grab_bgr_frame)

        frame = av.VideoFrame.from_ndarray(bgr, format="bgr24")
        if frame.width > self.max_width or frame.height > self.max_height:
            scale = min(self.max_width / frame.width, self.max_height / frame.height)
            target_w = max(2, int(frame.width * scale) & ~1)
            target_h = max(2, int(frame.height * scale) & ~1)
            frame = frame.reformat(width=target_w, height=target_h, format="yuv420p")
        else:
            target_w = frame.width & ~1
            target_h = frame.height & ~1
            frame = frame.reformat(width=target_w, height=target_h, format="yuv420p")

        frame.pts = self._timestamp
        frame.time_base = fractions.Fraction(1, self._clock_rate)
        return frame
