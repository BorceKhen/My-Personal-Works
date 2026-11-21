/**
 * High-DPI 2D Anti-Aliased Stroke Canvas Renderer.
 * Renders smooth quadratic / Catmull-Rom curves with subtle outer neon glow,
 * supports wide-radius erasing, undo history, clear, and .PNG export.
 */

export class Canvas2DRenderer {
  constructor(canvasEl) {
    this.canvas = canvasEl;
    this.ctx = canvasEl.getContext('2d');
    this.dpr = Math.max(1, window.devicePixelRatio || 1);

    this.strokes = []; // Completed strokes [{ points, color, radius, isEraser }]
    this.activeStroke = null;

    this.resize();
  }

  resize() {
    const rect = this.canvas.parentElement.getBoundingClientRect();
    this.dpr = Math.max(1, window.devicePixelRatio || 1);
    const w = Math.max(1, Math.floor(rect.width));
    const h = Math.max(1, Math.floor(rect.height));

    this.width = w;
    this.height = h;
    this.canvas.width = Math.floor(w * this.dpr);
    this.canvas.height = Math.floor(h * this.dpr);
    this.canvas.style.width = `${w}px`;
    this.canvas.style.height = `${h}px`;

    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    this.redraw();
  }

  beginStroke(x, y, { color, radius, isEraser = false }) {
    this.activeStroke = {
      points: [{ x, y }],
      color,
      radius,
      isEraser,
    };
    this.redraw();
  }

  appendPoint(x, y) {
    if (!this.activeStroke) return;
    const pts = this.activeStroke.points;
    const last = pts[pts.length - 1];
    const dist = Math.hypot(x - last.x, y - last.y);
    if (dist < 1.5) return;
    pts.push({ x, y });
    this.redraw();
  }

  endStroke() {
    if (!this.activeStroke) return;
    this.strokes.push(this.activeStroke);
    this.activeStroke = null;
    this.redraw();
  }

  undo() {
    if (this.activeStroke) {
      this.activeStroke = null;
      this.redraw();
      return true;
    }
    if (this.strokes.length > 0) {
      this.strokes.pop();
      this.redraw();
      return true;
    }
    return false;
  }

  clear() {
    this.strokes = [];
    this.activeStroke = null;
    this.ctx.clearRect(0, 0, this.width, this.height);
  }

  redraw() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.width, this.height);

    for (const stroke of this.strokes) {
      this._drawStroke(ctx, stroke);
    }
    if (this.activeStroke) {
      this._drawStroke(ctx, this.activeStroke);
    }
  }

  _drawStroke(ctx, stroke) {
    const pts = stroke.points;
    if (!pts || pts.length === 0) return;

    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (stroke.isEraser) {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.strokeStyle = 'rgba(0,0,0,1)';
      ctx.fillStyle = 'rgba(0,0,0,1)';
      ctx.lineWidth = stroke.radius * 2.6;
      ctx.shadowBlur = 0;
    } else {
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = stroke.color;
      ctx.fillStyle = stroke.color;
      ctx.lineWidth = stroke.radius;
      ctx.shadowColor = stroke.color;
      ctx.shadowBlur = Math.min(22, stroke.radius * 1.15);
    }

    if (pts.length === 1) {
      ctx.beginPath();
      ctx.arc(pts[0].x, pts[0].y, ctx.lineWidth * 0.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
      return;
    }

    // Smooth quadratic midpoint spline through all sampled points
    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < pts.length - 1; i += 1) {
      const midX = (pts[i].x + pts[i + 1].x) * 0.5;
      const midY = (pts[i].y + pts[i + 1].y) * 0.5;
      ctx.quadraticCurveTo(pts[i].x, pts[i].y, midX, midY);
    }
    const last = pts[pts.length - 1];
    ctx.lineTo(last.x, last.y);
    ctx.stroke();
    ctx.restore();
  }

  /**
   * Export artwork composited onto the dark #0B0E14 studio background as PNG data URL.
   */
  exportPNG() {
    const out = document.createElement('canvas');
    out.width = this.canvas.width;
    out.height = this.canvas.height;
    const octx = out.getContext('2d');
    octx.fillStyle = '#0B0E14';
    octx.fillRect(0, 0, out.width, out.height);
    octx.drawImage(this.canvas, 0, 0);
    return out.toDataURL('image/png');
  }
}
