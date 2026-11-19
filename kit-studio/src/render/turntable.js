// Turntable controls: drag to spin the garment 360°, slight tilt, wheel/pinch to
// zoom, inertia, idle auto-rotate, and animated "views" (front, back, name…).

const TAU = Math.PI * 2;
const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

export const VIEWS = {
  front: { yaw: 0, pitch: 0.04, zoom: 1, y: -0.25 },
  back: { yaw: Math.PI, pitch: 0.04, zoom: 1, y: -0.25 },
  side: { yaw: -Math.PI / 2, pitch: 0.04, zoom: 1, y: -0.25 },
  name: { yaw: Math.PI, pitch: 0.02, zoom: 0.66, y: 0.03 },
  shirt: { yaw: 0, pitch: 0.03, zoom: 0.66, y: 0.02 },
  shorts: { yaw: 0.3, pitch: 0.1, zoom: 0.72, y: -0.55 },
  sleeve: { yaw: -1.15, pitch: 0.05, zoom: 0.55, y: 0.12 },
};

export class Turntable {
  constructor(el, target, camera, onChange) {
    this.el = el;
    this.target = target;
    this.camera = camera;
    this.onChange = onChange;
    this.yaw = 0.5;
    this.pitch = 0.04;
    this.zoom = 1;
    this.focusY = -0.25;
    this.vel = 0;
    this.aspect = 1;
    this.tween = null;
    this.autoRotate = true;
    this.lastInput = 0;
    this.idleResume = 30000;
    this.pointers = new Map();
    this.listeners = new Set();
    this._bind();
    this._apply();
  }

  on(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  _emit(type) { for (const fn of this.listeners) fn(type); }

  setAspect(a) { this.aspect = a; this._apply(); }

  // Screen space covered by overlays (title at the top, controls at the bottom),
  // so the garment is framed in the free area between them.
  setViewport(w, h, top, bottom) { this.vp = { w, h, top, bottom }; this._apply(); }

  _bind() {
    const el = this.el;
    el.style.touchAction = 'none';
    let lastX = 0, lastY = 0, lastT = 0, pinchDist = 0, moved = 0;
    el.addEventListener('pointerdown', (e) => {
      el.setPointerCapture(e.pointerId);
      this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      this._interrupt();
      lastX = e.clientX; lastY = e.clientY; lastT = performance.now(); moved = 0;
      if (this.pointers.size === 2) pinchDist = this._pinch();
      el.classList.add('dragging');
    });
    el.addEventListener('pointermove', (e) => {
      if (!this.pointers.has(e.pointerId)) return;
      this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (this.pointers.size === 2) {
        const d = this._pinch();
        if (pinchDist > 0) this.zoom = clamp(this.zoom * (pinchDist / d), 0.4, 1.15);
        pinchDist = d;
        this._apply();
        return;
      }
      const now = performance.now();
      const dx = e.clientX - lastX, dy = e.clientY - lastY;
      moved += Math.abs(dx) + Math.abs(dy);
      const w = el.clientWidth || 600;
      const dyaw = (dx / w) * Math.PI * 1.6;
      this.yaw += dyaw;
      this.pitch = clamp(this.pitch + (dy / (el.clientHeight || 600)) * 0.9, -0.35, 0.45);
      const dt = Math.max(now - lastT, 1);
      this.vel = (dyaw / dt) * 1000 * 0.6 + this.vel * 0.4;
      lastX = e.clientX; lastY = e.clientY; lastT = now;
      this._apply();
    });
    const up = (e) => {
      this.pointers.delete(e.pointerId);
      if (this.pointers.size < 2) pinchDist = 0;
      if (this.pointers.size === 0) {
        el.classList.remove('dragging');
        if (performance.now() - lastT > 80) this.vel = 0;
      }
      this.lastInput = performance.now();
    };
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    el.addEventListener('dblclick', () => this.applyView('front'));
    el.addEventListener(
      'wheel',
      (e) => {
        e.preventDefault();
        this._interrupt();
        this.zoom = clamp(this.zoom * Math.exp(e.deltaY * 0.0012), 0.4, 1.15);
        this._apply();
      },
      { passive: false },
    );
  }

  _pinch() {
    const [a, b] = [...this.pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y) || 1;
  }

  _interrupt() {
    this.tween = null;
    this.vel = 0;
    if (this.autoRotate) { this.autoRotate = false; this._emit('interact'); }
    this.lastInput = performance.now();
  }

  // Keep auto-rotate off (e.g. while the customer is typing).
  hold() { this.autoRotate = false; this.lastInput = performance.now() + 1e9; }
  release() { this.lastInput = performance.now(); }

  applyView(name, instant = false) {
    const v = VIEWS[name] || VIEWS.front;
    this.autoRotate = false;
    this.vel = 0;
    // shortest path around the circle
    let target = v.yaw;
    const cur = this.yaw;
    target = cur + ((((target - cur) % TAU) + TAU + Math.PI) % TAU) - Math.PI;
    if (instant) {
      this.tween = null;
      this.yaw = target; this.pitch = v.pitch; this.zoom = v.zoom; this.focusY = v.y;
      this._apply();
      return;
    }
    this.tween = {
      t0: performance.now(),
      dur: 900,
      from: { yaw: this.yaw, pitch: this.pitch, zoom: this.zoom, y: this.focusY },
      to: { yaw: target, pitch: v.pitch, zoom: v.zoom, y: v.y },
    };
    this.lastInput = performance.now();
    this.onChange();
  }

  saveState() { return { yaw: this.yaw, pitch: this.pitch, zoom: this.zoom, focusY: this.focusY, autoRotate: this.autoRotate, tween: this.tween }; }
  restoreState(s) { Object.assign(this, s); this._apply(); }

  update(now) {
    let active = false;
    if (this.tween) {
      const { t0, dur, from, to } = this.tween;
      const k = ease(clamp((now - t0) / dur, 0, 1));
      this.yaw = from.yaw + (to.yaw - from.yaw) * k;
      this.pitch = from.pitch + (to.pitch - from.pitch) * k;
      this.zoom = from.zoom + (to.zoom - from.zoom) * k;
      this.focusY = from.y + (to.y - from.y) * k;
      if (k >= 1) this.tween = null;
      active = true;
    } else if (Math.abs(this.vel) > 0.001 && this.pointers.size === 0) {
      this.yaw += this.vel / 60;
      this.vel *= 0.93;
      active = true;
    } else if (this.autoRotate) {
      this.yaw += 0.0045;
      active = true;
    } else if (this.pointers.size === 0 && now - this.lastInput > this.idleResume) {
      this.autoRotate = true;
      this.pitch += (0.04 - this.pitch) * 0.05;
    }
    if (this.pointers.size) active = true;
    if (active) this._apply();
    return active;
  }

  _apply() {
    this.target.rotation.set(this.pitch * 0.5, this.yaw, 0, 'YXZ');
    // Fit the full kit (≈1.35 m tall, ≈0.9 m wide) into the view.
    const fov = (this.camera.fov * Math.PI) / 180;
    const fitH = 0.75 / Math.tan(fov / 2);
    const fitW = 0.52 / (Math.tan(fov / 2) * this.aspect);
    let dist = Math.max(fitH, fitW) * this.zoom;
    const vp = this.vp;
    if (vp && vp.h > 0) {
      const free = Math.max(vp.h - vp.top - vp.bottom, vp.h * 0.5);
      const k = Math.min(1, Math.max(0, (this.zoom - 0.6) / 0.4)); // overlays matter for full-body views
      dist *= 1 + (vp.h / free - 1) * k;
      this.camera.setViewOffset(vp.w, vp.h, 0, ((vp.bottom - vp.top) / 2) * k, vp.w, vp.h);
    }
    const y = this.focusY;
    this.camera.position.set(0, y + this.pitch * dist * 0.5, dist);
    this.camera.lookAt(0, y, 0);
    this.onChange();
  }
}
