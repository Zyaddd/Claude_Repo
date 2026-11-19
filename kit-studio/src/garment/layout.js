// Shared garment layout: the physical shape parameters (metres) and the
// texture-atlas layout. Used by the geometry worker, the kit painter and the
// material shader, so a kit texture always lands on the right part of the 3D model.

export const ATLAS_SIZE = 2048;

// ---------- Shirt ----------
export const SHIRT = {
  hemY: -0.37,
  // Neck opening: vertical elliptic cylinder that punches the collar hole.
  neck: { rx: 0.083, rz: 0.084, zc: 0.02, minY: 0.12 },
  // Sleeves: shoulder pivot, direction (down and out), length to the cuff.
  arm: {
    pivot: [0.168, 0.212, -0.012],
    angleDeg: 47,
    forward: 0.06,
    cuff: 0.215,
    r0: 0.081,
    r1: 0.068,
  },
  // Region of the body the front / back artwork is projected onto.
  box: { xMin: -0.3, xMax: 0.3, yMin: -0.4, yMax: 0.36 },
};

export function armFrame(side /* +1 right (wearer's left, +x), -1 left */) {
  const a = (SHIRT.arm.angleDeg * Math.PI) / 180;
  const s = SHIRT.arm.pivot;
  let d = [side * Math.sin(a), -Math.cos(a), SHIRT.arm.forward];
  const dl = Math.hypot(d[0], d[1], d[2]);
  d = d.map((v) => v / dl);
  // e1: the outer/top side of the sleeve (where badges sit), perpendicular to d.
  const out = [side, 0, 0];
  const od = out[0] * d[0] + out[1] * d[1] + out[2] * d[2];
  let e1 = [out[0] - od * d[0], out[1] - od * d[1], out[2] - od * d[2]];
  const el = Math.hypot(...e1);
  e1 = e1.map((v) => v / el);
  // e2 = d x e1 (right), e1 x d (left) so artwork reads the same way on both sleeves.
  const cx = (u, v) => [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
  const e2 = side > 0 ? cx(d, e1) : cx(e1, d);
  return { S: [side * s[0], s[1], s[2]], D: d, E1: e1, E2: e2 };
}

// Sleeve artwork is unwrapped around the arm axis: u = angle, v = distance along the arm.
export const SLEEVE_MAP = { tStart: -0.03, tLen: 0.28 };

// Atlas regions in canvas pixels (top-left origin).
export const SHIRT_REGIONS = {
  front: { x: 0, y: 0, w: 1024, h: 1297 },
  back: { x: 1024, y: 0, w: 1024, h: 1297 },
  sleeveR: { x: 0, y: 1330, w: 1024, h: 560 },
  sleeveL: { x: 1024, y: 1330, w: 1024, h: 560 },
};

// Name & number decal covers the upper back.
export const SHIRT_DECAL = { xMin: -0.3, xMax: 0.3, yMin: -0.2, yMax: 0.3 };
// Printed label inside the back neck (visible through the collar).
export const SHIRT_LABEL = { xMin: -0.05, xMax: 0.05, yMin: 0.235, yMax: 0.3 };

// ---------- Shorts ----------
export const SHORTS = {
  waistY: -0.285,
  hemY: -0.8,
  leg: { top: [0.112, -0.53, 0], bottom: [0.155, -0.93, 0.008], r0: 0.112, r1: 0.12 },
  box: { xMin: -0.3, xMax: 0.3, yMin: -0.83, yMax: -0.25 },
};
export const SHORTS_ATLAS = { w: 2048, h: 1024 };
export const SHORTS_REGIONS = {
  front: { x: 0, y: 0, w: 1024, h: 990 },
  back: { x: 1024, y: 0, w: 1024, h: 990 },
};
// Number on the wearer's left leg (front, +x).
export const SHORTS_DECAL = { xMin: 0.02, xMax: 0.28, yMin: -0.79, yMax: -0.56 };

// Physical metres -> pixel helpers for painting.
export function regionPx(region, box, x, y, mirror = false) {
  const w = box.xMax - box.xMin;
  const h = box.yMax - box.yMin;
  const s = mirror ? (box.xMax - x) / w : (x - box.xMin) / w;
  const t = (box.yMax - y) / h;
  return [region.x + s * region.w, region.y + t * region.h];
}
export function pxPerMetre(region, box) {
  return region.w / (box.xMax - box.xMin);
}

// Region -> shader uniform (u0, v0, du, dv) for a texture with flipY.
export function regionUniform(region, W, H) {
  return [region.x / W, 1 - region.y / H, region.w / W, -region.h / H];
}
