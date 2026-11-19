// Garment mesh builder: SDF -> surface-nets mesh -> projected onto the true
// surface -> openings clipped -> attributes (sleeve weight, AO) -> rim bands
// (hems, collar, cuffs) that give the fabric visible thickness.

import { SHIRT, SHORTS, armFrame } from './layout.js';
import { shirtSDF, shirtCuts, shirtParts, shirtSleeveWeight, shortsSDF, shortsCuts } from './sdf.js';

// ------------------------------------------------------------------
// Surface nets with a narrow band (only cells near the surface are evaluated)
// ------------------------------------------------------------------
function surfaceNets(f, min, max, h) {
  const nx = Math.ceil((max[0] - min[0]) / h) + 1;
  const ny = Math.ceil((max[1] - min[1]) / h) + 1;
  const nz = Math.ceil((max[2] - min[2]) / h) + 1;
  const sxy = nx * ny;
  const vals = new Float32Array(nx * ny * nz).fill(NaN);
  const B = 4;
  const band = B * h * 1.9;
  const cnx = Math.ceil((nx - 1) / B) + 1, cny = Math.ceil((ny - 1) / B) + 1, cnz = Math.ceil((nz - 1) / B) + 1;
  const coarse = new Float32Array(cnx * cny * cnz);
  for (let k = 0; k < cnz; k++)
    for (let j = 0; j < cny; j++)
      for (let i = 0; i < cnx; i++) {
        const fi = Math.min(i * B, nx - 1), fj = Math.min(j * B, ny - 1), fk = Math.min(k * B, nz - 1);
        const v = f(min[0] + fi * h, min[1] + fj * h, min[2] + fk * h);
        coarse[i + j * cnx + k * cnx * cny] = v;
        vals[fi + fj * nx + fk * sxy] = v;
      }
  const far = [];
  for (let k = 0; k < cnz - 1; k++)
    for (let j = 0; j < cny - 1; j++)
      for (let i = 0; i < cnx - 1; i++) {
        let mn = Infinity;
        for (let c = 0; c < 8; c++) {
          const v = coarse[i + (c & 1) + (j + ((c >> 1) & 1)) * cnx + (k + ((c >> 2) & 1)) * cnx * cny];
          if (Math.abs(v) < mn) mn = Math.abs(v);
        }
        const i0 = i * B, j0 = j * B, k0 = k * B;
        const i1 = Math.min(i0 + B, nx - 1), j1 = Math.min(j0 + B, ny - 1), k1 = Math.min(k0 + B, nz - 1);
        if (mn < band) {
          for (let kk = k0; kk <= k1; kk++)
            for (let jj = j0; jj <= j1; jj++)
              for (let ii = i0; ii <= i1; ii++) {
                const idx = ii + jj * nx + kk * sxy;
                if (Number.isNaN(vals[idx])) vals[idx] = f(min[0] + ii * h, min[1] + jj * h, min[2] + kk * h);
              }
        } else {
          far.push(i0, j0, k0, i1, j1, k1, Math.sign(coarse[i + j * cnx + k * cnx * cny]));
        }
      }
  for (let q = 0; q < far.length; q += 7) {
    const [i0, j0, k0, i1, j1, k1, s] = far.slice(q, q + 7);
    for (let kk = k0; kk <= k1; kk++)
      for (let jj = j0; jj <= j1; jj++)
        for (let ii = i0; ii <= i1; ii++) {
          const idx = ii + jj * nx + kk * sxy;
          if (Number.isNaN(vals[idx])) vals[idx] = s;
        }
  }

  // One vertex per cell that crosses the surface.
  const cnxC = nx - 1, cnyC = ny - 1, cnzC = nz - 1;
  const cellIdx = new Int32Array(cnxC * cnyC * cnzC).fill(-1);
  const pos = [];
  const corner = new Float32Array(8);
  const EDGES = [[0, 1], [2, 3], [4, 5], [6, 7], [0, 2], [1, 3], [4, 6], [5, 7], [0, 4], [1, 5], [2, 6], [3, 7]];
  for (let k = 0; k < cnzC; k++)
    for (let j = 0; j < cnyC; j++)
      for (let i = 0; i < cnxC; i++) {
        let mask = 0;
        for (let c = 0; c < 8; c++) {
          const v = vals[i + (c & 1) + (j + ((c >> 1) & 1)) * nx + (k + ((c >> 2) & 1)) * sxy];
          corner[c] = v;
          if (v < 0) mask |= 1 << c;
        }
        if (mask === 0 || mask === 255) continue;
        let ax = 0, ay = 0, az = 0, cnt = 0;
        for (const [a, b] of EDGES) {
          const va = corner[a], vb = corner[b];
          if (va < 0 === vb < 0) continue;
          const t = va / (va - vb);
          ax += (a & 1) + ((b & 1) - (a & 1)) * t;
          ay += ((a >> 1) & 1) + (((b >> 1) & 1) - ((a >> 1) & 1)) * t;
          az += ((a >> 2) & 1) + (((b >> 2) & 1) - ((a >> 2) & 1)) * t;
          cnt++;
        }
        cellIdx[i + j * cnxC + k * cnxC * cnyC] = pos.length / 3;
        pos.push(min[0] + (i + ax / cnt) * h, min[1] + (j + ay / cnt) * h, min[2] + (k + az / cnt) * h);
      }

  // Quads across every grid edge with a sign change.
  const idx = [];
  const cell = (i, j, k) => cellIdx[i + j * cnxC + k * cnxC * cnyC];
  const quad = (a, b, c, d, flip) => {
    if (a < 0 || b < 0 || c < 0 || d < 0) return;
    if (flip) idx.push(a, c, b, a, d, c);
    else idx.push(a, b, c, a, c, d);
  };
  for (let k = 1; k < cnzC; k++)
    for (let j = 1; j < cnyC; j++)
      for (let i = 0; i < cnxC; i++) {
        const v0 = vals[i + j * nx + k * sxy], v1 = vals[i + 1 + j * nx + k * sxy];
        if (v0 < 0 === v1 < 0) continue;
        quad(cell(i, j - 1, k - 1), cell(i, j, k - 1), cell(i, j, k), cell(i, j - 1, k), v0 < 0);
      }
  for (let k = 1; k < cnzC; k++)
    for (let j = 0; j < cnyC; j++)
      for (let i = 1; i < cnxC; i++) {
        const v0 = vals[i + j * nx + k * sxy], v1 = vals[i + (j + 1) * nx + k * sxy];
        if (v0 < 0 === v1 < 0) continue;
        quad(cell(i - 1, j, k - 1), cell(i - 1, j, k), cell(i, j, k), cell(i, j, k - 1), v0 < 0);
      }
  for (let k = 0; k < cnzC; k++)
    for (let j = 1; j < cnyC; j++)
      for (let i = 1; i < cnxC; i++) {
        const v0 = vals[i + j * nx + k * sxy], v1 = vals[i + j * nx + (k + 1) * sxy];
        if (v0 < 0 === v1 < 0) continue;
        quad(cell(i - 1, j - 1, k), cell(i, j - 1, k), cell(i, j, k), cell(i - 1, j, k), v0 < 0);
      }
  return { pos, idx };
}

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------
function gradient(f, x, y, z, out, e = 0.0007) {
  out[0] = f(x + e, y, z) - f(x - e, y, z);
  out[1] = f(x, y + e, z) - f(x, y - e, z);
  out[2] = f(x, y, z + e) - f(x, y, z - e);
  const l = Math.hypot(out[0], out[1], out[2]) || 1;
  out[0] /= l; out[1] /= l; out[2] /= l;
  return l / (2 * e);
}

function project(f, p, iters = 4) {
  const g = [0, 0, 0];
  for (let it = 0; it < iters; it++) {
    const d = f(p[0], p[1], p[2]);
    if (Math.abs(d) < 1e-6) break;
    const gl = gradient(f, p[0], p[1], p[2], g);
    const s = d / Math.max(gl, 0.3);
    p[0] -= g[0] * s; p[1] -= g[1] * s; p[2] -= g[2] * s;
  }
  return p;
}

// Clip a triangle mesh against keep(x,y,z) >= 0, splitting triangles exactly.
function clipMesh(pos, idx, keep, f) {
  const kv = new Map();
  const kOf = (i) => {
    let v = kv.get(i);
    if (v === undefined) { v = keep(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]); kv.set(i, v); }
    return v;
  };
  const edgeCache = new Map();
  const split = (a, b) => {
    const key = a < b ? a * 67108864 + b : b * 67108864 + a;
    let v = edgeCache.get(key);
    if (v !== undefined) return v;
    const ka = kOf(a), kb = kOf(b);
    const t = ka / (ka - kb);
    const p = [
      pos[a * 3] + (pos[b * 3] - pos[a * 3]) * t,
      pos[a * 3 + 1] + (pos[b * 3 + 1] - pos[a * 3 + 1]) * t,
      pos[a * 3 + 2] + (pos[b * 3 + 2] - pos[a * 3 + 2]) * t,
    ];
    project(f, p, 3);
    v = pos.length / 3;
    pos.push(p[0], p[1], p[2]);
    kv.set(v, 0);
    edgeCache.set(key, v);
    return v;
  };
  const out = [];
  for (let t = 0; t < idx.length; t += 3) {
    const tri = [idx[t], idx[t + 1], idx[t + 2]];
    const ks = tri.map(kOf);
    const inside = ks.map((k) => k >= 0);
    const n = inside.filter(Boolean).length;
    if (n === 3) { out.push(...tri); continue; }
    if (n === 0) continue;
    // Rotate so the odd vertex is first.
    let r = 0;
    if (n === 1) r = inside.indexOf(true);
    else r = inside.indexOf(false);
    const a = tri[r], b = tri[(r + 1) % 3], c = tri[(r + 2) % 3];
    const ab = split(a, b), ac = split(a, c);
    if (n === 1) out.push(a, ab, ac);
    else out.push(ab, b, c, ab, c, ac);
  }
  return out;
}

// Remove unreferenced vertices, compact arrays.
function compact(pos, idx) {
  const map = new Int32Array(pos.length / 3).fill(-1);
  const np = [];
  const ni = new Uint32Array(idx.length);
  for (let i = 0; i < idx.length; i++) {
    const v = idx[i];
    if (map[v] < 0) { map[v] = np.length / 3; np.push(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]); }
    ni[i] = map[v];
  }
  return { pos: new Float32Array(np), idx: ni };
}

function fixWinding(pos, nrm, idx) {
  for (let t = 0; t < idx.length; t += 3) {
    const a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
    const ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
    const vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
    const cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
    const nx = nrm[a] + nrm[b] + nrm[c], ny = nrm[a + 1] + nrm[b + 1] + nrm[c + 1], nz = nrm[a + 2] + nrm[b + 2] + nrm[c + 2];
    if (cx * nx + cy * ny + cz * nz < 0) { const tmp = idx[t + 1]; idx[t + 1] = idx[t + 2]; idx[t + 2] = tmp; }
  }
}

// Laplacian smoothing of a per-vertex scalar over the mesh.
function smoothAttribute(values, idx, iters) {
  const n = values.length;
  const sum = new Float32Array(n), cnt = new Float32Array(n);
  for (let it = 0; it < iters; it++) {
    sum.fill(0); cnt.fill(0);
    for (let t = 0; t < idx.length; t += 3) {
      const a = idx[t], b = idx[t + 1], c = idx[t + 2];
      sum[a] += values[b] + values[c]; cnt[a] += 2;
      sum[b] += values[a] + values[c]; cnt[b] += 2;
      sum[c] += values[a] + values[b]; cnt[c] += 2;
    }
    for (let i = 0; i < n; i++) if (cnt[i]) values[i] = values[i] * 0.4 + (sum[i] / cnt[i]) * 0.6;
  }
}

// Ambient occlusion from the SDF: sample along the normal (outside) and
// against it (inside the hollow), plus how close the point is to an opening.
function ambientOcclusion(fOcc, pos, nrm, openings) {
  const n = pos.length / 3;
  const ao = new Float32Array(n * 2);
  const open = openings.flat();
  for (let i = 0; i < n; i++) {
    const x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
    const nx = nrm[i * 3], ny = nrm[i * 3 + 1], nz = nrm[i * 3 + 2];
    let occ = 0, w = 1, wsum = 0;
    for (let s = 1; s <= 5; s++) {
      const hh = 0.014 * s;
      const d = fOcc(x + nx * hh, y + ny * hh, z + nz * hh);
      occ += Math.max(hh - d, 0) / hh * w;
      wsum += w;
      w *= 0.75;
    }
    ao[i * 2] = Math.min(1, Math.max(0.35, 1 - (occ / wsum) * 1.1));
    // Inside: distance to the nearest opening drives how much light gets in.
    let dmin = Infinity;
    for (let q = 0; q < open.length; q += 3) {
      const dx = open[q] - x, dy = open[q + 1] - y, dz = open[q + 2] - z;
      const d2 = dx * dx + dy * dy + dz * dz;
      if (d2 < dmin) dmin = d2;
    }
    dmin = Math.sqrt(dmin);
    ao[i * 2 + 1] = 0.16 + 0.62 * Math.exp(-dmin / 0.11);
  }
  return ao;
}

function smoothAO(ao, idx, iters) {
  const n = ao.length / 2;
  const o = new Float32Array(n), inn = new Float32Array(n);
  for (let i = 0; i < n; i++) { o[i] = ao[i * 2]; inn[i] = ao[i * 2 + 1]; }
  smoothAttribute(o, idx, iters);
  smoothAttribute(inn, idx, iters);
  for (let i = 0; i < n; i++) { ao[i * 2] = o[i]; ao[i * 2 + 1] = inn[i]; }
  return ao;
}

// Sample a closed loop where the surface crosses a plane, by marching rays
// outward from an interior centre.
function planarLoop(f, C, e1, e2, count) {
  const pts = [];
  for (let i = 0; i < count; i++) {
    const th = (i / count) * Math.PI * 2;
    const dx = e1[0] * Math.cos(th) + e2[0] * Math.sin(th);
    const dy = e1[1] * Math.cos(th) + e2[1] * Math.sin(th);
    const dz = e1[2] * Math.cos(th) + e2[2] * Math.sin(th);
    let r0 = 0, r1 = 0.005;
    while (f(C[0] + dx * r1, C[1] + dy * r1, C[2] + dz * r1) < 0 && r1 < 0.6) { r0 = r1; r1 += 0.005; }
    for (let b = 0; b < 24; b++) {
      const m = (r0 + r1) / 2;
      if (f(C[0] + dx * m, C[1] + dy * m, C[2] + dz * m) < 0) r0 = m; else r1 = m;
    }
    const r = (r0 + r1) / 2;
    pts.push([C[0] + dx * r, C[1] + dy * r, C[2] + dz * r]);
  }
  return pts;
}

function neckLoop(f, count) {
  const n = SHIRT.neck;
  const pts = [];
  for (let i = 0; i < count; i++) {
    const th = (i / count) * Math.PI * 2;
    const x = n.rx * Math.cos(th), z = n.zc + n.rz * Math.sin(th);
    let y1 = 0.5, y0 = 0.5;
    while (f(x, y0, z) > 0 && y0 > 0) { y1 = y0; y0 -= 0.002; }
    for (let b = 0; b < 24; b++) {
      const m = (y0 + y1) / 2;
      if (f(x, m, z) > 0) y1 = m; else y0 = m;
    }
    pts.push([x, (y0 + y1) / 2, z]);
  }
  return pts;
}

const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };

// Rounded-rectangle profile in (out, along) coordinates.
function roundedProfile(halfT, b0, b1, segs = 5) {
  const pts = [];
  const r = Math.min(halfT * 0.95, (b1 - b0) / 2);
  const corners = [
    [halfT - r, b1 - r, 0], // top-out
    [-halfT + r, b1 - r, Math.PI / 2], // top-in
    [-halfT + r, b0 + r, Math.PI], // bottom-in
    [halfT - r, b0 + r, (3 * Math.PI) / 2], // bottom-out
  ];
  for (const [cx, cy, a0] of corners) {
    for (let s = 0; s <= segs; s++) {
      const a = a0 + (s / segs) * (Math.PI / 2);
      pts.push({ p: [cx + Math.cos(a) * r, cy + Math.sin(a) * r], n: [Math.cos(a), Math.sin(a)] });
    }
  }
  return pts;
}

// Sweep a profile along a closed loop. frameFn(i) -> {P, N (out), U (along)}.
function sweep(loop, frameFn, profile, opts = {}) {
  const M = loop.length, K = profile.length;
  const pos = new Float32Array(M * K * 3), nrm = new Float32Array(M * K * 3), uv = new Float32Array(M * K * 2);
  let len = 0;
  const cum = [0];
  for (let i = 1; i <= M; i++) { len += Math.hypot(...sub(loop[i % M], loop[i - 1])); cum.push(len); }
  for (let i = 0; i < M; i++) {
    const { P, N, U } = frameFn(i);
    for (let k = 0; k < K; k++) {
      const pr = profile[k];
      const o = (i * K + k) * 3;
      const q = add(P, add(mul(N, pr.p[0]), mul(U, pr.p[1])));
      pos.set(q, o);
      nrm.set(norm(add(mul(N, pr.n[0]), mul(U, pr.n[1]))), o);
      uv[(i * K + k) * 2] = (cum[i] / (opts.ribPitch || 0.004));
      uv[(i * K + k) * 2 + 1] = k / K;
    }
  }
  const idx = [];
  for (let i = 0; i < M; i++)
    for (let k = 0; k < K; k++) {
      const a = i * K + k, b = ((i + 1) % M) * K + k, c = ((i + 1) % M) * K + ((k + 1) % K), d = i * K + ((k + 1) % K);
      idx.push(a, b, c, a, c, d);
    }
  const index = new Uint32Array(idx);
  fixWinding(pos, nrm, index);
  return { position: pos, normal: nrm, uv, index };
}

function loopTangent(loop, i) {
  const M = loop.length;
  return norm(sub(loop[(i + 1) % M], loop[(i - 1 + M) % M]));
}

function surfaceNormal(f, p) {
  const g = [0, 0, 0];
  gradient(f, p[0], p[1], p[2], g);
  return g;
}

// Planar rim (hem / cuff / leg opening): band folded back into the kept fabric.
function planarRim(f, loop, keepDir, halfT, height, extra = {}) {
  const prof = roundedProfile(halfT, -0.0015, height);
  return sweep(
    loop,
    (i) => {
      const P = loop[i];
      const T = loopTangent(loop, i);
      const N = surfaceNormal(f, P);
      let U = norm(cross(N, T));
      const kd = typeof keepDir === 'function' ? keepDir(P) : keepDir;
      if (dot(U, kd) < 0) U = mul(U, -1);
      return { P, N, U };
    },
    prof,
    extra,
  );
}

function withAttrs(geo, sleeve, ao) {
  const n = geo.position.length / 3;
  geo.sleeve = new Float32Array(n).fill(sleeve);
  const a = new Float32Array(n * 2);
  for (let i = 0; i < n; i++) { a[i * 2] = ao[0]; a[i * 2 + 1] = ao[1]; }
  geo.ao = a;
  return geo;
}

// ------------------------------------------------------------------
// Public builders
// ------------------------------------------------------------------
export function buildShirt(h = 0.0055) {
  const f = shirtSDF;
  const min = [-0.46, SHIRT.hemY - 0.02, -0.21];
  const max = [0.46, 0.44, 0.21];
  let { pos, idx } = surfaceNets(f, min, max, h);
  for (let i = 0; i < pos.length; i += 3) {
    const p = project(f, [pos[i], pos[i + 1], pos[i + 2]]);
    pos[i] = p[0]; pos[i + 1] = p[1]; pos[i + 2] = p[2];
  }
  for (const cut of shirtCuts) idx = clipMesh(pos, idx, cut, f);
  const m = compact(pos, idx);
  const n = m.pos.length / 3;
  const nrm = new Float32Array(n * 3);
  const sleeve = new Float32Array(n);
  const g = [0, 0, 0];
  for (let i = 0; i < n; i++) {
    const x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
    gradient(f, x, y, z, g);
    nrm[i * 3] = g[0]; nrm[i * 3 + 1] = g[1]; nrm[i * 3 + 2] = g[2];
    sleeve[i] = shirtSleeveWeight(x, y, z);
  }
  fixWinding(m.pos, nrm, m.idx);
  smoothAttribute(sleeve, m.idx, 1);

  // Openings
  const neck = neckLoop(f, 200);
  const hem = planarLoop(f, [0, SHIRT.hemY, 0.0], [1, 0, 0], [0, 0, 1], 220);
  const cuffs = [1, -1].map((side) => {
    const fr = armFrame(side);
    const C = add(fr.S, mul(fr.D, SHIRT.arm.cuff));
    const e2 = norm(cross(fr.D, fr.E1));
    return { loop: planarLoop(f, C, fr.E1, e2, 120), D: fr.D };
  });
  const sparse = (loop, step) => loop.filter((_, i) => i % step === 0);
  const ao = ambientOcclusion(f, m.pos, nrm, [
    sparse(neck, 4).flat(),
    sparse(hem, 4).flat(),
    sparse(cuffs[0].loop, 3).flat(),
    sparse(cuffs[1].loop, 3).flat(),
  ]);
  smoothAO(ao, m.idx, 6);

  // Rims
  const hemRim = withAttrs(planarRim(f, hem, [0, 1, 0], 0.0026, 0.022), 0, [0.97, 0.75]);
  const cuffRims = cuffs.map((c) => withAttrs(planarRim(f, c.loop, mul(c.D, -1), 0.0032, 0.024, { ribPitch: 0.0035 }), 1, [1, 0.8]));
  const nk = SHIRT.neck;
  const collarProf = roundedProfile(0.0036, -0.014, 0.017);
  const collar = sweep(
    neck,
    (i) => {
      const P = neck[i];
      const T = loopTangent(neck, i);
      const radial = norm([P[0], 0, P[2] - nk.zc]);
      let V = norm(sub([0, 1, 0], mul(radial, 0.22)));
      V = norm(sub(V, mul(T, dot(V, T))));
      let N = norm(cross(T, V));
      if (dot(N, radial) < 0) N = mul(N, -1);
      // Lift the collar slightly so it sits on top of the neckline.
      return { P: add(P, mul(N, 0.0005)), N, U: V };
    },
    collarProf,
    { ribPitch: 0.0035 },
  );
  withAttrs(collar, 0, [1, 0.85]);

  return {
    body: { position: m.pos, normal: nrm, sleeve, ao, index: m.idx },
    hem: hemRim,
    cuffs: cuffRims,
    collar,
  };
}

export function buildShorts(h = 0.0055) {
  const f = shortsSDF;
  const min = [-0.33, SHORTS.hemY - 0.02, -0.19];
  const max = [0.33, SHORTS.waistY + 0.02, 0.19];
  let { pos, idx } = surfaceNets(f, min, max, h);
  for (let i = 0; i < pos.length; i += 3) {
    const p = project(f, [pos[i], pos[i + 1], pos[i + 2]]);
    pos[i] = p[0]; pos[i + 1] = p[1]; pos[i + 2] = p[2];
  }
  for (const cut of shortsCuts) idx = clipMesh(pos, idx, cut, f);
  const m = compact(pos, idx);
  const n = m.pos.length / 3;
  const nrm = new Float32Array(n * 3);
  const g = [0, 0, 0];
  for (let i = 0; i < n; i++) {
    gradient(f, m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2], g);
    nrm.set(g, i * 3);
  }
  fixWinding(m.pos, nrm, m.idx);

  const waist = planarLoop(f, [0, SHORTS.waistY, 0], [1, 0, 0], [0, 0, 1], 200);
  const L = SHORTS.leg;
  const legs = [1, -1].map((s) => {
    const t = (SHORTS.hemY - L.top[1]) / (L.bottom[1] - L.top[1]);
    const C = [s * (L.top[0] + (L.bottom[0] - L.top[0]) * t), SHORTS.hemY, L.top[2] + (L.bottom[2] - L.top[2]) * t];
    return planarLoop(f, C, [1, 0, 0], [0, 0, 1], 140);
  });
  // Occlusion includes the shirt hanging over the waistband.
  const fOcc = (x, y, z) => Math.min(f(x, y, z), Math.max(shirtSDF(x, y, z), SHIRT.hemY - y));
  const sparse = (loop, step) => loop.filter((_, i) => i % step === 0);
  const ao = ambientOcclusion(fOcc, m.pos, nrm, [sparse(legs[0], 3).flat(), sparse(legs[1], 3).flat(), sparse(waist, 4).flat()]);
  smoothAO(ao, m.idx, 6);

  const waistRim = withAttrs(planarRim(f, waist, [0, -1, 0], 0.003, 0.04, { ribPitch: 0.005 }), 0, [0.6, 0.6]);
  const legRims = legs.map((lp) => withAttrs(planarRim(f, lp, [0, 1, 0], 0.0026, 0.02), 0, [0.97, 0.75]));
  return {
    body: { position: m.pos, normal: nrm, sleeve: new Float32Array(n), ao, index: m.idx },
    waist: waistRim,
    legs: legRims,
  };
}

export function buildAll(quality = 'high') {
  const h = quality === 'low' ? 0.0068 : 0.0055;
  return { shirt: buildShirt(h), shorts: buildShorts(h) };
}
