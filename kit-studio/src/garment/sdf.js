// Signed distance fields for the "ghost mannequin" shirt and shorts.
// Negative inside the body volume, positive outside. The garment surface is
// the zero level set; openings (collar, hem, cuffs, waist, legs) are cut later.

import { SHIRT, SHORTS, armFrame } from './layout.js';

const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

function smin(a, b, k) {
  const h = Math.max(k - Math.abs(a - b), 0) / k;
  return Math.min(a, b) - h * h * k * 0.25;
}
function smax(a, b, k) {
  return -smin(-a, -b, k);
}

// Round cone (capsule with linearly varying radius).
function cone(px, py, pz, ax, ay, az, bx, by, bz, ra, rb) {
  const bax = bx - ax, bay = by - ay, baz = bz - az;
  const pax = px - ax, pay = py - ay, paz = pz - az;
  let h = (pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz);
  h = clamp(h, 0, 1);
  const dx = pax - bax * h, dy = pay - bay * h, dz = paz - baz * h;
  return Math.sqrt(dx * dx + dy * dy + dz * dz) - (ra + (rb - ra) * h);
}

// Superellipse cross-section distance (approximate, conservative).
function sell(x, z, a, b, e) {
  const qx = Math.abs(x) / a, qz = Math.abs(z) / b;
  const n = Math.pow(Math.pow(qx, e) + Math.pow(qz, e), 1 / e);
  return (n - 1) * Math.min(a, b);
}

// Catmull-Rom interpolation through keyed profiles [y, ...values].
function profile(keys, y, out) {
  const n = keys.length;
  if (y <= keys[0][0]) { for (let i = 1; i < keys[0].length; i++) out[i - 1] = keys[0][i]; return out; }
  if (y >= keys[n - 1][0]) { for (let i = 1; i < keys[n - 1].length; i++) out[i - 1] = keys[n - 1][i]; return out; }
  let i = 0;
  while (keys[i + 1][0] < y) i++;
  const k0 = keys[Math.max(i - 1, 0)], k1 = keys[i], k2 = keys[i + 1], k3 = keys[Math.min(i + 2, n - 1)];
  const t = (y - k1[0]) / (k2[0] - k1[0]);
  const t2 = t * t, t3 = t2 * t;
  for (let j = 1; j < k1.length; j++) {
    const p0 = k0[j], p1 = k1[j], p2 = k2[j], p3 = k3[j];
    out[j - 1] = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
  }
  return out;
}

// ---- value noise for gentle fabric irregularity ----
function hash3(i, j, k) {
  let h = Math.imul(i, 374761393) + Math.imul(j, 668265263) + Math.imul(k, 1274126177);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h & 0xffff) / 0xffff;
}
function noise3(x, y, z) {
  const i = Math.floor(x), j = Math.floor(y), k = Math.floor(z);
  const fx = x - i, fy = y - j, fz = z - k;
  const ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy), uz = fz * fz * (3 - 2 * fz);
  const a = hash3(i, j, k), b = hash3(i + 1, j, k), c = hash3(i, j + 1, k), d = hash3(i + 1, j + 1, k);
  const e = hash3(i, j, k + 1), f = hash3(i + 1, j, k + 1), g = hash3(i, j + 1, k + 1), h = hash3(i + 1, j + 1, k + 1);
  const x1 = a + (b - a) * ux, x2 = c + (d - c) * ux, x3 = e + (f - e) * ux, x4 = g + (h - g) * ux;
  const y1 = x1 + (x2 - x1) * uy, y2 = x3 + (x4 - x3) * uy;
  return (y1 + (y2 - y1) * uz) * 2 - 1;
}

// =====================================================================
// SHIRT
// =====================================================================
// [y, halfWidth, halfDepth, zCentre]
const TORSO_KEYS = [
  [-0.6, 0.252, 0.142, 0.0],
  [-0.4, 0.254, 0.143, 0.0],
  [-0.22, 0.246, 0.139, 0.004],
  [-0.05, 0.251, 0.146, 0.01],
  [0.08, 0.258, 0.15, 0.01],
  [0.17, 0.25, 0.14, 0.004],
  [0.24, 0.212, 0.116, -0.006],
  [0.3, 0.15, 0.086, -0.01],
];

const ARM_R = armFrame(1);
const A = SHIRT.arm;
const ARM_END = A.cuff + 0.035;
const ARM_B = [ARM_R.S[0] + ARM_R.D[0] * ARM_END, ARM_R.S[1] + ARM_R.D[1] * ARM_END, ARM_R.S[2] + ARM_R.D[2] * ARM_END];

const _prof = [0, 0, 0];

// Returns body distance without the arms (torso + shoulders + neck).
function shirtTorso(x, y, z) {
  profile(TORSO_KEYS, y, _prof);
  const a = _prof[0], b = _prof[1], zc = _prof[2];
  let d = sell(x, z - zc, a, b, 2.4);
  d = smax(d, y - 0.255, 0.06);
  const ax = Math.abs(x);
  const sh = cone(ax, y, z, 0.0, 0.276, -0.012, 0.19, 0.226, -0.014, 0.064, 0.07);
  d = smin(d, sh, 0.06);
  const neck = cone(x, y, z, 0, 0.25, 0.006, 0, 0.36, 0.004, 0.04, 0.038);
  d = smin(d, neck, 0.04);
  return d;
}

function shirtArm(x, y, z) {
  const ax = Math.abs(x);
  return cone(ax, y, z, ARM_R.S[0], ARM_R.S[1], ARM_R.S[2], ARM_B[0], ARM_B[1], ARM_B[2], A.r0, A.r1);
}

function shirtWrinkles(x, y, z, dArm) {
  // Gentle low-frequency irregularity everywhere.
  let w = noise3(x * 9 + 3.1, y * 7, z * 9) * 0.0022;
  // Soft vertical drape near the hem.
  const hemW = clamp((-0.12 - y) / 0.25, 0, 1);
  if (hemW > 0) {
    const ang = Math.atan2(x, z);
    w += Math.sin(ang * 7 + noise3(x * 4, 1.7, z * 4) * 2.5) * 0.0028 * hemW * hemW;
  }
  // Drag lines from the armpits towards the chest/back.
  const ax = Math.abs(x);
  const pit = Math.exp(-((ax - 0.2) * (ax - 0.2) + (y - 0.1) * (y - 0.1)) / 0.012);
  if (pit > 0.02) w += Math.sin((y * 0.75 - ax * 0.65) * 70 + 1.3) * 0.0022 * pit;
  // Folds around the sleeve near the armpit.
  const near = clamp(1 - dArm / 0.03, 0, 1);
  if (near > 0) {
    const t = (ax - ARM_R.S[0]) * ARM_R.D[0] + (y - ARM_R.S[1]) * ARM_R.D[1] + (z - ARM_R.S[2]) * ARM_R.D[2];
    const sw = clamp(1 - Math.abs(t - 0.1) / 0.1, 0, 1);
    w += Math.sin(t * 95 + z * 30) * 0.0016 * sw * near * near;
  }
  return w;
}

export function shirtSDF(x, y, z) {
  const t = shirtTorso(x, y, z);
  const arm = shirtArm(x, y, z);
  const d = smin(t, arm, 0.04);
  return d + shirtWrinkles(x, y, z, arm);
}

// Parts used for sleeve weighting and clipping.
export function shirtParts(x, y, z) {
  return { torso: shirtTorso(x, y, z), arm: shirtArm(x, y, z) };
}

export function shirtSleeveT(x, y, z) {
  const ax = Math.abs(x);
  return (ax - ARM_R.S[0]) * ARM_R.D[0] + (y - ARM_R.S[1]) * ARM_R.D[1] + (z - ARM_R.S[2]) * ARM_R.D[2];
}

// Keep-functions (>= 0 keeps the surface).
export const shirtCuts = [
  // Hem
  (x, y) => y - SHIRT.hemY,
  // Collar opening
  (x, y, z) => {
    const n = SHIRT.neck;
    if (y < n.minY) return 1;
    return Math.hypot(x / n.rx, (z - n.zc) / n.rz) - 1;
  },
  // Cuffs (only where the surface belongs to the sleeve)
  (x, y, z) => {
    const p = shirtParts(x, y, z);
    return Math.max(A.cuff - shirtSleeveT(x, y, z), p.arm - p.torso);
  },
];

// Sleeve blend weight: 0 = body panel, 1 = sleeve panel, with a set-in seam.
export function shirtSleeveWeight(x, y, z) {
  const ax = Math.abs(x);
  const p = shirtParts(x, y, z);
  // Armhole seam plane, tilted from the shoulder point down to the armpit.
  const nx = 0.94, ny = -0.34;
  const plane = (ax - 0.205) * nx + (y - 0.2) * ny;
  const sp = clamp(plane / 0.003 + 0.5, 0, 1);
  const owner = clamp((p.torso - p.arm) / 0.003 + 0.5, 0, 1);
  return sp * owner;
}

// =====================================================================
// SHORTS
// =====================================================================
const HIP_KEYS = [
  [-0.2, 0.2, 0.13, 0.0],
  [-0.3, 0.203, 0.132, 0.0],
  [-0.42, 0.212, 0.138, -0.004],
  [-0.55, 0.216, 0.136, -0.006],
  [-0.66, 0.2, 0.118, -0.004],
];
const L = SHORTS.leg;

export function shortsSDF(x, y, z) {
  profile(HIP_KEYS, y, _prof);
  let d = sell(x, z - _prof[2], _prof[0], _prof[1], 2.3);
  d = smax(d, -(y + 0.6), 0.07);
  const ax = Math.abs(x);
  const leg = cone(ax, y, z, L.top[0], L.top[1], L.top[2], L.bottom[0], L.bottom[1], L.bottom[2], L.r0, L.r1);
  d = smin(d, leg, 0.06);
  let w = noise3(x * 8 + 11.0, y * 6, z * 8) * 0.0024;
  const legW = clamp((-0.62 - y) / 0.18, 0, 1);
  if (legW > 0) {
    const ang = Math.atan2(ax - 0.13, z);
    w += Math.sin(ang * 5 + y * 20) * 0.0022 * legW;
  }
  return d + w;
}

export const shortsCuts = [(x, y) => SHORTS.waistY - y, (x, y) => y - SHORTS.hemY];
