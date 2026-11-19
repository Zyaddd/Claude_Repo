import * as THREE from 'three';

// Loads public/garments.bin (see scripts/build-garments.mjs) into BufferGeometries.
export async function loadGarments(url, onProgress) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Could not load ${url}`);
  const total = Number(res.headers.get('content-length')) || 0;
  let buf;
  if (url.endsWith('.b64.txt')) {
    // Hosts that only serve text get the mesh as base64.
    const bin = atob((await res.text()).trim());
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    buf = bytes.buffer;
    onProgress?.(1);
  } else if (res.body && total && onProgress) {
    const reader = res.body.getReader();
    const parts = [];
    let got = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      parts.push(value);
      got += value.byteLength;
      onProgress(got / total);
    }
    buf = new Uint8Array(got);
    let o = 0;
    for (const p of parts) { buf.set(p, o); o += p.byteLength; }
    buf = buf.buffer;
  } else {
    buf = await res.arrayBuffer();
  }
  const hlen = new DataView(buf).getUint32(0, true);
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buf, 4, hlen)));
  const base = 4 + hlen;
  const out = {};
  for (const [name, p] of Object.entries(header.parts)) {
    const n = p.count;
    const g = new THREE.BufferGeometry();
    const pos = new Int16Array(buf, base + p.position, n * 3);
    const posF = new Float32Array(n * 3);
    for (let i = 0; i < n * 3; i++) posF[i] = (pos[i] / 32767) * p.box;
    g.setAttribute('position', new THREE.BufferAttribute(posF, 3));
    const nrm = new Int8Array(buf, base + p.normal, n * 4);
    g.setAttribute('normal', new THREE.InterleavedBufferAttribute(new THREE.InterleavedBuffer(nrm, 4), 3, 0, true));
    const attrs = new Uint8Array(buf, base + p.attrs, n * 4);
    const ib = new THREE.InterleavedBuffer(attrs, 4);
    g.setAttribute('aSleeve', new THREE.InterleavedBufferAttribute(ib, 1, 0, true));
    g.setAttribute('aAO', new THREE.InterleavedBufferAttribute(ib, 2, 1, true));
    if (p.uv !== undefined) g.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(buf, base + p.uv, n * 2), 2));
    const index = p.indexType === 'u16' ? new Uint16Array(buf, base + p.index, p.indexCount) : new Uint32Array(buf, base + p.index, p.indexCount);
    g.setIndex(new THREE.BufferAttribute(index, 1));
    g.computeBoundingSphere();
    out[name] = g;
  }
  return out;
}
