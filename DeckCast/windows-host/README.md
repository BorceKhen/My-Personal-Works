# DeckCast Windows Companion (`/windows-host`)

100% offline/LAN Python 3.11+ companion supporting **both**:
- **Windows → Android (Host Mode)**: Captures the Windows desktop (`dxcam` DXGI Desktop Duplication if installed, or `mss`), streams H.264/VP8 WebRTC video (`iceServers=[]`), advertises `_remotedisplay._tcp.` via `zeroconf`, hosts a local WebSocket signaling server on `:9443/signal`, and injects remote pointer/keyboard events via Win32 `SendInput`.
- **Android → Windows (Controller Mode)**: Discovers Android Hosts (`_remotedisplay._tcp.`) or connects via manual `IP:Port`, receives WebRTC video in a PyQt6 floating/resizable desktop viewer window, and forwards mouse drags, wheel scroll, and keyboard input over the `"input"` DataChannel.

## Quick Start

```powershell
pip install -r requirements.txt
python main.py                 # Opens PyQt6 Control Center (Host + Android->Windows Viewer)
python main.py --host          # Headless Windows Host mode on port 9443
python main.py --connect 192.168.1.42:9443   # Direct Windows Controller -> Android Host
```

## Running Tests

```powershell
python -m unittest discover -s tests -v
```
