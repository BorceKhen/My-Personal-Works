/**
 * Bottom-Right Picture-in-Picture 21-Keypoint Hand Skeleton Monitor.
 * Renders the articulated hand skeleton over a dark telemetry grid (and optional
 * webcam feed) while highlighting active trigger joints per gesture state.
 */

import { STATE_ACCENTS } from './state.js';

const HAND_CONNECTIONS = [
  [0, 1], [1, 2], [2, 3], [3, 4],
  [0, 5], [5, 6], [6, 7], [7, 8],
  [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16],
  [13, 17], [0, 17], [17, 18], [18, 19], [19, 20],
];

const TRIGGER_JOINTS = {
  IDLE: [8],
  HOVER: [8, 12],
  DRAW: [5, 6, 7, 8],
  PINCH_SCALE: [4, 8],
  ERASE: [4, 8, 12, 16, 20],
  ORBIT_3D: [0, 5, 9, 13, 17],
};

const JOINT_HINTS = {
  IDLE: 'Hand Ready / Transition',
  HOVER: 'Index (8) + Mid (12): Dwell',
  DRAW: 'Index Tip (8): Painting',
  PINCH_SCALE: 'Thumb (4) ↔ Index (8): Scale',
  ERASE: 'All 5 Tips: Wide Eraser',
  ORBIT_3D: 'Wrist (0) Fist: 3D Orbit',
};

export class PipHandMonitor {
  constructor(canvasEl) {
    this.canvas = canvasEl;
    this.ctx = canvasEl.getContext('2d');
    this.xyzEl = document.getElementById('pip-xyz');
    this.hintEl = document.getElementById('pip-joint-hint');
    this.pinchEl = document.getElementById('pip-pinch-readout');
    this._resize();
  }

  _resize() {
    const dpr = Math.max(1, window.devicePixelRatio || 1);
    const w = this.canvas.clientWidth || 264;
    const h = this.canvas.clientHeight || 158;
    this.width = w;
    this.height = h;
    this.canvas.width = Math.floor(w * dpr);
    this.canvas.height = Math.floor(h * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  render(telemetry) {
    const ctx = this.ctx;
    const w = this.width;
    const h = this.height;
    const state = telemetry.state || 'IDLE';
    const accent = STATE_ACCENTS[state] || '#00F0FF';
    const [cx, cy, cz] = telemetry.cursor || [0.5, 0.5, 0.0];
    const pinchDist = telemetry.pinch_dist ?? 0.12;

    if (this.xyzEl) {
      const zSign = cz >= 0 ? '+' : '';
      this.xyzEl.textContent = `${cx.toFixed(2)}, ${cy.toFixed(2)}, ${zSign}${cz.toFixed(2)}`;
    }
    if (this.hintEl) {
      this.hintEl.textContent = JOINT_HINTS[state] || 'Tracking';
    }
    if (this.pinchEl) {
      this.pinchEl.textContent = `Pinch: ${pinchDist.toFixed(3)}`;
    }

    ctx.clearRect(0, 0, w, h);

    // Subtle dark telemetry grid
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.045)';
    ctx.lineWidth = 1;
    for (let x = 22; x < w; x += 22) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }
    for (let y = 22; y < h; y += 22) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    const lms = telemetry.landmarks;
    if (!lms || lms.length < 21) {
      ctx.fillStyle = 'rgba(139, 155, 180, 0.65)';
      ctx.font = '11px monospace';
      ctx.textAlign = 'center';
      ctx.fillText('Awaiting Hand Skeleton…', w * 0.5, h * 0.52);
      return;
    }

    const pad = 12;
    const pts = lms.map((p) => [
      pad + p[0] * (w - pad * 2),
      pad + p[1] * (h - pad * 2),
    ]);

    // Skeleton bone connections
    ctx.strokeStyle = 'rgba(180, 200, 230, 0.45)';
    ctx.lineWidth = 1.6;
    for (const [a, b] of HAND_CONNECTIONS) {
      ctx.beginPath();
      ctx.moveTo(pts[a][0], pts[a][1]);
      ctx.lineTo(pts[b][0], pts[b][1]);
      ctx.stroke();
    }

    // Special pinch caliper vector when scaling brush
    if (state === 'PINCH_SCALE') {
      ctx.save();
      ctx.strokeStyle = accent;
      ctx.lineWidth = 2.6;
      ctx.shadowColor = accent;
      ctx.shadowBlur = 10;
      ctx.beginPath();
      ctx.moveTo(pts[4][0], pts[4][1]);
      ctx.lineTo(pts[8][0], pts[8][1]);
      ctx.stroke();
      ctx.restore();
    }

    const highlighted = new Set(TRIGGER_JOINTS[state] || [8]);

    for (let i = 0; i < pts.length; i += 1) {
      const [px, py] = pts[i];
      const isTrigger = highlighted.has(i);
      const isTip = i === 4 || i === 8 || i === 12 || i === 16 || i === 20;

      ctx.save();
      if (isTrigger) {
        ctx.fillStyle = accent;
        ctx.shadowColor = accent;
        ctx.shadowBlur = 12;
        ctx.beginPath();
        ctx.arc(px, py, isTip ? 5.2 : 3.8, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.fillStyle = isTip ? '#E2E8F0' : 'rgba(148, 163, 184, 0.85)';
        ctx.beginPath();
        ctx.arc(px, py, isTip ? 3.2 : 2.1, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    }
  }
}
