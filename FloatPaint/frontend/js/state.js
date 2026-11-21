/**
 * Shared studio state, color palette, and gesture helpers.
 */

export const PALETTE_SWATCHES = [
  { name: 'Electric Cyan', hex: '#00F0FF' },
  { name: 'Neon Magenta', hex: '#FF2E93' },
  { name: 'Solar Amber', hex: '#FFB020' },
  { name: 'Emerald Pulse', hex: '#2EF2A0' },
  { name: 'Ultraviolet', hex: '#9D6CFF' },
  { name: 'Glacier White', hex: '#F0F6FC' },
];

export const GESTURE_STATES = Object.freeze({
  IDLE: 'IDLE',
  HOVER: 'HOVER',
  DRAW: 'DRAW',
  PINCH_SCALE: 'PINCH_SCALE',
  ERASE: 'ERASE',
  ORBIT_3D: 'ORBIT_3D',
});

export const STATE_ACCENTS = {
  IDLE: '#94A3B8',
  HOVER: '#00F0FF',
  DRAW: '#FF2E93',
  PINCH_SCALE: '#FFB020',
  ERASE: '#FF6B35',
  ORBIT_3D: '#9D6CFF',
};

export function createStudioState() {
  return {
    mode: '2d', // '2d' | '3d'
    brushColor: '#00F0FF',
    brushRadius: 10, // 2..30 px
    zLockEnabled: false,
    zLockPlane: 0.0, // -1.0 .. +1.0
    inputSource: 'auto', // 'auto' | 'webcam' | 'simulator'
    udpConnected: false,
    udpLastPacketMs: 0,
    telemetry: {
      state: GESTURE_STATES.IDLE,
      cursor: [0.5, 0.5, 0.0],
      pinch_dist: 0.12,
      landmarks: [],
      fps: 60,
      source: 'simulator',
    },
  };
}

/**
 * Map normalized pinch distance (< 0.045) smoothly onto brush radius [2, 30].
 */
export function pinchDistToRadius(pinchDist) {
  const minD = 0.008;
  const maxD = 0.045;
  const t = Math.max(0, Math.min(1, (pinchDist - minD) / (maxD - minD)));
  return Math.round(2 + t * 28);
}
