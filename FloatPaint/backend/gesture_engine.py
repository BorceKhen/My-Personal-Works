"""Hand-gesture classification and 5-frame debounced state machine."""

from __future__ import annotations

import math
from collections import deque
from enum import Enum
from typing import Deque, Optional, Sequence, Tuple


class GestureState(str, Enum):
    """Supported AERO-BRUSH hand-gesture states."""

    IDLE = "IDLE"
    HOVER = "HOVER"
    DRAW = "DRAW"
    PINCH_SCALE = "PINCH_SCALE"
    ERASE = "ERASE"
    ORBIT_3D = "ORBIT_3D"


PINCH_THRESHOLD = 0.045


def _dist2d(a: Sequence[float], b: Sequence[float]) -> float:
    return math.hypot(float(a[0]) - float(b[0]), float(a[1]) - float(b[1]))


def compute_pinch_distance(landmarks: Sequence[Sequence[float]]) -> float:
    """Return 2D Euclidean distance between Thumb Tip (4) and Index Tip (8)."""
    if len(landmarks) < 21:
        raise ValueError("Expected 21 hand landmarks")
    return _dist2d(landmarks[4], landmarks[8])


def _is_thumb_extended(landmarks: Sequence[Sequence[float]], palm_size: float) -> bool:
    """Check whether thumb tip (4) is extended above/away from its knuckle joints."""
    thumb_tip = landmarks[4]
    thumb_ip = landmarks[3]
    thumb_mcp = landmarks[2]
    middle_mcp = landmarks[9]
    index_mcp = landmarks[5]

    above_knuckle = float(thumb_tip[1]) < float(thumb_ip[1]) and float(thumb_tip[1]) < float(thumb_mcp[1])
    abducted = _dist2d(thumb_tip, middle_mcp) > _dist2d(thumb_ip, middle_mcp)
    clear_of_index = _dist2d(thumb_tip, index_mcp) > max(0.045, 0.35 * palm_size)
    return above_knuckle and (abducted or clear_of_index)


def classify_gesture(
    landmarks: Optional[Sequence[Sequence[float]]],
    pinch_threshold: float = PINCH_THRESHOLD,
) -> Tuple[GestureState, float]:
    """Classify raw single-frame hand pose into a GestureState and pinch distance.

    Normalized image coordinates assume y=0 at top and y=1 at bottom, so
    "above" corresponds to smaller y values and "below" to larger y values.
    """
    if landmarks is None or len(landmarks) < 21:
        return GestureState.IDLE, 1.0

    pinch_dist = compute_pinch_distance(landmarks)
    palm_size = max(1e-5, _dist2d(landmarks[0], landmarks[9]))

    # Finger extension (tip above PIP) and folding (tip below PIP / MCP).
    index_ext = float(landmarks[8][1]) < float(landmarks[6][1])
    middle_ext = float(landmarks[12][1]) < float(landmarks[10][1])
    ring_ext = float(landmarks[16][1]) < float(landmarks[14][1])
    pinky_ext = float(landmarks[20][1]) < float(landmarks[18][1])

    middle_fold = float(landmarks[12][1]) > float(landmarks[10][1])
    ring_fold = float(landmarks[16][1]) > float(landmarks[14][1])
    pinky_fold = float(landmarks[20][1]) > float(landmarks[18][1])

    # Closed-fist MCP checks (tip below MCP joint).
    index_below_mcp = float(landmarks[8][1]) > float(landmarks[5][1])
    middle_below_mcp = float(landmarks[12][1]) > float(landmarks[9][1])
    ring_below_mcp = float(landmarks[16][1]) > float(landmarks[13][1])
    pinky_below_mcp = float(landmarks[20][1]) > float(landmarks[17][1])

    thumb_ext = _is_thumb_extended(landmarks, palm_size)
    thumb_folded = float(landmarks[4][1]) > float(landmarks[2][1]) or (
        not thumb_ext and _dist2d(landmarks[4], landmarks[9]) < 0.85 * palm_size
    )

    # 1. ORBIT_3D: closed fist — all fingertips folded below MCP joints.
    if (
        index_below_mcp
        and middle_below_mcp
        and ring_below_mcp
        and pinky_below_mcp
        and thumb_folded
    ):
        return GestureState.ORBIT_3D, pinch_dist

    # 2. PINCH_SCALE: thumb tip (4) and index tip (8) pinched closer than threshold.
    if pinch_dist < pinch_threshold:
        return GestureState.PINCH_SCALE, pinch_dist

    # 3. ERASE: all 5 fingertips extended above their respective knuckle joints.
    if (
        thumb_ext
        and index_ext
        and middle_ext
        and ring_ext
        and pinky_ext
        and float(landmarks[8][1]) < float(landmarks[5][1])
        and float(landmarks[12][1]) < float(landmarks[9][1])
        and float(landmarks[16][1]) < float(landmarks[13][1])
        and float(landmarks[20][1]) < float(landmarks[17][1])
    ):
        return GestureState.ERASE, pinch_dist

    # 4. HOVER: index (8) and middle (12) extended; ring (16) and pinky (20) folded.
    if index_ext and middle_ext and ring_fold and pinky_fold:
        return GestureState.HOVER, pinch_dist

    # 5. DRAW: index (8) extended above PIP (6); middle, ring, and pinky folded.
    if index_ext and middle_fold and ring_fold and pinky_fold:
        return GestureState.DRAW, pinch_dist

    return GestureState.IDLE, pinch_dist


class GestureStateMachine:
    """Gesture state machine guarded by a N-frame consecutive debounce buffer."""

    def __init__(self, debounce_frames: int = 5, pinch_threshold: float = PINCH_THRESHOLD) -> None:
        if debounce_frames < 1:
            raise ValueError("debounce_frames must be >= 1")
        self.debounce_frames = int(debounce_frames)
        self.pinch_threshold = float(pinch_threshold)
        self._buffer: Deque[GestureState] = deque(maxlen=self.debounce_frames)
        self.confirmed_state: GestureState = GestureState.IDLE
        self.raw_state: GestureState = GestureState.IDLE

    def reset(self) -> None:
        """Clear debounce history and revert to IDLE."""
        self._buffer.clear()
        self.confirmed_state = GestureState.IDLE
        self.raw_state = GestureState.IDLE

    def update(self, landmarks: Optional[Sequence[Sequence[float]]]) -> Tuple[GestureState, float]:
        """Process one frame of landmarks and return (confirmed_state, pinch_dist)."""
        raw_state, pinch_dist = classify_gesture(landmarks, pinch_threshold=self.pinch_threshold)
        self.raw_state = raw_state
        self._buffer.append(raw_state)

        if len(self._buffer) == self.debounce_frames and all(
            s == raw_state for s in self._buffer
        ):
            self.confirmed_state = raw_state

        return self.confirmed_state, pinch_dist
