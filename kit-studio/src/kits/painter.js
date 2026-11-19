// Paints a kit's texture atlas (front, back, both sleeves) and its shorts
// atlas in physical coordinates, so stripes and hoops line up across panels.

import {
  ATLAS_SIZE, SHIRT, SHIRT_REGIONS, SLEEVE_MAP, SHORTS, SHORTS_ATLAS, SHORTS_REGIONS, regionPx, pxPerMetre,
} from '../garment/layout.js';
import { clubOf } from './kits.js';

const BOX = SHIRT.box;
const SBOX = SHORTS.box;

export function shade(hex, amt) {
  const n = parseInt(hex.slice(1), 16);
  let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
  const f = (c) => Math.max(0, Math.min(255, Math.round(amt < 0 ? c * (1 + amt) : c + (255 - c) * amt)));
  r = f(r); g = f(g); b = f(b);
  return `#${((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1)}`;
}
export function luminance(hex) {
  const n = parseInt(hex.slice(1), 16);
  return (0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
}

// ---------------------------------------------------------------
// Crest (original procedural club badges)
// ---------------------------------------------------------------
function crestPath(ctx, shape, s) {
  ctx.beginPath();
  if (shape === 'round') {
    ctx.arc(0, 0, s * 0.5, 0, Math.PI * 2);
  } else if (shape === 'pentagon') {
    for (let i = 0; i < 5; i++) {
      const a = -Math.PI / 2 + (i * Math.PI * 2) / 5;
      ctx.lineTo(Math.cos(a) * s * 0.52, Math.sin(a) * s * 0.52 + s * 0.03);
    }
    ctx.closePath();
  } else if (shape === 'diamond') {
    ctx.moveTo(0, -s * 0.55); ctx.lineTo(s * 0.45, 0); ctx.lineTo(0, s * 0.55); ctx.lineTo(-s * 0.45, 0); ctx.closePath();
  } else {
    // shield
    ctx.moveTo(-s * 0.42, -s * 0.5);
    ctx.lineTo(s * 0.42, -s * 0.5);
    ctx.lineTo(s * 0.42, s * 0.05);
    ctx.quadraticCurveTo(s * 0.4, s * 0.38, 0, s * 0.55);
    ctx.quadraticCurveTo(-s * 0.4, s * 0.38, -s * 0.42, s * 0.05);
    ctx.closePath();
  }
}

function emblem(ctx, type, s, color) {
  ctx.save();
  ctx.fillStyle = color;
  ctx.strokeStyle = color;
  ctx.lineWidth = s * 0.05;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.beginPath();
  switch (type) {
    case 'bridge':
      ctx.arc(0, s * 0.12, s * 0.22, Math.PI, 0);
      ctx.moveTo(-s * 0.3, s * 0.12); ctx.lineTo(s * 0.3, s * 0.12);
      for (let i = -2; i <= 2; i++) { ctx.moveTo(i * s * 0.09, s * 0.12); ctx.lineTo(i * s * 0.09, s * 0.12 - Math.sqrt(Math.max(0, 0.0484 - (i * 0.09) ** 2)) * s); }
      ctx.stroke();
      break;
    case 'crown':
      ctx.moveTo(-s * 0.24, s * 0.12); ctx.lineTo(-s * 0.26, -s * 0.12); ctx.lineTo(-s * 0.12, 0); ctx.lineTo(0, -s * 0.18);
      ctx.lineTo(s * 0.12, 0); ctx.lineTo(s * 0.26, -s * 0.12); ctx.lineTo(s * 0.24, s * 0.12); ctx.closePath(); ctx.fill();
      break;
    case 'anchor':
      ctx.moveTo(0, -s * 0.2); ctx.lineTo(0, s * 0.2);
      ctx.moveTo(-s * 0.12, -s * 0.1); ctx.lineTo(s * 0.12, -s * 0.1);
      ctx.moveTo(-s * 0.2, s * 0.05); ctx.quadraticCurveTo(-s * 0.18, s * 0.22, 0, s * 0.22); ctx.quadraticCurveTo(s * 0.18, s * 0.22, s * 0.2, s * 0.05);
      ctx.stroke(); ctx.beginPath(); ctx.arc(0, -s * 0.24, s * 0.045, 0, Math.PI * 2); ctx.stroke();
      break;
    case 'oak':
      ctx.ellipse(0, -s * 0.04, s * 0.13, s * 0.2, 0, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.moveTo(0, s * 0.12); ctx.lineTo(0, s * 0.26); ctx.stroke();
      break;
    case 'star':
      for (let i = 0; i < 10; i++) {
        const a = -Math.PI / 2 + (i * Math.PI) / 5;
        const r = i % 2 ? s * 0.09 : s * 0.22;
        ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
      }
      ctx.closePath(); ctx.fill();
      break;
    case 'flame':
      ctx.moveTo(0, -s * 0.26);
      ctx.bezierCurveTo(s * 0.22, -s * 0.05, s * 0.18, s * 0.2, 0, s * 0.22);
      ctx.bezierCurveTo(-s * 0.18, s * 0.2, -s * 0.2, 0, -s * 0.05, -s * 0.1);
      ctx.bezierCurveTo(-s * 0.02, 0, s * 0.02, -s * 0.12, 0, -s * 0.26);
      ctx.fill();
      break;
    case 'wing':
      for (let i = 0; i < 4; i++) {
        ctx.moveTo(-s * 0.22, -s * 0.12 + i * s * 0.09);
        ctx.quadraticCurveTo(s * 0.05, -s * 0.2 + i * s * 0.1, s * 0.24 - i * s * 0.05, -s * 0.18 + i * s * 0.12);
      }
      ctx.stroke();
      break;
    case 'wave':
    default:
      for (let i = 0; i < 3; i++) {
        const y = -s * 0.1 + i * s * 0.1;
        ctx.moveTo(-s * 0.24, y);
        ctx.bezierCurveTo(-s * 0.12, y - s * 0.08, -s * 0.04, y + s * 0.08, s * 0.06, y);
        ctx.bezierCurveTo(s * 0.14, y - s * 0.07, s * 0.2, y + s * 0.04, s * 0.24, y);
      }
      ctx.stroke();
  }
  ctx.restore();
}

export function drawCrest(ctx, club, cx, cy, size) {
  const c = club.crest;
  ctx.save();
  ctx.translate(cx, cy);
  crestPath(ctx, c.shape, size);
  ctx.fillStyle = c.ring;
  ctx.fill();
  ctx.save();
  ctx.scale(0.86, 0.86);
  crestPath(ctx, c.shape, size);
  ctx.fillStyle = c.fill;
  ctx.fill();
  ctx.restore();
  emblem(ctx, c.emblem, size * 0.95, c.accent);
  ctx.fillStyle = c.ring;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `700 ${size * 0.15}px "Barlow Condensed", "Oswald", sans-serif`;
  const ty = c.shape === 'round' ? size * 0.3 : c.shape === 'diamond' ? size * 0.3 : -size * 0.33;
  ctx.fillText(club.short, 0, ty);
  if (c.shape !== 'diamond') {
    ctx.font = `600 ${size * 0.08}px "Barlow Condensed", sans-serif`;
    ctx.fillText(String(club.founded), 0, c.shape === 'round' ? -size * 0.33 : size * 0.33);
  }
  ctx.restore();
}

// Kit-maker mark (a fictional brand mark)
function drawMakerMark(ctx, cx, cy, size, color) {
  ctx.save();
  ctx.translate(cx, cy);
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(-size * 0.5, size * 0.25);
  ctx.lineTo(-size * 0.1, -size * 0.3);
  ctx.lineTo(size * 0.05, -size * 0.3);
  ctx.lineTo(-size * 0.25, size * 0.25);
  ctx.closePath();
  ctx.moveTo(-size * 0.05, size * 0.25);
  ctx.lineTo(size * 0.35, -size * 0.3);
  ctx.lineTo(size * 0.5, -size * 0.3);
  ctx.lineTo(size * 0.2, size * 0.25);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

function drawSponsor(ctx, sp, cx, cy, maxW, px) {
  ctx.save();
  ctx.translate(cx, cy);
  if (sp.panel) {
    ctx.fillStyle = sp.panel;
    const pw = maxW * 1.08, ph = 0.075 * px;
    ctx.beginPath();
    ctx.roundRect(-pw / 2, -ph / 2, pw, ph, ph * 0.18);
    ctx.fill();
  }
  ctx.fillStyle = sp.color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  let font;
  if (sp.style === 'serif') font = `italic 700 ${0.06 * px}px Georgia, "Times New Roman", serif`;
  else if (sp.style === 'italic') font = `italic 800 ${0.058 * px}px "Saira Condensed", sans-serif`;
  else if (sp.style === 'round') font = `400 ${0.055 * px}px "Russo One", sans-serif`;
  else font = `700 ${0.058 * px}px "Oswald", sans-serif`;
  ctx.font = font;
  const spacing = sp.style === 'wide' ? 0.012 * px : 0.004 * px;
  const chars = [...sp.text];
  const widths = chars.map((c) => ctx.measureText(c).width);
  let total = widths.reduce((a, b) => a + b, 0) + spacing * (chars.length - 1);
  const sx = Math.min(1, maxW / total);
  ctx.scale(sx, 1);
  let x = -total / 2;
  ctx.textAlign = 'left';
  chars.forEach((c, i) => { ctx.fillText(c, x, 0); x += widths[i] + spacing; });
  ctx.restore();
}

// ---------------------------------------------------------------
// Patterns in physical coordinates
// ---------------------------------------------------------------
function poly(ctx, toPx, pts) {
  ctx.beginPath();
  pts.forEach(([x, y], i) => { const [px, py] = toPx(x, y); if (i) ctx.lineTo(px, py); else ctx.moveTo(px, py); });
  ctx.closePath();
  ctx.fill();
}

function rectP(ctx, toPx, x0, y0, x1, y1) {
  poly(ctx, toPx, [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]);
}

function paintTorso(ctx, kit, region, mirror, side) {
  const toPx = (x, y) => regionPx(region, BOX, x, y, mirror);
  const px = pxPerMetre(region, BOX);
  ctx.save();
  ctx.beginPath();
  ctx.rect(region.x, region.y, region.w, region.h);
  ctx.clip();
  const p = kit.pattern;
  // base
  if (p.type === 'gradient') {
    const [, y0] = toPx(0, BOX.yMax), [, y1] = toPx(0, BOX.yMin);
    const g = ctx.createLinearGradient(0, y0, 0, y1);
    g.addColorStop(0, kit.base);
    g.addColorStop(1, p.to);
    ctx.fillStyle = g;
  } else ctx.fillStyle = kit.base;
  ctx.fillRect(region.x, region.y, region.w, region.h);

  ctx.fillStyle = p.color || kit.second;
  switch (p.type) {
    case 'stripes': {
      const w = p.width || 0.05;
      for (let x = -w / 2 - w * 8; x < 0.4; x += w * 2) rectP(ctx, toPx, x + w, BOX.yMin - 0.1, x + 2 * w, BOX.yMax + 0.1);
      break;
    }
    case 'pinstripe': {
      const w = 0.028;
      for (let x = -0.4; x < 0.4; x += w) rectP(ctx, toPx, x, BOX.yMin - 0.1, x + 0.0035, BOX.yMax + 0.1);
      break;
    }
    case 'hoops': {
      const hgt = p.height || 0.05;
      for (let y = BOX.yMin; y < BOX.yMax; y += hgt * 2) rectP(ctx, toPx, -0.5, y, 0.5, y + hgt);
      break;
    }
    case 'sash': {
      const w = p.width || 0.08;
      // From the wearer's right shoulder to the left hip on the front.
      const s = side === 'front' ? 1 : -1;
      poly(ctx, toPx, [[-0.32 * s, 0.32 + w], [-0.32 * s, 0.32 - w], [0.32 * s, -0.42 - w], [0.32 * s, -0.42 + w]].map(([x, y]) => [x, y]));
      break;
    }
    case 'halves':
      rectP(ctx, toPx, 0, BOX.yMin - 0.1, 0.5, BOX.yMax + 0.1);
      break;
    case 'chevron': {
      const c = 0.13, w = 0.035;
      poly(ctx, toPx, [[-0.4, c + 0.14], [0, c], [0.4, c + 0.14], [0.4, c + 0.14 - w], [0, c - w], [-0.4, c + 0.14 - w]]);
      ctx.fillStyle = shade(p.color, -0.25);
      poly(ctx, toPx, [[-0.4, c + 0.1 - w], [0, c - w * 1.6], [0.4, c + 0.1 - w], [0.4, c + 0.1 - w * 1.4], [0, c - w * 2], [-0.4, c + 0.1 - w * 1.4]]);
      break;
    }
    case 'yoke':
      poly(ctx, toPx, [[-0.5, 0.4], [0.5, 0.4], [0.5, 0.19], [0.2, 0.235], [0, 0.245], [-0.2, 0.235], [-0.5, 0.19]]);
      break;
    case 'sidepanel':
      rectP(ctx, toPx, 0.19, BOX.yMin - 0.1, 0.5, BOX.yMax + 0.1);
      rectP(ctx, toPx, -0.5, BOX.yMin - 0.1, -0.19, BOX.yMax + 0.1);
      break;
    default:
      break;
  }

  // Tonal jacquard overlay
  const tonal = p.tonal ?? 'diagonal';
  if (tonal !== 'none') {
    ctx.globalAlpha = luminance(kit.base) > 0.6 ? 0.05 : 0.07;
    ctx.fillStyle = luminance(kit.base) > 0.6 ? '#000' : '#fff';
    if (tonal === 'diagonal') {
      for (let k = -1.2; k < 1.2; k += 0.024) poly(ctx, toPx, [[k, -0.5], [k + 0.008, -0.5], [k + 0.9, 0.5], [k + 0.892, 0.5]]);
    } else if (tonal === 'pin') {
      for (let x = -0.4; x < 0.4; x += 0.012) rectP(ctx, toPx, x, BOX.yMin - 0.1, x + 0.002, BOX.yMax + 0.1);
    } else if (tonal === 'wave') {
      for (let y = BOX.yMin; y < BOX.yMax; y += 0.03) {
        const pts = [];
        for (let x = -0.35; x <= 0.35; x += 0.02) pts.push([x, y + Math.sin(x * 25) * 0.008]);
        for (let x = 0.35; x >= -0.35; x -= 0.02) pts.push([x, y + 0.004 + Math.sin(x * 25) * 0.008]);
        poly(ctx, toPx, pts);
      }
    }
    ctx.globalAlpha = 1;
  }

  const club = clubOf(kit);
  if (side === 'front') {
    // Crest on the wearer's left chest (+x), maker mark on the right.
    const [cx, cy] = toPx(0.105, 0.19);
    drawCrest(ctx, club, cx, cy, 0.085 * px);
    const [mx, my] = toPx(-0.105, 0.2);
    drawMakerMark(ctx, mx, my, 0.05 * px, kit.trim);
    const [sx, sy] = toPx(0, 0.055);
    drawSponsor(ctx, kit.sponsor, sx, sy, 0.27 * px, px);
  } else {
    // Small club name under the back collar.
    const [bx, by] = toPx(0, 0.295);
    ctx.fillStyle = kit.print.fill === kit.base ? kit.trim : shade(kit.base, luminance(kit.base) > 0.5 ? -0.35 : 0.45);
    ctx.font = `600 ${0.014 * px}px "Barlow Condensed", sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(club.name.toUpperCase(), bx, by);
  }
  ctx.restore();
}

// Sleeve region: u (0..1) around the arm (0.5 = outer side), v along the arm.
function paintSleeve(ctx, kit, region, patch, isRight) {
  ctx.save();
  ctx.beginPath();
  ctx.rect(region.x, region.y, region.w, region.h);
  ctx.clip();
  ctx.fillStyle = kit.sleeve || kit.base;
  if (!kit.sleeve && kit.pattern.type === 'gradient') ctx.fillStyle = kit.base;
  ctx.fillRect(region.x, region.y, region.w, region.h);
  const tToY = (t) => region.y + ((t - SLEEVE_MAP.tStart) / SLEEVE_MAP.tLen) * region.h;
  const p = kit.pattern;
  if (!kit.sleeve && p.type === 'hoops') {
    ctx.fillStyle = p.color;
    const hgt = p.height || 0.05;
    for (let t = 0; t < 0.3; t += hgt * 2) ctx.fillRect(region.x, tToY(t + 0.02), region.w, (hgt / SLEEVE_MAP.tLen) * region.h);
  }
  if (!kit.sleeve && p.type === 'pinstripe') {
    ctx.fillStyle = p.color;
    for (let u = 0; u < 1; u += 0.06) ctx.fillRect(region.x + u * region.w, region.y, 0.008 * region.w, region.h);
  }
  // Cuff bands just above the ribbed cuff.
  ctx.fillStyle = kit.cuff || kit.trim;
  ctx.fillRect(region.x, tToY(SHIRT.arm.cuff - 0.02), region.w, (0.008 / SLEEVE_MAP.tLen) * region.h);
  ctx.fillRect(region.x, tToY(SHIRT.arm.cuff - 0.007), region.w, region.h);
  if (patch) drawPatch(ctx, patch, region.x + region.w * 0.5, tToY(0.075), region.w * 0.13, isRight);
  ctx.restore();
}

// Generic, original sleeve patches (shops add their licensed league badges).
export const PATCHES = {
  none: null,
  league: { label: 'League patch', fill: '#ffffff', ring: '#3a0a5e', star: '#3a0a5e' },
  champions: { label: 'Champions patch', fill: '#f4d36a', ring: '#7a5a10', star: '#7a5a10' },
};

function drawPatch(ctx, id, cx, cy, size) {
  const p = PATCHES[id];
  if (!p) return;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(1, 1.1);
  ctx.beginPath();
  ctx.moveTo(-size * 0.42, -size * 0.5);
  ctx.lineTo(size * 0.42, -size * 0.5);
  ctx.lineTo(size * 0.42, size * 0.1);
  ctx.quadraticCurveTo(0, size * 0.62, -size * 0.42, size * 0.1);
  ctx.closePath();
  ctx.fillStyle = p.ring;
  ctx.fill();
  ctx.scale(0.86, 0.86);
  ctx.beginPath();
  ctx.moveTo(-size * 0.42, -size * 0.5);
  ctx.lineTo(size * 0.42, -size * 0.5);
  ctx.lineTo(size * 0.42, size * 0.1);
  ctx.quadraticCurveTo(0, size * 0.62, -size * 0.42, size * 0.1);
  ctx.closePath();
  ctx.fillStyle = p.fill;
  ctx.fill();
  ctx.fillStyle = p.star;
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    const r = i % 2 ? size * 0.09 : size * 0.22;
    ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r - size * 0.04);
  }
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

export function paintShirtAtlas(canvas, kit, { patch = 'none' } = {}) {
  canvas.width = canvas.height = ATLAS_SIZE;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = kit.base;
  ctx.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
  paintTorso(ctx, kit, SHIRT_REGIONS.front, false, 'front');
  paintTorso(ctx, kit, SHIRT_REGIONS.back, true, 'back');
  paintSleeve(ctx, kit, SHIRT_REGIONS.sleeveR, patch, true);
  paintSleeve(ctx, kit, SHIRT_REGIONS.sleeveL, patch, false);
  return canvas;
}

export function paintShortsAtlas(canvas, kit) {
  canvas.width = SHORTS_ATLAS.w;
  canvas.height = SHORTS_ATLAS.h;
  const ctx = canvas.getContext('2d');
  const s = kit.shorts;
  ctx.fillStyle = s.base;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  for (const [name, mirror] of [['front', false], ['back', true]]) {
    const region = SHORTS_REGIONS[name];
    const toPx = (x, y) => regionPx(region, SBOX, x, y, mirror);
    const px = pxPerMetre(region, SBOX);
    ctx.save();
    ctx.beginPath();
    ctx.rect(region.x, region.y, region.w, region.h);
    ctx.clip();
    // Side stripes
    ctx.fillStyle = s.stripe;
    rectP(ctx, toPx, 0.205, SBOX.yMin, 0.235, SBOX.yMax);
    rectP(ctx, toPx, -0.235, SBOX.yMin, -0.205, SBOX.yMax);
    // Hem band
    ctx.fillStyle = s.trim;
    rectP(ctx, toPx, -0.5, SHORTS.hemY - 0.01, 0.5, SHORTS.hemY + 0.012);
    if (name === 'front') {
      const club = clubOf(kit);
      const [cx, cy] = toPx(-0.14, -0.7);
      drawCrest(ctx, club, cx, cy, 0.06 * px);
      const [mx, my] = toPx(0.15, -0.6);
      drawMakerMark(ctx, mx, my, 0.032 * px, s.trim);
    }
    ctx.restore();
  }
  return canvas;
}

// Flat shirt thumbnail for the kit picker.
export function kitThumbnail(kit, atlas, size = 160) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const ctx = c.getContext('2d');
  const s = size / 100;
  ctx.save();
  ctx.scale(s, s);
  const path = new Path2D('M30 8 L40 4 Q50 12 60 4 L70 8 L94 22 L84 42 L74 36 L74 94 L26 94 L26 36 L16 42 L6 22 Z');
  ctx.clip(path);
  const r = SHIRT_REGIONS.front;
  // torso region: x -0.3..0.3 => 18..82 ; y 0.36..-0.4 => 2..96
  ctx.drawImage(atlas, r.x, r.y, r.w, r.h, 18, 0, 64, 64 * (r.h / r.w));
  // sleeves
  ctx.fillStyle = kit.sleeve || kit.base;
  ctx.beginPath(); ctx.moveTo(30, 8); ctx.lineTo(6, 22); ctx.lineTo(16, 42); ctx.lineTo(26, 36); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(70, 8); ctx.lineTo(94, 22); ctx.lineTo(84, 42); ctx.lineTo(74, 36); ctx.closePath(); ctx.fill();
  ctx.fillStyle = kit.cuff || kit.trim;
  ctx.beginPath(); ctx.moveTo(6, 22); ctx.lineTo(8.2, 18.5); ctx.lineTo(18.3, 38.5); ctx.lineTo(16, 42); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(94, 22); ctx.lineTo(91.8, 18.5); ctx.lineTo(81.7, 38.5); ctx.lineTo(84, 42); ctx.closePath(); ctx.fill();
  ctx.restore();
  // collar + outline
  ctx.save();
  ctx.scale(s, s);
  ctx.strokeStyle = kit.trim;
  ctx.lineWidth = 2.4;
  ctx.beginPath(); ctx.moveTo(40, 4.5); ctx.quadraticCurveTo(50, 13, 60, 4.5); ctx.stroke();
  ctx.strokeStyle = 'rgba(0,0,0,0.25)';
  ctx.lineWidth = 0.8;
  ctx.stroke(new Path2D('M30 8 L40 4 Q50 12 60 4 L70 8 L94 22 L84 42 L74 36 L74 94 L26 94 L26 36 L16 42 L6 22 Z'));
  ctx.restore();
  return c;
}

export function crestImage(club, size = 64) {
  const c = document.createElement('canvas');
  c.width = c.height = size * 2;
  const ctx = c.getContext('2d');
  drawCrest(ctx, club, size, size, size * 1.7);
  return c;
}
