"""AERO-BRUSH computer-vision tracking and telemetry backend package."""

from .depth import PalmDepthEstimator
from .filters import OneEuroFilter, OneEuroFilter2D
from .gesture_engine import GestureState, GestureStateMachine, classify_gesture
from .udp_telemetry import TelemetryPacket, UDPTelemetryPublisher

__all__ = [
    "OneEuroFilter",
    "OneEuroFilter2D",
    "PalmDepthEstimator",
    "GestureState",
    "GestureStateMachine",
    "classify_gesture",
    "TelemetryPacket",
    "UDPTelemetryPublisher",
]
