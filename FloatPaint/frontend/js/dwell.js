/**
 * 0.8-second Air-Hover Dwell-Select Engine for .air-target controls.
 */

const CIRCUMFERENCE = 2 * Math.PI * 28; // matches r=28 in reticle SVG

export class DwellSelector {
  constructor({ dwellMs = 800, cooldownMs = 700, onTrigger } = {}) {
    this.dwellMs = dwellMs;
    this.cooldownMs = cooldownMs;
    this.onTrigger = onTrigger;

    this._hoveredEl = null;
    this._hoverStartMs = 0;
    this._lastTriggerMs = 0;
    this._arcEl = document.getElementById('reticle-dwell-arc');
  }

  /**
   * Update dwell progress given screen-space cursor (clientX, clientY) and gesture state.
   * Returns true if the cursor is currently hovering an interactive .air-target control.
   */
  update(clientX, clientY, gestureState, nowMs = performance.now()) {
    if (gestureState !== 'HOVER') {
      this.clear();
      return false;
    }

    const target = this._findAirTargetAt(clientX, clientY);
    if (!target) {
      this.clear();
      return false;
    }

    if (target !== this._hoveredEl) {
      this._setHoveredElement(target);
      this._hoverStartMs = nowMs;
    }

    if (nowMs - this._lastTriggerMs < this.cooldownMs) {
      this._setProgress(0);
      return true;
    }

    const elapsed = nowMs - this._hoverStartMs;
    const progress = Math.max(0, Math.min(1, elapsed / this.dwellMs));
    this._setProgress(progress);

    if (progress >= 1) {
      this._lastTriggerMs = nowMs;
      this._hoverStartMs = nowMs;
      this._setProgress(0);
      if (this.onTrigger) {
        this.onTrigger(target);
      } else {
        target.click();
      }
    }

    return true;
  }

  clear() {
    if (this._hoveredEl) {
      this._hoveredEl.classList.remove('is-air-hovered');
      this._hoveredEl = null;
    }
    this._hoverStartMs = 0;
    this._setProgress(0);
  }

  _setHoveredElement(el) {
    if (this._hoveredEl && this._hoveredEl !== el) {
      this._hoveredEl.classList.remove('is-air-hovered');
    }
    this._hoveredEl = el;
    if (el) {
      el.classList.add('is-air-hovered');
    }
  }

  _findAirTargetAt(clientX, clientY) {
    const el = document.elementFromPoint(clientX, clientY);
    if (!el) return null;
    return el.closest('.air-target');
  }

  _setProgress(progress) {
    if (this._hoveredEl) {
      this._hoveredEl.style.setProperty('--dwell-progress', `${Math.round(progress * 100)}%`);
    }
    if (!this._arcEl) return;
    const offset = CIRCUMFERENCE * (1 - progress);
    this._arcEl.style.strokeDashoffset = String(offset);
    this._arcEl.style.opacity = progress > 0.02 ? '1' : '0';
  }
}
