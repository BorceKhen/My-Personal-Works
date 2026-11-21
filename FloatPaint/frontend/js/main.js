/**
 * AERO-BRUSH Studio Main Controller.
 * Orchestrates top nav, floating air-hover tool dock, 0.8s dwell selection,
 * 2D/3D viewport rendering, PiP hand monitor, undo/clear/export, and telemetry.
 */

import { Canvas2DRenderer } from './canvas2d.js';
import { DwellSelector } from './dwell.js';
import { InputSourceManager } from './input_sources.js';
import { PipHandMonitor } from './pip_monitor.js';
import {
  createStudioState,
  GESTURE_STATES,
  PALETTE_SWATCHES,
  pinchDistToRadius,
} from './state.js';
import { Viewport3DRenderer } from './viewport3d.js';

const state = createStudioState();

// DOM references
const stageEl = document.querySelector('.viewport-stage');
const canvas2dEl = document.getElementById('canvas-2d');
const viewport3dEl = document.getElementById('viewport-3d');
const pipCanvasEl = document.getElementById('pip-skeleton-canvas');
const pipVideoEl = document.getElementById('pip-webcam-video');

const mode2dBtn = document.getElementById('btn-mode-2d');
const mode3dBtn = document.getElementById('btn-mode-3d');
const gesturePillEl = document.getElementById('gesture-pill');
const gesturePillLabelEl = document.getElementById('gesture-pill-label');
const telemFpsEl = document.getElementById('telem-fps');
const udpStatusEl = document.getElementById('udp-status');
const udpStatusTextEl = document.getElementById('udp-status-text');
const sourceSelectEl = document.getElementById('input-source-select');

const swatchGridEl = document.getElementById('swatch-grid');
const customColorInput = document.getElementById('custom-color-input');
const radiusSliderEl = document.getElementById('brush-radius-slider');
const radiusValueEl = document.getElementById('brush-radius-value');
const radiusDownBtn = document.getElementById('btn-radius-down');
const radiusUpBtn = document.getElementById('btn-radius-up');

const zLockBtn = document.getElementById('btn-z-lock');
const zLockLabelEl = document.getElementById('z-lock-label');
const zPlaneSliderEl = document.getElementById('z-plane-slider');
const zPlaneValueEl = document.getElementById('z-plane-value');

const undoBtn = document.getElementById('btn-undo');
const clearBtn = document.getElementById('btn-clear');
const exportPngBtn = document.getElementById('btn-export-png');
const exportObjBtn = document.getElementById('btn-export-obj');
const exportGltfBtn = document.getElementById('btn-export-gltf');

const reticleEl = document.getElementById('air-reticle');
const reticleBrushRingEl = document.getElementById('reticle-brush-ring');
const reticleTagEl = document.getElementById('reticle-tag');

// Subsystems
const canvas2d = new Canvas2DRenderer(canvas2dEl);
const viewport3d = new Viewport3DRenderer(viewport3dEl);
const pipMonitor = new PipHandMonitor(pipCanvasEl);
const dwellSelector = new DwellSelector({
  dwellMs: 800,
  onTrigger: (targetEl) => targetEl.click(),
});

let prevGesture = GESTURE_STATES.IDLE;

// ---------- Palette Initialization ----------
function initPalette() {
  swatchGridEl.innerHTML = '';
  PALETTE_SWATCHES.forEach((swatch, index) => {
    const btn = document.createElement('button');
    btn.className = `color-swatch air-target${index === 0 ? ' is-active' : ''}`;
    btn.style.backgroundColor = swatch.hex;
    btn.style.setProperty('--swatch-color', swatch.hex);
    btn.title = swatch.name;
    btn.dataset.color = swatch.hex;
    btn.addEventListener('click', () => setBrushColor(swatch.hex, btn));
    swatchGridEl.appendChild(btn);
  });
}

function setBrushColor(hex, activeBtn = null) {
  state.brushColor = hex;
  customColorInput.value = hex;
  document.querySelectorAll('.color-swatch').forEach((el) => {
    el.classList.toggle('is-active', el === activeBtn || el.dataset.color === hex);
  });
}

function setBrushRadius(radius) {
  const clamped = Math.max(2, Math.min(30, Math.round(radius)));
  state.brushRadius = clamped;
  radiusSliderEl.value = String(clamped);
  radiusValueEl.textContent = `${clamped}px`;
}

function setMode(nextMode) {
  if (state.mode === nextMode) return;
  canvas2d.endStroke();
  viewport3d.endStroke();
  viewport3d.resetOrbitTracking();

  state.mode = nextMode;
  const is3d = nextMode === '3d';

  mode2dBtn.classList.toggle('is-active', !is3d);
  mode2dBtn.setAttribute('aria-selected', String(!is3d));
  mode3dBtn.classList.toggle('is-active', is3d);
  mode3dBtn.setAttribute('aria-selected', String(is3d));

  canvas2dEl.classList.toggle('hidden', is3d);
  viewport3dEl.classList.toggle('hidden', !is3d);

  document.querySelectorAll('.only-2d').forEach((el) => el.classList.toggle('hidden', is3d));
  document.querySelectorAll('.only-3d').forEach((el) => el.classList.toggle('hidden', !is3d));

  if (is3d) {
    viewport3d.resize();
  } else {
    canvas2d.resize();
  }
}

async function triggerDownload(filename, contentOrDataUrl, mime = 'text/plain') {
  const isDataUrl = contentOrDataUrl.startsWith('data:');
  if (window.pywebview?.api?.save_file) {
    try {
      await window.pywebview.api.save_file(filename, contentOrDataUrl, isDataUrl);
      return;
    } catch (err) {
      console.warn('Desktop save dialog failed, falling back to browser download:', err);
    }
  }

  const link = document.createElement('a');
  if (isDataUrl) {
    link.href = contentOrDataUrl;
  } else {
    const blob = new Blob([contentOrDataUrl], { type: mime });
    link.href = URL.createObjectURL(blob);
  }
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

// ---------- Wire Controls ----------
initPalette();
setBrushRadius(state.brushRadius);

customColorInput.addEventListener('input', (e) => setBrushColor(e.target.value));
radiusSliderEl.addEventListener('input', (e) => setBrushRadius(Number(e.target.value)));
radiusDownBtn.addEventListener('click', () => setBrushRadius(state.brushRadius - 2));
radiusUpBtn.addEventListener('click', () => setBrushRadius(state.brushRadius + 2));

mode2dBtn.addEventListener('click', () => setMode('2d'));
mode3dBtn.addEventListener('click', () => setMode('3d'));

zLockBtn.addEventListener('click', () => {
  state.zLockEnabled = !state.zLockEnabled;
  zLockBtn.classList.toggle('is-active', state.zLockEnabled);
  zLockLabelEl.textContent = `Z-Lock: ${state.zLockEnabled ? 'ON' : 'OFF'}`;
});

zPlaneSliderEl.addEventListener('input', (e) => {
  state.zLockPlane = Number(e.target.value);
  zPlaneValueEl.textContent = state.zLockPlane.toFixed(2);
});

undoBtn.addEventListener('click', () => {
  if (state.mode === '2d') canvas2d.undo();
  else viewport3d.undo();
});

clearBtn.addEventListener('click', () => {
  if (state.mode === '2d') canvas2d.clear();
  else viewport3d.clear();
});

exportPngBtn.addEventListener('click', () => {
  triggerDownload(`aero-brush-2d-${Date.now()}.png`, canvas2d.exportPNG());
});

exportObjBtn.addEventListener('click', () => {
  const objText = viewport3d.exportOBJ();
  triggerDownload(`aero-brush-3d-${Date.now()}.obj`, objText, 'text/plain');
});

exportGltfBtn.addEventListener('click', async () => {
  const gltfText = await viewport3d.exportGLTF();
  triggerDownload(`aero-brush-3d-${Date.now()}.gltf`, gltfText, 'model/gltf+json');
});

window.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
    e.preventDefault();
    if (state.mode === '2d') canvas2d.undo();
    else viewport3d.undo();
  }
});

window.addEventListener('resize', () => {
  canvas2d.resize();
  viewport3d.resize();
});

// ---------- Telemetry Handling ----------
function updateConnectionUI({ udpConnected, source }) {
  state.udpConnected = udpConnected;
  udpStatusEl.classList.remove('is-live', 'is-sim', 'is-disconnected');

  if (udpConnected) {
    udpStatusEl.classList.add('is-live');
    udpStatusTextEl.textContent = 'UDP :5055 — CAMERA LIVE';
  } else if (source === 'webcam') {
    udpStatusEl.classList.add('is-live');
    udpStatusTextEl.textContent = 'BROWSER CAM — CV LIVE';
  } else if (source === 'simulator') {
    udpStatusEl.classList.add('is-sim');
    udpStatusTextEl.textContent = 'MOUSE SIMULATOR';
  } else {
    udpStatusEl.classList.add('is-disconnected');
    udpStatusTextEl.textContent = 'UDP :5055 — WAITING FOR TRACKER';
  }
}

function handleTelemetry(packet) {
  state.telemetry = packet;
  const gState = packet.state || GESTURE_STATES.IDLE;
  const [nx, ny, rawNz] = packet.cursor || [0.5, 0.5, 0.0];
  const nz = state.zLockEnabled ? state.zLockPlane : rawNz;

  // Update top nav HUD
  telemFpsEl.textContent = String(packet.fps ?? 60);
  gesturePillLabelEl.textContent = gState;
  gesturePillEl.className = `gesture-pill state-${gState}`;

  if (!state.zLockEnabled) {
    zPlaneSliderEl.value = nz.toFixed(2);
    zPlaneValueEl.textContent = nz.toFixed(2);
  }

  // Compute full-studio screen cursor and stage-relative canvas/3D coordinates
  const screenX = packet.clientX ?? nx * window.innerWidth;
  const screenY = packet.clientY ?? ny * window.innerHeight;
  const rect = stageEl.getBoundingClientRect();
  const stageX = screenX - rect.left;
  const stageY = screenY - rect.top;
  const stageNx = Math.max(0, Math.min(1, stageX / Math.max(1, rect.width)));
  const stageNy = Math.max(0, Math.min(1, stageY / Math.max(1, rect.height)));

  // Dynamic PINCH_SCALE radius control
  if (gState === GESTURE_STATES.PINCH_SCALE && typeof packet.pinch_dist === 'number') {
    setBrushRadius(pinchDistToRadius(packet.pinch_dist));
  }

  // Update floating reticle
  reticleEl.style.left = `${screenX}px`;
  reticleEl.style.top = `${screenY}px`;
  reticleEl.className = `air-reticle state-${gState}`;
  reticleTagEl.textContent = gState;
  const ringDia = gState === GESTURE_STATES.ERASE ? state.brushRadius * 2.6 : state.brushRadius;
  reticleBrushRingEl.style.width = `${Math.max(10, ringDia)}px`;
  reticleBrushRingEl.style.height = `${Math.max(10, ringDia)}px`;

  // 0.8s Air-Hover Dwell Selection over dock/nav buttons
  const hoveringUi = dwellSelector.update(screenX, screenY, gState);

  // Route drawing / erasing / 3D orbit when not hovering UI controls
  if (state.mode === '2d') {
    const insideStage = stageX >= 0 && stageY >= 0 && stageX <= rect.width && stageY <= rect.height;
    const isPainting = !hoveringUi && insideStage && (gState === GESTURE_STATES.DRAW || gState === GESTURE_STATES.ERASE);
    const isEraser = gState === GESTURE_STATES.ERASE;
    const wasPainting = prevGesture === GESTURE_STATES.DRAW || prevGesture === GESTURE_STATES.ERASE;

    if (isPainting) {
      if (!wasPainting || gState !== prevGesture || !canvas2d.activeStroke) {
        canvas2d.endStroke();
        canvas2d.beginStroke(stageX, stageY, {
          color: state.brushColor,
          radius: state.brushRadius,
          isEraser,
        });
      } else {
        canvas2d.appendPoint(stageX, stageY);
      }
    } else if (wasPainting) {
      canvas2d.endStroke();
    }
  } else {
    const worldPos = viewport3d.updateCursor(stageNx, stageNy, nz, {
      state: gState,
      brushRadius: state.brushRadius,
      brushColor: state.brushColor,
    });

    if (!hoveringUi && gState === GESTURE_STATES.DRAW) {
      if (prevGesture !== GESTURE_STATES.DRAW || !viewport3d.activeStroke) {
        viewport3d.beginStroke(worldPos, {
          color: state.brushColor,
          radius: state.brushRadius,
        });
      } else {
        viewport3d.appendPoint(worldPos);
      }
    } else if (prevGesture === GESTURE_STATES.DRAW) {
      viewport3d.endStroke();
    }

    if (!hoveringUi && gState === GESTURE_STATES.ERASE) {
      viewport3d.eraseAt(worldPos, state.brushRadius);
    }

    if (gState === GESTURE_STATES.ORBIT_3D) {
      const wrist = packet.landmarks && packet.landmarks[0] ? packet.landmarks[0] : [nx, ny, nz];
      viewport3d.orbitByWrist(wrist);
    } else if (prevGesture === GESTURE_STATES.ORBIT_3D) {
      viewport3d.resetOrbitTracking();
    }
  }

  prevGesture = gState;
  pipMonitor.render(packet);
}

// ---------- Input Source Manager & Render Loop ----------
const inputManager = new InputSourceManager({
  stageEl,
  videoEl: pipVideoEl,
  onTelemetry: handleTelemetry,
  onConnectionChange: updateConnectionUI,
});

sourceSelectEl.addEventListener('change', (e) => {
  state.inputSource = e.target.value;
  inputManager.setMode(state.inputSource);
  updateConnectionUI({ udpConnected: state.udpConnected, source: state.inputSource });
});

inputManager.start();
updateConnectionUI({ udpConnected: false, source: state.inputSource });

function renderLoop() {
  if (state.mode === '3d') {
    viewport3d.render();
  }
  requestAnimationFrame(renderLoop);
}
requestAnimationFrame(renderLoop);
