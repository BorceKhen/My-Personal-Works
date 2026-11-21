"""Unit tests for OneEuroFilter and OneEuroFilter2D."""

import math
import random
import unittest

from backend.filters import OneEuroFilter, OneEuroFilter2D


class TestOneEuroFilter(unittest.TestCase):
    def test_reduces_stationary_jitter(self) -> None:
        rng = random.Random(42)
        flt = OneEuroFilter(min_cutoff=1.0, beta=0.8, d_cutoff=1.0)
        raw_samples = [0.5 + rng.gauss(0.0, 0.015) for _ in range(120)]
        filtered = [flt.filter(val, i / 60.0) for i, val in enumerate(raw_samples)]

        raw_std = math.sqrt(sum((x - 0.5) ** 2 for x in raw_samples[20:]) / 100)
        flt_std = math.sqrt(sum((x - 0.5) ** 2 for x in filtered[20:]) / 100)
        self.assertLess(flt_std, raw_std * 0.65)

    def test_tracks_fast_motion_with_low_lag(self) -> None:
        flt = OneEuroFilter(min_cutoff=1.2, beta=1.8, d_cutoff=1.0)
        val = 0.0
        for i in range(1, 40):
            target = 0.1 if i < 15 else 0.85
            val = flt.filter(target, i / 60.0)
        self.assertGreater(val, 0.80)

    def test_2d_reset_reinitializes(self) -> None:
        flt = OneEuroFilter2D()
        flt.filter(0.1, 0.1, 0.0)
        flt.filter(0.2, 0.2, 0.016)
        flt.reset()
        x, y = flt.filter(0.9, 0.8, 0.032)
        self.assertAlmostEqual(x, 0.9, places=6)
        self.assertAlmostEqual(y, 0.8, places=6)


if __name__ == "__main__":
    unittest.main()
