"""One-Euro Filter implementation for low-latency hand-landmark smoothing."""

from __future__ import annotations

import math
from typing import Optional, Tuple


def _smoothing_factor(dt: float, cutoff: float) -> float:
    """Compute exponential smoothing alpha for a cutoff frequency and timestep."""
    r = 2.0 * math.pi * cutoff * dt
    return r / (r + 1.0)


def _exponential_smoothing(alpha: float, value: float, prev_value: float) -> float:
    """Apply a single first-order low-pass filter step."""
    return alpha * value + (1.0 - alpha) * prev_value


class OneEuroFilter:
    """Adaptive One-Euro Filter (Casiez et al., 2012) for 1D real-time signals."""

    def __init__(
        self,
        min_cutoff: float = 1.2,
        beta: float = 1.4,
        d_cutoff: float = 1.0,
    ) -> None:
        if min_cutoff <= 0.0:
            raise ValueError("min_cutoff must be positive")
        if beta < 0.0:
            raise ValueError("beta must be non-negative")
        if d_cutoff <= 0.0:
            raise ValueError("d_cutoff must be positive")

        self.min_cutoff = float(min_cutoff)
        self.beta = float(beta)
        self.d_cutoff = float(d_cutoff)

        self._x_prev: Optional[float] = None
        self._dx_prev: float = 0.0
        self._t_prev: Optional[float] = None

    def reset(self) -> None:
        """Clear filter history so the next sample initializes fresh."""
        self._x_prev = None
        self._dx_prev = 0.0
        self._t_prev = None

    def filter(self, value: float, timestamp: float) -> float:
        """Filter a noisy sample at the given monotonic timestamp (seconds)."""
        x = float(value)
        t = float(timestamp)

        if self._x_prev is None or self._t_prev is None:
            self._x_prev = x
            self._dx_prev = 0.0
            self._t_prev = t
            return x

        dt = t - self._t_prev
        if dt <= 1e-6:
            dt = 1.0 / 60.0

        # Estimate and low-pass filter the signal derivative to adapt cutoff.
        a_d = _smoothing_factor(dt, self.d_cutoff)
        dx = (x - self._x_prev) / dt
        dx_hat = _exponential_smoothing(a_d, dx, self._dx_prev)

        cutoff = self.min_cutoff + self.beta * abs(dx_hat)
        a = _smoothing_factor(dt, cutoff)
        x_hat = _exponential_smoothing(a, x, self._x_prev)

        self._x_prev = x_hat
        self._dx_prev = dx_hat
        self._t_prev = t
        return x_hat


class OneEuroFilter2D:
    """Paired One-Euro Filter for 2D normalized cursor coordinates (x, y)."""

    def __init__(
        self,
        min_cutoff: float = 1.2,
        beta: float = 1.4,
        d_cutoff: float = 1.0,
    ) -> None:
        self.x_filter = OneEuroFilter(min_cutoff=min_cutoff, beta=beta, d_cutoff=d_cutoff)
        self.y_filter = OneEuroFilter(min_cutoff=min_cutoff, beta=beta, d_cutoff=d_cutoff)

    def reset(self) -> None:
        """Reset both coordinate filters."""
        self.x_filter.reset()
        self.y_filter.reset()

    def filter(self, x: float, y: float, timestamp: float) -> Tuple[float, float]:
        """Return filtered (x, y) coordinates for the given timestamp."""
        return (
            self.x_filter.filter(x, timestamp),
            self.y_filter.filter(y, timestamp),
        )
