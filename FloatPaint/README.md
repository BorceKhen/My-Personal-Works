# AERO-BRUSH — 2D/3D Hand-Gesture Air Painting System

A modular real-time hand-gesture painting studio combining a Python computer-vision tracking backend (`tracker.py`), a zero-dependency UDP-to-WebSocket studio bridge (`bridge.py`), and a dark glassmorphism 2D/3D WebGL frontend (`frontend/`).

## Architecture

- **`tracker.py` & `backend/`** (Python 3.10+):
  - MediaPipe hand landmark extraction (`max_num_hands=1`, `min_detection_confidence=0.75`, `min_tracking_confidence=0.75`) supporting both `mediapipe.tasks` (`HandLandmarker`) and legacy `mp.solutions.hands`.
  - **One-Euro Filter** (`backend/filters.py`) applied to Index Fingertip (`Landmark 8`) `(x, y)` coordinates.
  - **Apparent Palm-Size $Z$-Depth** (`backend/depth.py`) computed from Wrist (`0`) to Middle Finger MCP (`9`) distance and mapped to `[-1.0, 1.0]`.
  - **Gesture State Machine** (`backend/gesture_engine.py`) with a **5-frame debounce buffer** for `DRAW`, `HOVER`, `PINCH_SCALE` (`< 0.045`), `ERASE`, and `ORBIT_3D`.
  - **60Hz UDP Telemetry** (`127.0.0.1:5055`) + optional OpenCV skeleton preview (`--preview`) or synthetic telemetry (`--simulate`).
- **`bridge.py`**:
  - Listens for UDP JSON telemetry on `127.0.0.1:5055`, broadcasts via RFC 6455 WebSocket on `ws://127.0.0.1:8765`, and serves the studio UI on `http://127.0.0.1:8080`.
- **`frontend/`**:
  - **2D Canvas**: High-DPI anti-aliased quadratic spline renderer with neon outer glow, wide eraser, undo, and `.PNG` export.
  - **3D Space**: Three.js viewport with reference grid, dynamic $Z$-depth plane, continuous `TubeGeometry` strokes, fist-orbit controls, 3D eraser, and `.OBJ` / `.GLTF` export.
  - **Air-Hover Dock & PiP Hand Monitor**: `48x48px+` controls with `0.8s` radial dwell selection, plus a bottom-right 21-keypoint skeleton monitor.
  - **Camera Selection**: Automatically enumerates available system cameras and allows live switching from the studio header.

## Quick Start

1. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   npm install
   ```

2. **Start the WebSocket + Studio HTTP bridge:**
   ```bash
   python bridge.py
   ```
   Then open **http://127.0.0.1:8080** in your browser.

3. **Start the Python webcam gesture tracker:**
   ```bash
   python tracker.py --preview
   ```
   *(No webcam handy? Run `python tracker.py --simulate --preview`, or use the studio's built-in Mouse Simulator / Browser Cam selector.)*

## Running Tests
```bash
python -m unittest discover -s tests -v
```
