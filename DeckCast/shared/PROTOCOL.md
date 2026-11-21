# DeckCast Wire Protocol Specification

## 1. Discovery (`_remotedisplay._tcp.`)
Hosts advertise on the local network via mDNS/NSD (`_remotedisplay._tcp.`) with TXT records:
- `deviceName`: UTF-8 display name (e.g., `Pixel 8` or `DESKTOP-WORK`)
- `osType`: `"android"` or `"windows"`
- `wsPort`: TCP port of the embedded WebSocket signaling server (default `9443`)

Manual fallback: Controllers can connect directly to `ws://<host-ip>:<wsPort>/signal`.

## 2. Zero-Internet WebRTC ICE Configuration
- `iceServers = emptyList()` on Android (`PeerConnection.RTCConfiguration(emptyList())`) and `iceServers=[]` on Python `aiortc`.
- Only LAN Host ICE candidates (Wi-Fi / hotspot subnet) are gathered and exchanged.

## 3. Local WebSocket Signaling (`ws://<ip>:<wsPort>/signal`)
1. Controller opens WebSocket to Host and creates a WebRTC `Offer` with:
   - `recvonly` H.264 video transceiver (Host streams screen video to Controller).
   - Unordered/unreliable DataChannel named `"input"` (`ordered = false`, `maxRetransmits = 0`).
2. Controller sends `{"type": "hello", ...}` and `{"type": "offer", "sessionId": "...", "sdp": "..."}`.
3. Host responds with `{"type": "hello", ...}` and `{"type": "answer", "sessionId": "...", "sdp": "..."}`.
4. Both peers trickle `{"type": "ice_candidate", "sessionId": "...", "sdpMid": "...", "sdpMLineIndex": 0, "candidate": "..."}`.

## 4. DataChannel `"input"` Packets
- **Pointer**:
  ```json
  {"type": "pointer", "action": "DOWN|MOVE|UP|SCROLL", "x": 0.5, "y": 0.5, "scrollY": 0.0}
  ```
  - `(x, y)` are normalized `[0.0, 1.0]` relative to the video frame (`0,0` = top-left, `1,1` = bottom-right).
- **Keyboard**:
  ```json
  {"type": "key", "action": "DOWN|UP", "keyCode": 66, "text": ""}
  ```
  - `keyCode` uses Android `KeyEvent.KEYCODE_*` constants (`0` when delivering committed Unicode `text` strings).
