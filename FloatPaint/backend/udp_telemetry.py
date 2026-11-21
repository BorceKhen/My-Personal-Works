"""UDP JSON telemetry serialization and 60Hz broadcaster."""

from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import List, Sequence


@dataclass
class TelemetryPacket:
    """Telemetry payload streamed over UDP to the studio bridge."""

    state: str
    cursor: List[float]
    pinch_dist: float
    landmarks: List[List[float]]
    fps: int

    def to_json(self) -> str:
        """Serialize telemetry into compact JSON matching the studio wire schema."""
        return json.dumps(
            {
                "state": self.state,
                "cursor": [round(float(v), 5) for v in self.cursor[:3]],
                "pinch_dist": round(float(self.pinch_dist), 5),
                "landmarks": [
                    [round(float(c), 5) for c in lm[:3]] for lm in self.landmarks
                ],
                "fps": int(self.fps),
            },
            separators=(",", ":"),
        )


def build_telemetry_packet(
    state: str,
    cursor: Sequence[float],
    pinch_dist: float,
    landmarks: Sequence[Sequence[float]],
    fps: int,
) -> TelemetryPacket:
    """Create a validated TelemetryPacket from tracker state."""
    return TelemetryPacket(
        state=str(state),
        cursor=[float(cursor[0]), float(cursor[1]), float(cursor[2])],
        pinch_dist=float(pinch_dist),
        landmarks=[[float(p[0]), float(p[1]), float(p[2])] for p in landmarks],
        fps=int(fps),
    )


class UDPTelemetryPublisher:
    """Non-blocking UDP datagram sender targeting 127.0.0.1:5055."""

    def __init__(self, host: str = "127.0.0.1", port: int = 5055) -> None:
        self.host = host
        self.port = int(port)
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def send(self, packet: TelemetryPacket) -> int:
        """Serialize and transmit one telemetry packet over UDP."""
        payload = packet.to_json().encode("utf-8")
        return self._sock.sendto(payload, (self.host, self.port))

    def close(self) -> None:
        """Close the underlying UDP socket."""
        self._sock.close()

    def __enter__(self) -> "UDPTelemetryPublisher":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()
