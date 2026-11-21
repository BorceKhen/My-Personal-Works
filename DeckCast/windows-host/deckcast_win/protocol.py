"""Shared JSON signaling and DataChannel 'input' protocol helpers."""

from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import Any, Dict, Optional

from aiortc import RTCIceCandidate
from aiortc.sdp import candidate_from_sdp

SERVICE_TYPE = "_remotedisplay._tcp.local."
DEFAULT_WS_PORT = 9443
INPUT_CHANNEL_NAME = "input"


@dataclass(frozen=True)
class DiscoveredHost:
    service_name: str
    device_name: str
    os_type: str
    host_address: str
    ws_port: int

    @property
    def session_key(self) -> str:
        return f"{self.host_address}:{self.ws_port}"


def get_primary_lan_ip() -> str:
    """Return the local LAN IPv4 address used to reach peers (zero-internet safe)."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.168.255.255", 9))
        return sock.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        sock.close()


def make_hello(session_id: str, device_name: str, os_type: str = "windows") -> str:
    return json.dumps({
        "type": "hello",
        "sessionId": session_id,
        "deviceName": device_name,
        "osType": os_type,
        "protocolVersion": 1,
    })


def make_offer(session_id: str, sdp: str) -> str:
    return json.dumps({"type": "offer", "sessionId": session_id, "sdp": sdp})


def make_answer(session_id: str, sdp: str) -> str:
    return json.dumps({"type": "answer", "sessionId": session_id, "sdp": sdp})


def make_ice_candidate(session_id: str, sdp_mid: str, sdp_mline_index: int, candidate: str) -> str:
    return json.dumps({
        "type": "ice_candidate",
        "sessionId": session_id,
        "sdpMid": sdp_mid,
        "sdpMLineIndex": int(sdp_mline_index),
        "candidate": candidate,
    })


def make_bye(session_id: str, reason: str = "closed") -> str:
    return json.dumps({"type": "bye", "sessionId": session_id, "reason": reason})


def make_error(code: str, message: str, session_id: str = "") -> str:
    payload: Dict[str, Any] = {"type": "error", "code": code, "message": message}
    if session_id:
        payload["sessionId"] = session_id
    return json.dumps(payload)


def make_pointer_packet(action: str, x: float, y: float, scroll_y: float = 0.0) -> str:
    return json.dumps({
        "type": "pointer",
        "action": action,
        "x": max(0.0, min(1.0, float(x))),
        "y": max(0.0, min(1.0, float(y))),
        "scrollY": float(scroll_y),
    })


def make_key_packet(action: str, key_code: int, text: str = "") -> str:
    return json.dumps({
        "type": "key",
        "action": action,
        "keyCode": int(key_code),
        "text": text,
    })


def parse_json_message(raw: str) -> Optional[Dict[str, Any]]:
    try:
        obj = json.loads(raw)
        return obj if isinstance(obj, dict) else None
    except (ValueError, TypeError):
        return None


def parse_ice_candidate(msg: Dict[str, Any]) -> Optional[RTCIceCandidate]:
    raw_cand = str(msg.get("candidate", "")).strip()
    if not raw_cand:
        return None
    if raw_cand.startswith("a=candidate:"):
        raw_cand = raw_cand[len("a=candidate:"):]
    elif raw_cand.startswith("candidate:"):
        raw_cand = raw_cand[len("candidate:"):]
    cand = candidate_from_sdp(raw_cand)
    cand.sdpMid = str(msg.get("sdpMid", "0"))
    cand.sdpMLineIndex = int(msg.get("sdpMLineIndex", 0))
    return cand
