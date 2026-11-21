"""Unit tests for PalmDepthEstimator."""

import unittest

from backend.depth import PalmDepthEstimator


def _make_landmarks_with_palm_size(palm_size: float, raw_z: float = 0.0):
    lm = [[0.5, 0.5, raw_z] for _ in range(21)]
    lm[0] = [0.5, 0.7, raw_z]
    lm[9] = [0.5, 0.7 - palm_size, raw_z]
    return lm


class TestPalmDepthEstimator(unittest.TestCase):
    def test_maps_palm_size_to_normalized_range(self) -> None:
        est = PalmDepthEstimator(min_palm_size=0.06, max_palm_size=0.30)
        self.assertAlmostEqual(est.estimate(_make_landmarks_with_palm_size(0.06)), -1.0, places=5)
        self.assertAlmostEqual(est.estimate(_make_landmarks_with_palm_size(0.18)), 0.0, places=5)
        self.assertAlmostEqual(est.estimate(_make_landmarks_with_palm_size(0.30)), 1.0, places=5)

    def test_clamps_extreme_palm_sizes(self) -> None:
        est = PalmDepthEstimator(min_palm_size=0.06, max_palm_size=0.30)
        self.assertEqual(est.estimate(_make_landmarks_with_palm_size(0.01)), -1.0)
        self.assertEqual(est.estimate(_make_landmarks_with_palm_size(0.60)), 1.0)

    def test_ignores_noisy_raw_mediapipe_z(self) -> None:
        est = PalmDepthEstimator(min_palm_size=0.06, max_palm_size=0.30)
        z1 = est.estimate(_make_landmarks_with_palm_size(0.18, raw_z=-0.95))
        z2 = est.estimate(_make_landmarks_with_palm_size(0.18, raw_z=0.95))
        self.assertAlmostEqual(z1, z2, places=6)


if __name__ == "__main__":
    unittest.main()
