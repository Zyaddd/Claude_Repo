import * as THREE from 'three';

// Procedural height maps: a fine knit/mesh weave for the jersey body and a
// rib texture for collars, cuffs and waistbands.

export function makeKnitTexture(size = 256) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const ctx = c.getContext('2d');
  const img = ctx.createImageData(size, size);
  const cells = 8; // knit loops per tile
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const u = (x / size) * cells, v = (y / size) * cells;
      // Interlocking "V" loops of a knitted jersey.
      const fu = u - Math.floor(u), fv = v - Math.floor(v);
      const row = Math.floor(v) % 2;
      const lu = (fu + row * 0.5) % 1;
      const vshape = Math.abs(lu - 0.5) * 2; // 0 at loop centre
      const loop = Math.sin(Math.PI * Math.min(1, Math.abs(fv - vshape * 0.5) * 2.2));
      // Small mesh holes (breathable panel look).
      const hu = (u * 2) % 1 - 0.5, hv = (v * 2 + (Math.floor(u * 2) % 2) * 0.5) % 1 - 0.5;
      const hole = Math.exp(-(hu * hu + hv * hv) / 0.03);
      const h = 0.55 + 0.35 * loop - 0.45 * hole + (Math.random() - 0.5) * 0.06;
      const g = Math.max(0, Math.min(255, Math.round(h * 255)));
      const o = (y * size + x) * 4;
      img.data[o] = img.data[o + 1] = img.data[o + 2] = g;
      img.data[o + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.NoColorSpace;
  tex.anisotropy = 4;
  return tex;
}

export function makeRibTexture() {
  const w = 64, h = 8;
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d');
  const img = ctx.createImageData(w, h);
  for (let x = 0; x < w; x++) {
    const v = 0.5 + 0.5 * Math.cos((x / w) * Math.PI * 2 * 4);
    const g = Math.round(v * 255);
    for (let y = 0; y < h; y++) {
      const o = (y * w + x) * 4;
      img.data[o] = img.data[o + 1] = img.data[o + 2] = g;
      img.data[o + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(0.25, 1);
  tex.colorSpace = THREE.NoColorSpace;
  return tex;
}
