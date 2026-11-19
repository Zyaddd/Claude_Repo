// Generates the garment meshes once and writes them as a compact binary
// (public/garments.bin) so browsers download a ready mesh instead of
// spending seconds generating it on a phone.
//
//   npm run garments
//
// Format: [u32 headerLength][JSON header][padding to 4][buffers...]
// The header lists each mesh part and the byte offset / type of each attribute.

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildAll } from '../src/garment/build.js';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const t0 = performance.now();
const all = buildAll('high');
console.log(`generated in ${Math.round(performance.now() - t0)} ms`);

const chunks = [];
let offset = 0;
const header = { version: 1, parts: {} };

function push(typed) {
  const pad = (4 - (offset % 4)) % 4;
  if (pad) { chunks.push(new Uint8Array(pad)); offset += pad; }
  const bytes = new Uint8Array(typed.buffer, typed.byteOffset, typed.byteLength);
  chunks.push(bytes);
  const at = offset;
  offset += bytes.byteLength;
  return at;
}

// Positions quantised to int16 over a fixed box, normals to int8, weights to uint8.
const BOX = 1.0; // metres, symmetric (-1..1)
function addPart(name, g) {
  const n = g.position.length / 3;
  const pos = new Int16Array(n * 3);
  for (let i = 0; i < n * 3; i++) pos[i] = Math.round((g.position[i] / BOX) * 32767);
  const nrm = new Int8Array(n * 4);
  for (let i = 0; i < n; i++) for (let c = 0; c < 3; c++) nrm[i * 4 + c] = Math.round(g.normal[i * 3 + c] * 127);
  const attrs = new Uint8Array(n * 4); // sleeve, aoOut, aoIn, pad
  for (let i = 0; i < n; i++) {
    attrs[i * 4] = Math.round((g.sleeve ? g.sleeve[i] : 0) * 255);
    attrs[i * 4 + 1] = Math.round((g.ao ? g.ao[i * 2] : 1) * 255);
    attrs[i * 4 + 2] = Math.round((g.ao ? g.ao[i * 2 + 1] : 1) * 255);
  }
  const part = { count: n, box: BOX };
  part.position = push(pos);
  part.normal = push(nrm);
  part.attrs = push(attrs);
  if (g.uv) part.uv = push(new Float32Array(g.uv));
  const index = n < 65536 ? new Uint16Array(g.index) : new Uint32Array(g.index);
  part.index = push(index);
  part.indexType = n < 65536 ? 'u16' : 'u32';
  part.indexCount = g.index.length;
  header.parts[name] = part;
}

addPart('shirt.body', all.shirt.body);
addPart('shirt.hem', all.shirt.hem);
addPart('shirt.collar', all.shirt.collar);
addPart('shirt.cuffR', all.shirt.cuffs[0]);
addPart('shirt.cuffL', all.shirt.cuffs[1]);
addPart('shorts.body', all.shorts.body);
addPart('shorts.waist', all.shorts.waist);
addPart('shorts.legR', all.shorts.legs[0]);
addPart('shorts.legL', all.shorts.legs[1]);

const hjson = new TextEncoder().encode(JSON.stringify(header));
const hpad = (4 - ((4 + hjson.length) % 4)) % 4;
const total = 4 + hjson.length + hpad + offset;
const out = new Uint8Array(total);
new DataView(out.buffer).setUint32(0, hjson.length + hpad, true);
out.set(hjson, 4);
out.fill(32, 4 + hjson.length, 4 + hjson.length + hpad);
let o = 4 + hjson.length + hpad;
for (const c of chunks) { out.set(c, o); o += c.byteLength; }

mkdirSync(resolve(root, 'public'), { recursive: true });
writeFileSync(resolve(root, 'public/garments.bin'), out);
console.log(`wrote public/garments.bin (${(total / 1024).toFixed(0)} KB)`);
