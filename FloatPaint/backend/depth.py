"""Apparent palm-size 3D depth (Z) estimator."""

from __future__ import annotations

import math
from typing import Optional, Sequence

from .filters import OneEuroFilter


class PalmDepthEstimator:
    """Estimate normalized Z-depth in [-1.0, 1.0] from apparent palm size.

    Uses the 2D Euclidean distance between Wrist (0) and Middle Finger MCP (9)
    instead of raw MediaPipe Z values, which exhibit high monocular drift.
    Smaller apparent palm (farther from camera) maps toward -1.0; larger
    apparent palm (closer to camera) maps toward +1.0.
    """

    def __init__(
        self,
        min_palm_size: float = 0.06,
        max_palm_size: float = 0.30,
        smooth_min_cutoff: float = 1.0,
        smooth_beta: float = 0.4,
    ) -> None:
        if max_palm_size <= min_palm_size:
            raise ValueError("max_palm_size must be greater than min_palm_size")
        self.min_palm_size = float(min_palm_size)
        self.max_palm_size = float(max_palm_size)
        self._z_filter = OneEuroFilter(
            min_cutoff=smooth_min_cutoff,
            beta=smooth_beta,
            d_cutoff=1.0,
        )

    def reset(self) -> None:
        """Reset Z-depth temporal smoothing."""
        self._z_filter.reset()

    @staticmethod
    def compute_palm_size(landmarks: Sequence[Sequence[float]]) -> float:
        """Compute 2D Euclidean distance between Wrist (0) and Middle MCP (9)."""
        if len(landmarks) <= 9:
            raise ValueError("Expected at least 10 landmarks to compute palm size")
        wrist = landmarks[0]
        middle_mcp = landmarks[9]
        return math.hypot(float(middle_mcp[0]) - float(wrist[0]), float(middle_mcp[1]) - float(wrist[1]))

    def map_palm_size(self, palm_size: float) -> float:
        """Map apparent palm size linearly and clamp to [-1.0, 1.0]."""
        t = (palm_size - self.min_palm_size) / (self.max_palm_size - self.min_palm_size)
        z = t * 2.0 - 1.0
        return max(-1.0, min(1.0, z))

    def estimate(
        self,
        landmarks: Sequence[Sequence[float]],
        timestamp: Optional[float] = None,
    ) -> float:
        """Return normalized Z-depth in [-1.0, 1.0], optionally smoothed by timestamp."""
        palm_size = self.compute_palm_size(landmarks)
        z_raw = self.map_palm_size(palm_size)
        if timestamp is None:
            return z_raw
        return max(-1.0, min(1.0, self._z_filter.filter(z_raw, timestamp)))
