/**
 * Telemetry input sources:
 * 1. WebSocket (:8765) bridge receiving UDP (:5055) telemetry from tracker.py
 * 2. Built-in MediaPipe JS Webcam Fallback (@mediapipe/tasks-vision)
 * 3. Built-in Mouse-to-Hand Simulator with articulated 21-landmark synthesis
 */

import { GESTURE_STATES } from './state.js';

class OneEuroFilterJS {
  constructor(minCutoff = 1.2, beta = 1.4, dCutoff = 1.0) {
    this.minCutoff = minCutoff;
    this.beta = beta;
    this.dCutoff = dCutoff;
    this.xPrev = null;
    this.dxPrev = 0;
    this.tPrev = null;
  }

  reset() {
    this.xPrev = null;
    this.dxPrev = 0;
    this.tPrev = null;
  }

  _alpha(dt, cutoff) {
    const r = 2 * Math.PI * cutoff * dt;
    return r / (r + 1);
  }

  filter(val, tSec) {
    if (this.xPrev === null || this.tPrev === null) {
      this.xPrev = val;
      this.dxPrev = 0;
      this.tPrev = tSec;
      return val;
    }
    let dt = tSec - this.tPrev;
    if (dt <= 1e-6) dt = 1 / 60;
    const aD = this._alpha(dt, this.dCutoff);
    const dx = (val - this.xPrev) / dt;
    const dxHat = aD * dx + (1 - aD) * this.dxPrev;
    const cutoff = this.minCutoff + this.beta * Math.abs(dxHat);
    const a = this._alpha(dt, cutoff);
    const xHat = a * val + (1 - a) * this.xPrev;
    this.xPrev = xHat;
    this.dxPrev = dxHat;
    this.tPrev = tSec;
    return xHat;
  }
}

function dist2d(a, b) {
  return Math.hypot(a[0] - b[0], a[1] - b[1]);
}

function classifyLandmarksJS(lm) {
  if (!lm || lm.length < 21) return { state: GESTURE_STATES.IDLE, pinchDist: 1.0 };
  const pinchDist = dist2d(lm[4], lm[8]);
  const palmSize = Math.max(1e-5, dist2d(lm[0], lm[9]));

  const indexExt = lm[8][1] < lm[6][1];
  const middleExt = lm[12][1] < lm[10][1];
  const ringExt = lm[16][1] < lm[14][1];
  const pinkyExt = lm[20][1] < lm[18][1];

  const middleFold = lm[12][1] > lm[10][1];
  const ringFold = lm[16][1] > lm[14][1];
  const pinkyFold = lm[20][1] > lm[18][1];

  const indexBelowMcp = lm[8][1] > lm[5][1];
  const middleBelowMcp = lm[12][1] > lm[9][1];
  const ringBelowMcp = lm[16][1] > lm[13][1];
  const pinkyBelowMcp = lm[20][1] > lm[17][1];

  const thumbExt =
    lm[4][1] < lm[3][1] &&
    lm[4][1] < lm[2][1] &&
    (dist2d(lm[4], lm[9]) > dist2d(lm[3], lm[9]) || dist2d(lm[4], lm[5]) > Math.max(0.045, 0.35 * palmSize));

  const thumbFolded =
    lm[4][1] > lm[2][1] || (!thumbExt && dist2d(lm[4], lm[9]) < 0.85 * palmSize);

  if (indexBelowMcp && middleBelowMcp && ringBelowMcp && pinkyBelowMcp && thumbFolded) {
    return { state: GESTURE_STATES.ORBIT_3D, pinchDist };
  }
  if (pinchDist < 0.045) {
    return { state: GESTURE_STATES.PINCH_SCALE, pinchDist };
  }
  if (
    thumbExt &&
    indexExt &&
    middleExt &&
    ringExt &&
    pinkyExt &&
    lm[8][1] < lm[5][1] &&
    lm[12][1] < lm[9][1] &&
    lm[16][1] < lm[13][1] &&
    lm[20][1] < lm[17][1]
  ) {
    return { state: GESTURE_STATES.ERASE, pinchDist };
  }
  if (indexExt && middleExt && ringFold && pinkyFold) {
    return { state: GESTURE_STATES.HOVER, pinchDist };
  }
  if (indexExt && middleFold && ringFold && pinkyFold) {
    return { state: GESTURE_STATES.DRAW, pinchDist };
  }
  return { state: GESTURE_STATES.IDLE, pinchDist };
}

function synthesizeHandLandmarks(nx, ny, nz, state, pinchDist) {
  const palm = 0.16 + nz * 0.04;
  const wrist = [nx - 0.03, Math.min(0.95, ny + palm * 1.45), 0];
  const lm = Array.from({ length: 21 }, () => [...wrist]);

  lm[1] = [wrist[0] - 0.04, wrist[1] - palm * 0.3, 0];
  lm[2] = [wrist[0] - 0.06, wrist[1] - palm * 0.55, 0];

  if (state === GESTURE_STATES.ERASE) {
    lm[3] = [wrist[0] - 0.09, wrist[1] - palm * 0.72, 0];
    lm[4] = [wrist[0] - 0.12, wrist[1] - palm * 0.88, 0];
  } else if (state === GESTURE_STATES.PINCH_SCALE) {
    lm[3] = [nx - pinchDist * 1.2, ny + 0.03, 0];
    lm[4] = [nx - pinchDist * 0.7, ny + pinchDist * 0.7, 0];
  } else {
    lm[3] = [wrist[0] - 0.04, wrist[1] - palm * 0.64, 0];
    lm[4] = [wrist[0] - 0.01, wrist[1] - palm * 0.58, 0];
  }

  const fingerSpecs = [
    { base: 5, dx: -0.02, ext: state !== GESTURE_STATES.ORBIT_3D },
    { base: 9, dx: 0.01, ext: state === GESTURE_STATES.HOVER || state === GESTURE_STATES.ERASE },
    { base: 13, dx: 0.04, ext: state === GESTURE_STATES.ERASE },
    { base: 17, dx: 0.07, ext: state === GESTURE_STATES.ERASE },
  ];

  for (const spec of fingerSpecs) {
    const mcp = [wrist[0] + spec.dx, wrist[1] - palm, 0];
    lm[spec.base] = mcp;
    if (spec.base === 5 && state !== GESTURE_STATES.ORBIT_3D) {
      lm[6] = [mcp[0] + (nx - mcp[0]) * 0.45, mcp[1] + (ny - mcp[1]) * 0.45, 0];
      lm[7] = [mcp[0] + (nx - mcp[0]) * 0.75, mcp[1] + (ny - mcp[1]) * 0.75, 0];
      lm[8] = [nx, ny, 0];
    } else if (spec.ext) {
      lm[spec.base + 1] = [mcp[0] + spec.dx * 0.15, mcp[1] - palm * 0.42, 0];
      lm[spec.base + 2] = [mcp[0] + spec.dx * 0.25, mcp[1] - palm * 0.72, 0];
      lm[spec.base + 3] = [mcp[0] + spec.dx * 0.35, mcp[1] - palm * 0.98, 0];
    } else {
      lm[spec.base + 1] = [mcp[0], mcp[1] - 0.02, 0];
      lm[spec.base + 2] = [mcp[0], mcp[1] + 0.02, 0];
      lm[spec.base + 3] = [mcp[0], mcp[1] + 0.05, 0];
    }
  }

  return lm;
}

export class InputSourceManager {
  constructor({ stageEl, videoEl, onTelemetry, onConnectionChange }) {
    this.stageEl = stageEl;
    this.videoEl = videoEl;
    this.onTelemetry = onTelemetry;
    this.onConnectionChange = onConnectionChange;

    this.mode = 'auto'; // 'auto' | 'webcam' | 'simulator'
    this._ws = null;
    this._wsRetryTimer = null;
    this._udpLastPacketMs = 0;
    this._udpConnected = false;

    // Mouse simulator state
    this._sim = {
      nx: 0.5,
      ny: 0.5,
      nz: 0.0,
      leftDown: false,
      rightDown: false,
      eraseKey: false,
      pinchKey: false,
      pinchDist: 0.024,
      activeInsideWindow: false,
    };

    // Browser MediaPipe JS fallback state
    this._camStream = null;
    this._handLandmarker = null;
    this._camLoopId = null;
    this._xFilter = new OneEuroFilterJS();
    this._yFilter = new OneEuroFilterJS();
    this._zFilter = new OneEuroFilterJS(1.0, 0.4, 1.0);
    this._debounceBuf = [];
    this._confirmedState = GESTURE_STATES.IDLE;

    this._bindSimulatorEvents();
  }

  start() {
    this._connectWebSocket();
    this._startSimTicker();
  }

  setMode(mode) {
    this.mode = mode;
    if (mode === 'webcam') {
      this._startBrowserWebcam();
    } else {
      this._stopBrowserWebcam();
    }
  }

  _connectWebSocket() {
    if (this._ws && (this._ws.readyState === WebSocket.OPEN || this._ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const host = window.location.hostname || '127.0.0.1';
    const params = new URLSearchParams(window.location.search);
    const wsPort = params.get('ws') || '8765';
    const wsUrl = `ws://${host}:${wsPort}`;
    try {
      const ws = new WebSocket(wsUrl);
      this._ws = ws;

      ws.onmessage = (evt) => {
        if (this.mode === 'webcam' || this.mode === 'simulator') return;
        try {
          const data = JSON.parse(evt.data);
          this._udpLastPacketMs = performance.now();
          if (!this._udpConnected) {
            this._udpConnected = true;
            this.onConnectionChange({ udpConnected: true, source: 'udp' });
          }
          this.onTelemetry({
            state: data.state || GESTURE_STATES.IDLE,
            cursor: data.cursor || [0.5, 0.5, 0.0],
            pinch_dist: typeof data.pinch_dist === 'number' ? data.pinch_dist : 0.12,
            landmarks: Array.isArray(data.landmarks) ? data.landmarks : [],
            fps: data.fps || 60,
            source: 'udp',
          });
        } catch {
          // Ignore malformed datagram
        }
      };

      ws.onclose = () => {
        this._markUdpDisconnected();
        this._scheduleWsReconnect();
      };

      ws.onerror = () => {
        this._markUdpDisconnected();
      };
    } catch {
      this._scheduleWsReconnect();
    }
  }

  _scheduleWsReconnect() {
    if (this._wsRetryTimer) return;
    this._wsRetryTimer = setTimeout(() => {
      this._wsRetryTimer = null;
      this._connectWebSocket();
    }, 2000);
  }

  _markUdpDisconnected() {
    if (this._udpConnected) {
      this._udpConnected = false;
      this.onConnectionChange({ udpConnected: false, source: this.mode });
    }
  }

  _bindSimulatorEvents() {
    const updateCoords = (e) => {
      const nx = Math.max(0, Math.min(1, e.clientX / Math.max(1, window.innerWidth)));
      const ny = Math.max(0, Math.min(1, e.clientY / Math.max(1, window.innerHeight)));
      this._sim.nx = nx;
      this._sim.ny = ny;
      this._sim.clientX = e.clientX;
      this._sim.clientY = e.clientY;
      this._sim.activeInsideWindow = true;
    };

    window.addEventListener('pointermove', updateCoords);

    this.stageEl.addEventListener('pointerdown', (e) => {
      updateCoords(e);
      if (e.button === 0) this._sim.leftDown = true;
      if (e.button === 2) this._sim.rightDown = true;
    });

    window.addEventListener('pointerup', (e) => {
      if (e.button === 0) this._sim.leftDown = false;
      if (e.button === 2) this._sim.rightDown = false;
    });

    this.stageEl.addEventListener('contextmenu', (e) => {
      if (this._shouldRunSimulator()) e.preventDefault();
    });

    this.stageEl.addEventListener(
      'wheel',
      (e) => {
        if (!this._shouldRunSimulator()) return;
        if (this._sim.pinchKey) {
          e.preventDefault();
          this._sim.pinchDist = Math.max(0.008, Math.min(0.044, this._sim.pinchDist - e.deltaY * 0.00004));
        } else if (e.shiftKey) {
          e.preventDefault();
          this._sim.nz = Math.max(-1, Math.min(1, this._sim.nz - e.deltaY * 0.0015));
        }
      },
      { passive: false }
    );

    window.addEventListener('keydown', (e) => {
      if (e.key === 'e' || e.key === 'E') this._sim.eraseKey = true;
      if (e.key === 'p' || e.key === 'P' || e.key === 'Alt') this._sim.pinchKey = true;
    });

    window.addEventListener('keyup', (e) => {
      if (e.key === 'e' || e.key === 'E') this._sim.eraseKey = false;
      if (e.key === 'p' || e.key === 'P' || e.key === 'Alt') this._sim.pinchKey = false;
    });
  }

  _shouldRunSimulator() {
    return this.mode === 'simulator';
  }

  _startSimTicker() {
    let lastTick = performance.now();
    const tick = () => {
      const now = performance.now();
      const dt = Math.max(1, now - lastTick);
      lastTick = now;

      if (this._udpConnected && now - this._udpLastPacketMs > 1500) {
        this._markUdpDisconnected();
      }

      if (this._shouldRunSimulator() && this._sim.activeInsideWindow) {
        let state = GESTURE_STATES.HOVER;
        if (this._sim.rightDown) state = GESTURE_STATES.ORBIT_3D;
        else if (this._sim.pinchKey) state = GESTURE_STATES.PINCH_SCALE;
        else if (this._sim.eraseKey) state = GESTURE_STATES.ERASE;
        else if (this._sim.leftDown) state = GESTURE_STATES.DRAW;

        const pinchDist = state === GESTURE_STATES.PINCH_SCALE ? this._sim.pinchDist : 0.14;
        const landmarks = synthesizeHandLandmarks(
          this._sim.nx,
          this._sim.ny,
          this._sim.nz,
          state,
          pinchDist
        );

        this.onTelemetry({
          state,
          cursor: [this._sim.nx, this._sim.ny, this._sim.nz],
          pinch_dist: pinchDist,
          landmarks,
          fps: Math.min(120, Math.round(1000 / dt)),
          source: 'simulator',
          clientX: this._sim.clientX,
          clientY: this._sim.clientY,
        });
      }
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  async _startBrowserWebcam() {
    try {
      this._camStream = await navigator.mediaDevices.getUserMedia({
        video: { width: 640, height: 480, facingMode: 'user' },
      });
      this.videoEl.srcObject = this._camStream;
      this.videoEl.classList.remove('hidden');
      await this.videoEl.play();

      const vision = await import(
        'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.18/vision_bundle.mjs'
      );
      const fileset = await vision.FilesetResolver.forVisionTasks(
        'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.18/wasm'
      );
      this._handLandmarker = await vision.HandLandmarker.createFromOptions(fileset, {
        baseOptions: {
          modelAssetPath:
            'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',
        },
        runningMode: 'VIDEO',
        numHands: 1,
        minHandDetectionConfidence: 0.75,
        minHandPresenceConfidence: 0.75,
        minTrackingConfidence: 0.75,
      });

      this.onConnectionChange({ udpConnected: false, source: 'webcam' });
      let lastVideoTime = -1;
      let lastFrameMs = performance.now();

      const detectLoop = () => {
        if (this.mode !== 'webcam') return;
        const nowMs = performance.now();
        if (this.videoEl.readyState >= 2 && this.videoEl.currentTime !== lastVideoTime) {
          lastVideoTime = this.videoEl.currentTime;
          const result = this._handLandmarker.detectForVideo(this.videoEl, nowMs);
          const fps = Math.max(1, Math.round(1000 / Math.max(1, nowMs - lastFrameMs)));
          lastFrameMs = nowMs;

          let lm = null;
          if (result.landmarks && result.landmarks.length > 0) {
            // Mirror X for natural selfie interaction
            lm = result.landmarks[0].map((p) => [1 - p.x, p.y, p.z]);
          }

          const { state: rawState, pinchDist } = classifyLandmarksJS(lm);
          this._debounceBuf.push(rawState);
          if (this._debounceBuf.length > 5) this._debounceBuf.shift();
          if (this._debounceBuf.length === 5 && this._debounceBuf.every((s) => s === rawState)) {
            this._confirmedState = rawState;
          }

          let cursor = [0.5, 0.5, 0.0];
          if (lm) {
            const tSec = nowMs / 1000;
            const fx = Math.max(0, Math.min(1, this._xFilter.filter(lm[8][0], tSec)));
            const fy = Math.max(0, Math.min(1, this._yFilter.filter(lm[8][1], tSec)));
            const palm = dist2d(lm[0], lm[9]);
            const zRaw = Math.max(-1, Math.min(1, ((palm - 0.06) / 0.24) * 2 - 1));
            const fz = Math.max(-1, Math.min(1, this._zFilter.filter(zRaw, tSec)));
            cursor = [fx, fy, fz];
          } else {
            this._xFilter.reset();
            this._yFilter.reset();
            this._zFilter.reset();
          }

          this.onTelemetry({
            state: this._confirmedState,
            cursor,
            pinch_dist: pinchDist,
            landmarks: lm || [],
            fps,
            source: 'webcam',
          });
        }
        this._camLoopId = requestAnimationFrame(detectLoop);
      };
      this._camLoopId = requestAnimationFrame(detectLoop);
    } catch (err) {
      console.warn('[AERO-BRUSH] Browser webcam fallback unavailable:', err);
      this.mode = 'simulator';
      const selectEl = document.getElementById('input-source-select');
      if (selectEl) selectEl.value = 'simulator';
      this._stopBrowserWebcam();
    }
  }

  _stopBrowserWebcam() {
    if (this._camLoopId) {
      cancelAnimationFrame(this._camLoopId);
      this._camLoopId = null;
    }
    if (this._camStream) {
      for (const track of this._camStream.getTracks()) track.stop();
      this._camStream = null;
    }
    if (this._handLandmarker) {
      this._handLandmarker.close();
      this._handLandmarker = null;
    }
    this.videoEl.srcObject = null;
    this.videoEl.classList.add('hidden');
  }
}
