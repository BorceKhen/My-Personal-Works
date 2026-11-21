"""Unit tests for GestureState classification and 5-frame debounce buffer."""

import unittest

from backend.gesture_engine import GestureState, GestureStateMachine, classify_gesture


def _base_hand():
    """Neutral hand upright in normalized image coordinates (y=0 top, y=1 bottom)."""
    lm = [[0.5, 0.75, 0.0] for _ in range(21)]
    lm[0] = [0.50, 0.80, 0.0]  # Wrist

    # Thumb (neutral/tucked)
    lm[1] = [0.44, 0.74, 0.0]
    lm[2] = [0.41, 0.68, 0.0]
    lm[3] = [0.43, 0.65, 0.0]
    lm[4] = [0.46, 0.66, 0.0]

    # MCPs
    lm[5] = [0.45, 0.60, 0.0]
    lm[9] = [0.50, 0.59, 0.0]
    lm[13] = [0.55, 0.60, 0.0]
    lm[17] = [0.60, 0.62, 0.0]

    # Default folded fingers (tip > pip > mcp)
    for base, x in ((5, 0.45), (9, 0.50), (13, 0.55), (17, 0.60)):
        lm[base + 1] = [x, 0.54, 0.0]  # PIP
        lm[base + 2] = [x, 0.57, 0.0]  # DIP
        lm[base + 3] = [x, 0.63, 0.0]  # TIP below PIP and MCP
    return lm


def _pose_draw():
    lm = _base_hand()
    lm[6] = [0.45, 0.50, 0.0]
    lm[7] = [0.45, 0.43, 0.0]
    lm[8] = [0.45, 0.36, 0.0]  # Index extended above PIP (6)
    return lm


def _pose_hover():
    lm = _pose_draw()
    lm[10] = [0.50, 0.49, 0.0]
    lm[11] = [0.50, 0.42, 0.0]
    lm[12] = [0.50, 0.35, 0.0]  # Middle extended above PIP (10)
    return lm


def _pose_pinch():
    lm = _pose_draw()
    lm[4] = [0.46, 0.40, 0.0]
    lm[8] = [0.47, 0.41, 0.0]  # dist ~ 0.014 < 0.045
    return lm


def _pose_erase():
    lm = _pose_hover()
    lm[3] = [0.37, 0.62, 0.0]
    lm[4] = [0.32, 0.56, 0.0]  # Thumb extended above IP/MCP and abducted
    lm[14] = [0.55, 0.50, 0.0]
    lm[15] = [0.55, 0.43, 0.0]
    lm[16] = [0.55, 0.37, 0.0]
    lm[18] = [0.60, 0.53, 0.0]
    lm[19] = [0.60, 0.47, 0.0]
    lm[20] = [0.60, 0.41, 0.0]
    return lm


def _pose_orbit_fist():
    lm = _base_hand()
    lm[4] = [0.48, 0.69, 0.0]  # Thumb folded below MCP (2)
    return lm


class TestGestureEngine(unittest.TestCase):
    def test_all_five_gesture_classifications(self) -> None:
        self.assertEqual(classify_gesture(_pose_draw())[0], GestureState.DRAW)
        self.assertEqual(classify_gesture(_pose_hover())[0], GestureState.HOVER)
        state_pinch, pinch_dist = classify_gesture(_pose_pinch())
        self.assertEqual(state_pinch, GestureState.PINCH_SCALE)
        self.assertLess(pinch_dist, 0.045)
        self.assertEqual(classify_gesture(_pose_erase())[0], GestureState.ERASE)
        self.assertEqual(classify_gesture(_pose_orbit_fist())[0], GestureState.ORBIT_3D)
        self.assertEqual(classify_gesture(None)[0], GestureState.IDLE)

    def test_five_frame_debounce_buffer(self) -> None:
        sm = GestureStateMachine(debounce_frames=5)

        # 4 frames of DRAW must NOT flip confirmed state from IDLE yet
        for _ in range(4):
            state, _ = sm.update(_pose_draw())
            self.assertEqual(state, GestureState.IDLE)

        # 5th consecutive frame confirms DRAW
        state, _ = sm.update(_pose_draw())
        self.assertEqual(state, GestureState.DRAW)

        # A 2-frame glitch of ERASE followed by DRAW must stay DRAW
        sm.update(_pose_erase())
        sm.update(_pose_erase())
        self.assertEqual(sm.confirmed_state, GestureState.DRAW)
        sm.update(_pose_draw())
        self.assertEqual(sm.confirmed_state, GestureState.DRAW)

        # 5 consecutive frames of HOVER transitions cleanly to HOVER
        for _ in range(4):
            state, _ = sm.update(_pose_hover())
            self.assertEqual(state, GestureState.DRAW)
        state, _ = sm.update(_pose_hover())
        self.assertEqual(state, GestureState.HOVER)


if __name__ == "__main__":
    unittest.main()
