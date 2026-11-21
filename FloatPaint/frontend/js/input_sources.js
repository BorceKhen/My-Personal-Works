/**
 * Camera telemetry input sources:
 * 1. Python OpenCV + MediaPipe tracker via WebSocket (:8765) + /api/cameras
 * 2. Browser MediaPipe JS webcam fallback when running standalone without run.py
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

export class InputSourceManager {
  constructor({ stageEl, videoEl, onTelemetry, onConnectionChange }) {
    this.stageEl = stageEl;
    this.videoEl = videoEl;
    this.onTelemetry = onTelemetry;
    this.onConnectionChange = onConnectionChange;

    this.mode = 'python'; // 'python' | 'browser-cam' | 'none'
    this.activeCameraLabel = '';
    this._ws = null;
    this._wsRetryTimer = null;
    this._watchdogTimer = null;
    this._udpLastPacketMs = 0;
    this._udpConnected = false;

    // Browser MediaPipe JS fallback state (only used if bridge runs standalone)
    this._camStream = null;
    this._handLandmarker = null;
    this._camLoopId = null;
    this._xFilter = new OneEuroFilterJS();
    this._yFilter = new OneEuroFilterJS();
    this._zFilter = new OneEuroFilterJS(1.0, 0.4, 1.0);
    this._debounceBuf = [];
    this._confirmedState = GESTURE_STATES.IDLE;
  }

  start() {
    this._connectWebSocket();
    this._startWatchdog();
  }

  async listAvailableCameras() {
    try {
      const resp = await fetch('/api/cameras', { cache: 'no-store' });
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data.cameras)) {
          return {
            backend: 'python',
            activeValue:
              typeof data.activeIndex === 'number' ? `py:${data.activeIndex}` : '',
            cameras: data.cameras.map((cam) => ({
              value: `py:${cam.index}`,
              label: cam.name || `Camera ${cam.index}`,
            })),
          };
        }
      }
    } catch {
      // Fall through to browser mediaDevices enumeration
    }

    if (!navigator.mediaDevices?.enumerateDevices) {
      return { backend: 'browser', activeValue: '', cameras: [] };
    }

    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const videoInputs = devices.filter((d) => d.kind === 'videoinput');
      return {
        backend: 'browser',
        activeValue: videoInputs.length > 0 ? `js:${videoInputs[0].deviceId}` : '',
        cameras: videoInputs.map((d, idx) => ({
          value: `js:${d.deviceId}`,
          label: d.label || `Camera ${idx + 1}`,
        })),
      };
    } catch {
      return { backend: 'browser', activeValue: '', cameras: [] };
    }
  }

  async selectCamera(value, label = '') {
    this.activeCameraLabel = label;

    if (!value) {
      this.mode = 'none';
      this._stopBrowserWebcam();
      this.onConnectionChange({
        udpConnected: false,
        source: 'none',
        cameraLabel: '',
      });
      return;
    }

    if (value.startsWith('py:')) {
      this.mode = 'python';
      this._stopBrowserWebcam();
      const idx = Number(value.slice(3));
      this._markUdpDisconnected();
      try {
        await fetch(`/api/camera?index=${encodeURIComponent(idx)}`, {
          method: 'POST',
        });
      } catch (err) {
        console.warn('[AERO-BRUSH] Failed to switch Python camera:', err);
      }
      return;
    }

    if (value.startsWith('js:')) {
      this.mode = 'browser-cam';
      const deviceId = value.slice(3);
      await this._startBrowserWebcam(deviceId);
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
        if (this.mode !== 'python') return;
        try {
          const data = JSON.parse(evt.data);
          this._udpLastPacketMs = performance.now();
          if (!this._udpConnected) {
            this._udpConnected = true;
            this.onConnectionChange({
              udpConnected: true,
              source: 'python',
              cameraLabel: this.activeCameraLabel,
            });
          }
          this.onTelemetry({
            state: data.state || GESTURE_STATES.IDLE,
            cursor: data.cursor || [0.5, 0.5, 0.0],
            pinch_dist: typeof data.pinch_dist === 'number' ? data.pinch_dist : 0.12,
            landmarks: Array.isArray(data.landmarks) ? data.landmarks : [],
            fps: data.fps || 60,
            source: 'camera',
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
      this.onConnectionChange({
        udpConnected: false,
        source: this.mode,
        cameraLabel: this.activeCameraLabel,
      });
    }
  }

  _startWatchdog() {
    const check = () => {
      const now = performance.now();
      if (this._udpConnected && now - this._udpLastPacketMs > 1500) {
        this._markUdpDisconnected();
      }
      this._watchdogTimer = setTimeout(check, 500);
    };
    this._watchdogTimer = setTimeout(check, 500);
  }

  async _startBrowserWebcam(deviceId = '') {
    this._stopBrowserWebcam();
    try {
      const videoConstraint = deviceId
        ? { deviceId: { exact: deviceId }, width: 640, height: 480 }
        : { width: 640, height: 480, facingMode: 'user' };

      this._camStream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraint,
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

      this.onConnectionChange({
        udpConnected: false,
        source: 'browser-cam',
        cameraLabel: this.activeCameraLabel,
      });
      let lastVideoTime = -1;
      let lastFrameMs = performance.now();

      const detectLoop = () => {
        if (this.mode !== 'browser-cam') return;
        const nowMs = performance.now();
        if (this.videoEl.readyState >= 2 && this.videoEl.currentTime !== lastVideoTime) {
          lastVideoTime = this.videoEl.currentTime;
          const result = this._handLandmarker.detectForVideo(this.videoEl, nowMs);
          const fps = Math.max(1, Math.round(1000 / Math.max(1, nowMs - lastFrameMs)));
          lastFrameMs = nowMs;

          let lm = null;
          if (result.landmarks && result.landmarks.length > 0) {
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
            source: 'browser-cam',
          });
        }
        this._camLoopId = requestAnimationFrame(detectLoop);
      };
      this._camLoopId = requestAnimationFrame(detectLoop);
    } catch (err) {
      console.warn('[AERO-BRUSH] Browser webcam unavailable:', err);
      this._stopBrowserWebcam();
      this.onConnectionChange({
        udpConnected: false,
        source: 'none',
        cameraLabel: this.activeCameraLabel,
      });
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
