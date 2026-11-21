"""AERO-BRUSH Python Vision & Gesture Tracking Backend.

Captures webcam frames via OpenCV, extracts 21 3D hand landmarks with MediaPipe,
applies a One-Euro Filter to the index fingertip (Landmark 8), estimates
normalized Z-depth from apparent palm size, classifies gestures with a 5-frame
debounce buffer, and streams JSON telemetry at 60Hz over UDP (127.0.0.1:5055).
"""

from __future__ import annotations

import argparse
import math
import sys
import threading
import time
import urllib.request
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

import cv2
import mediapipe as mp
import numpy as np

from backend.cameras import open_camera
from backend.depth import PalmDepthEstimator
from backend.filters import OneEuroFilter2D
from backend.gesture_engine import GestureState, GestureStateMachine
from backend.udp_telemetry import UDPTelemetryPublisher, build_telemetry_packet

HAND_LANDMARKER_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
)
BUNDLE_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
DEFAULT_MODEL_PATH = BUNDLE_ROOT / "models" / "hand_landmarker.task"

HAND_CONNECTIONS: Tuple[Tuple[int, int], ...] = (
    (0, 1), (1, 2), (2, 3), (3, 4),
    (0, 5), (5, 6), (6, 7), (7, 8),
    (5, 9), (9, 10), (10, 11), (11, 12),
    (9, 13), (13, 14), (14, 15), (15, 16),
    (13, 17), (0, 17), (17, 18), (18, 19), (19, 20),
)

STATE_BGR_COLORS = {
    GestureState.IDLE.value: (160, 170, 190),
    GestureState.HOVER.value: (255, 240, 0),      # Cyan in BGR
    GestureState.DRAW.value: (147, 46, 255),      # Magenta/Pink in BGR
    GestureState.PINCH_SCALE.value: (32, 176, 255),  # Amber in BGR
    GestureState.ERASE.value: (60, 120, 255),     # Warm orange-amber
    GestureState.ORBIT_3D.value: (255, 180, 90),  # Electric blue
}


def ensure_hand_landmarker_model(model_path: Path = DEFAULT_MODEL_PATH) -> Path:
    """Download MediaPipe's hand_landmarker.task bundle if not already cached."""
    if model_path.exists() and model_path.stat().st_size > 0:
        return model_path
    model_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"[AERO-BRUSH] Downloading hand_landmarker.task to {model_path} ...")
    urllib.request.urlretrieve(HAND_LANDMARKER_URL, str(model_path))
    return model_path


class MediaPipeHandDetector:
    """Compatibility wrapper supporting both MediaPipe Solutions and Tasks APIs."""

    def __init__(
        self,
        model_path: Path = DEFAULT_MODEL_PATH,
        min_detection_confidence: float = 0.75,
        min_tracking_confidence: float = 0.75,
    ) -> None:
        self._use_solutions = hasattr(mp, "solutions") and hasattr(mp.solutions, "hands")
        self._last_timestamp_ms = 0

        if self._use_solutions:
            self._hands = mp.solutions.hands.Hands(
                static_image_mode=False,
                max_num_hands=1,
                min_detection_confidence=min_detection_confidence,
                min_tracking_confidence=min_tracking_confidence,
            )
            self._landmarker = None
        else:
            from mediapipe.tasks.python import BaseOptions
            from mediapipe.tasks.python import vision

            resolved = ensure_hand_landmarker_model(model_path)
            options = vision.HandLandmarkerOptions(
                base_options=BaseOptions(model_asset_path=str(resolved)),
                running_mode=vision.RunningMode.VIDEO,
                num_hands=1,
                min_hand_detection_confidence=min_detection_confidence,
                min_hand_presence_confidence=min_detection_confidence,
                min_tracking_confidence=min_tracking_confidence,
            )
            self._landmarker = vision.HandLandmarker.create_from_options(options)
            self._hands = None

    def detect(self, rgb_frame: np.ndarray, timestamp_s: float) -> Optional[List[List[float]]]:
        """Extract 21 normalized [x, y, z] landmarks for the primary hand, or None."""
        if self._use_solutions:
            result = self._hands.process(rgb_frame)
            if not result.multi_hand_landmarks:
                return None
            hand = result.multi_hand_landmarks[0]
            return [[float(lm.x), float(lm.y), float(lm.z)] for lm in hand.landmark]

        ts_ms = max(self._last_timestamp_ms + 1, int(timestamp_s * 1000.0))
        self._last_timestamp_ms = ts_ms
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        result = self._landmarker.detect_for_video(mp_image, ts_ms)
        if not result.hand_landmarks:
            return None
        hand = result.hand_landmarks[0]
        return [[float(lm.x), float(lm.y), float(lm.z)] for lm in hand]

    def close(self) -> None:
        if self._hands is not None:
            self._hands.close()
        if self._landmarker is not None:
            self._landmarker.close()


class HandTrackingPipeline:
    """Core tracking pipeline converting raw 21 landmarks into filtered telemetry."""

    def __init__(self, debounce_frames: int = 5) -> None:
        self.cursor_filter = OneEuroFilter2D(min_cutoff=1.2, beta=1.4, d_cutoff=1.0)
        self.depth_estimator = PalmDepthEstimator()
        self.gesture_machine = GestureStateMachine(debounce_frames=debounce_frames)
        self._last_cursor: List[float] = [0.5, 0.5, 0.0]

    def process_landmarks(
        self,
        landmarks: Optional[Sequence[Sequence[float]]],
        timestamp_s: float,
        fps: int,
    ):
        """Filter cursor, estimate Z-depth, update gesture state, and build packet."""
        state, pinch_dist = self.gesture_machine.update(landmarks)

        if landmarks is not None and len(landmarks) >= 21:
            raw_x, raw_y = float(landmarks[8][0]), float(landmarks[8][1])
            fx, fy = self.cursor_filter.filter(raw_x, raw_y, timestamp_s)
            fx = max(0.0, min(1.0, fx))
            fy = max(0.0, min(1.0, fy))
            fz = self.depth_estimator.estimate(landmarks, timestamp=timestamp_s)
            self._last_cursor = [fx, fy, fz]
            lm_list = [[float(p[0]), float(p[1]), float(p[2])] for p in landmarks[:21]]
        else:
            self.cursor_filter.reset()
            self.depth_estimator.reset()
            lm_list = []

        return build_telemetry_packet(
            state=state.value,
            cursor=self._last_cursor,
            pinch_dist=pinch_dist,
            landmarks=lm_list,
            fps=fps,
        )


def draw_preview_overlay(
    frame: np.ndarray,
    landmarks: Sequence[Sequence[float]],
    state: str,
    cursor: Sequence[float],
    pinch_dist: float,
    fps: int,
) -> np.ndarray:
    """Render 21-keypoint skeleton and telemetry HUD onto an OpenCV BGR frame."""
    h, w = frame.shape[:2]
    accent = STATE_BGR_COLORS.get(state, (255, 240, 0))

    if landmarks:
        pts = [(int(lm[0] * w), int(lm[1] * h)) for lm in landmarks]
        for a, b in HAND_CONNECTIONS:
            cv2.line(frame, pts[a], pts[b], (110, 125, 145), 2, cv2.LINE_AA)

        for idx, (px, py) in enumerate(pts):
            is_tip = idx in (4, 8, 12, 16, 20)
            radius = 6 if is_tip else 3
            color = accent if idx in _highlight_joints_for_state(state) else (220, 230, 245)
            cv2.circle(frame, (px, py), radius, color, -1, cv2.LINE_AA)

        if state == GestureState.PINCH_SCALE.value:
            cv2.line(frame, pts[4], pts[8], accent, 3, cv2.LINE_AA)

    cx = int(cursor[0] * w)
    cy = int(cursor[1] * h)
    cv2.circle(frame, (cx, cy), 12, accent, 2, cv2.LINE_AA)

    # Top HUD banner
    overlay = frame.copy()
    cv2.rectangle(overlay, (12, 12), (370, 108), (15, 20, 28), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)
    cv2.rectangle(frame, (12, 12), (370, 108), accent, 1)

    cv2.putText(frame, f"AERO-BRUSH | {state}", (24, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.65, accent, 2, cv2.LINE_AA)
    cv2.putText(
        frame,
        f"XYZ: ({cursor[0]:.2f}, {cursor[1]:.2f}, {cursor[2]:+.2f})  FPS: {fps}",
        (24, 68),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        (225, 235, 245),
        1,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        f"Pinch: {pinch_dist:.3f}   Press Q / ESC to quit",
        (24, 94),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.48,
        (165, 180, 200),
        1,
        cv2.LINE_AA,
    )
    return frame


def _highlight_joints_for_state(state: str) -> Tuple[int, ...]:
    if state == GestureState.DRAW.value:
        return (5, 6, 7, 8)
    if state == GestureState.HOVER.value:
        return (8, 12)
    if state == GestureState.PINCH_SCALE.value:
        return (4, 8)
    if state == GestureState.ERASE.value:
        return (4, 8, 12, 16, 20)
    if state == GestureState.ORBIT_3D.value:
        return (0, 5, 9, 13, 17)
    return (8,)


def _generate_simulated_hand(t: float) -> List[List[float]]:
    """Synthesize a smooth 21-landmark hand performing a figure-eight DRAW stroke."""
    cx = 0.5 + 0.22 * math.sin(t * 1.1)
    cy = 0.45 + 0.16 * math.sin(t * 2.2)
    palm_h = 0.17 + 0.04 * math.sin(t * 0.7)

    # Wrist & MCPs relative to index tip target
    wrist = [cx - 0.04, cy + palm_h * 1.4, 0.0]
    lm = [[wrist[0], wrist[1], 0.0] for _ in range(21)]
    lm[0] = wrist
    lm[1] = [wrist[0] - 0.04, wrist[1] - palm_h * 0.3, 0.0]
    lm[2] = [wrist[0] - 0.06, wrist[1] - palm_h * 0.55, 0.0]
    lm[3] = [wrist[0] - 0.04, wrist[1] - palm_h * 0.65, 0.0]
    lm[4] = [wrist[0] - 0.01, wrist[1] - palm_h * 0.60, 0.0]

    # Index extended -> DRAW
    lm[5] = [cx - 0.02, wrist[1] - palm_h * 0.95, 0.0]
    lm[6] = [cx - 0.01, cy + palm_h * 0.25, 0.0]
    lm[7] = [cx, cy + palm_h * 0.12, 0.0]
    lm[8] = [cx, cy, 0.0]

    # Middle, ring, pinky folded
    for base, x_off in ((9, 0.01), (13, 0.04), (17, 0.07)):
        mcp_y = wrist[1] - palm_h
        pip_y = mcp_y - 0.04
        lm[base] = [wrist[0] + x_off, mcp_y, 0.0]
        lm[base + 1] = [wrist[0] + x_off, pip_y, 0.0]
        lm[base + 2] = [wrist[0] + x_off, pip_y + 0.03, 0.0]
        lm[base + 3] = [wrist[0] + x_off, pip_y + 0.06, 0.0]
    return lm


def run_tracker(args: argparse.Namespace, stop_event: Optional[threading.Event] = None) -> None:
    """Run the real-time tracking (or simulation) loop and stream UDP telemetry."""
    pipeline = HandTrackingPipeline(debounce_frames=5)
    target_dt = 1.0 / max(1, args.hz)

    cap = None
    detector = None
    if not args.simulate:
        cap = open_camera(args.camera, args.width, args.height, args.hz)
        if not cap.isOpened():
            raise RuntimeError(
                f"Could not open camera index {args.camera}."
            )
        detector = MediaPipeHandDetector(model_path=Path(args.model))

    print(
        f"[AERO-BRUSH] Streaming {'SIMULATED' if args.simulate else 'WEBCAM'} "
        f"telemetry @ {args.hz}Hz -> udp://{args.host}:{args.port}"
    )

    frame_counter = 0
    fps_timer = time.perf_counter()
    current_fps = args.hz

    with UDPTelemetryPublisher(host=args.host, port=args.port) as publisher:
        try:
            while not (stop_event is not None and stop_event.is_set()):
                loop_start = time.perf_counter()

                if args.simulate:
                    landmarks = _generate_simulated_hand(loop_start)
                    frame = np.zeros((480, 640, 3), dtype=np.uint8) if args.preview else None
                else:
                    ok, frame = cap.read()
                    if not ok:
                        time.sleep(0.005)
                        continue
                    if not args.no_mirror:
                        frame = cv2.flip(frame, 1)
                    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    landmarks = detector.detect(rgb, loop_start)

                frame_counter += 1
                elapsed_fps = loop_start - fps_timer
                if elapsed_fps >= 0.5:
                    current_fps = max(1, int(round(frame_counter / elapsed_fps)))
                    frame_counter = 0
                    fps_timer = loop_start

                packet = pipeline.process_landmarks(landmarks, loop_start, current_fps)
                publisher.send(packet)

                if args.preview and frame is not None:
                    annotated = draw_preview_overlay(
                        frame,
                        packet.landmarks,
                        packet.state,
                        packet.cursor,
                        packet.pinch_dist,
                        packet.fps,
                    )
                    cv2.imshow("AERO-BRUSH Vision Tracker", annotated)
                    key = cv2.waitKey(1) & 0xFF
                    if key in (27, ord("q")):
                        break

                spent = time.perf_counter() - loop_start
                remaining = target_dt - spent
                if remaining > 0:
                    time.sleep(remaining)
        except KeyboardInterrupt:
            pass
        finally:
            if cap is not None:
                cap.release()
            if detector is not None:
                detector.close()
            if args.preview:
                cv2.destroyAllWindows()


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AERO-BRUSH Hand-Gesture Tracker")
    parser.add_argument("--camera", type=int, default=0, help="OpenCV camera index (default: 0)")
    parser.add_argument("--width", type=int, default=1280, help="Capture width (default: 1280)")
    parser.add_argument("--height", type=int, default=720, help="Capture height (default: 720)")
    parser.add_argument("--host", default="127.0.0.1", help="UDP destination host (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=5055, help="UDP destination port (default: 5055)")
    parser.add_argument("--hz", type=int, default=60, help="Target telemetry rate in Hz (default: 60)")
    parser.add_argument("--model", default=str(DEFAULT_MODEL_PATH), help="Path to hand_landmarker.task")
    parser.add_argument("--preview", action="store_true", help="Show OpenCV 21-keypoint debug window")
    parser.add_argument("--no-mirror", action="store_true", help="Disable horizontal selfie mirroring")
    parser.add_argument("--simulate", action="store_true", help="Emit synthetic figure-eight hand telemetry")
    return parser.parse_args(argv)


if __name__ == "__main__":
    run_tracker(parse_args())
