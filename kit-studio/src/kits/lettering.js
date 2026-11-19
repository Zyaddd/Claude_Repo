// Name & number artwork. Drawn in real-world units (metres) so the 3D preview,
// the print job sheet and the printer all use the same sizes.

import { SHIRT_DECAL, SHORTS_DECAL, SHIRT_LABEL } from '../garment/layout.js';

export const FONT_STYLES = [
  { id: 'block', label: 'Classic Block', family: 'Graduate', weight: 400, nameSpacing: 0.04, numSpacing: 0.02, numScaleX: 0.78 },
  { id: 'modern', label: 'Modern', family: 'Oswald', weight: 600, nameSpacing: 0.1, numSpacing: 0.03, numScaleX: 1 },
  { id: 'sport', label: 'Sport Italic', family: 'Saira Condensed', weight: 800, italic: true, nameSpacing: 0.1, numSpacing: 0.02, numScaleX: 1 },
  { id: 'rounded', label: 'Rounded', family: 'Russo One', weight: 400, nameSpacing: 0.05, numSpacing: 0.07, numScaleX: 0.84 },
];
export const ARABIC_FONT = { family: 'Changa', weight: 700 };

// Print specification (cm) — adult and kids sizes differ, like real print sets.
export const PRINT_SPEC = {
  adult: { nameHeight: 7.5, minNameHeight: 5.5, numberHeight: 25, nameTop: 7, gap: 2, shortsNumber: 9, nameMaxWidth: 30 },
  kids: { nameHeight: 6, minNameHeight: 4.5, numberHeight: 20, nameTop: 6, gap: 1.5, shortsNumber: 7.5, nameMaxWidth: 24 },
};

const BACK_COLLAR_Y = 0.33; // metres, where the back neckline sits on the 3D shirt

export const isArabic = (s) => /[؀-ۿ]/.test(s);

export function fontCss(style, sizePx, arabic = false) {
  if (arabic) return `${ARABIC_FONT.weight} ${sizePx}px "${ARABIC_FONT.family}", sans-serif`;
  return `${style.italic ? 'italic ' : ''}${style.weight} ${sizePx}px "${style.family}", sans-serif`;
}

// Draw text with manual letter spacing; optional arc (radius in px), horizontal squeeze.
function drawSpaced(ctx, text, x, y, { spacing = 0, squeeze = 1, arc = 0, fill, outline, outlineW = 0 }) {
  const chars = [...text];
  const widths = chars.map((c) => ctx.measureText(c).width);
  const total = widths.reduce((a, b) => a + b, 0) + spacing * Math.max(chars.length - 1, 0);
  const passes = [];
  if (outline && outlineW > 0) passes.push('stroke');
  passes.push('fill');
  for (const pass of passes) {
    let cursor = -total / 2;
    chars.forEach((c, i) => {
      const cxw = cursor + widths[i] / 2;
      ctx.save();
      if (arc) {
        const ang = (cxw * squeeze) / arc;
        ctx.translate(x + Math.sin(ang) * arc, y + arc - Math.cos(ang) * arc);
        ctx.rotate(ang);
        ctx.scale(squeeze, 1);
      } else {
        ctx.translate(x + cxw * squeeze, y);
        ctx.scale(squeeze, 1);
      }
      if (pass === 'stroke') {
        ctx.lineWidth = outlineW / squeeze;
        ctx.strokeStyle = outline;
        ctx.lineJoin = 'round';
        ctx.strokeText(c, -widths[i] / 2, 0);
      } else {
        ctx.fillStyle = fill;
        ctx.fillText(c, -widths[i] / 2, 0);
      }
      ctx.restore();
      cursor += widths[i] + spacing;
    });
  }
  return total * squeeze;
}

// Font size whose capital letters are `capH` px tall.
function sizeForCap(ctx, style, capH, arabic, sample = 'H') {
  ctx.font = fontCss(style, 100, arabic);
  const m = ctx.measureText(arabic ? 'ك' : sample);
  const h = (m.actualBoundingBoxAscent || 72) + (arabic ? m.actualBoundingBoxDescent || 0 : 0);
  return (100 * capH) / Math.max(h, 1);
}

// Renders the back name & number into the decal canvas covering SHIRT_DECAL.
// Returns layout info (in cm) for the job sheet.
export function drawBackPrint(canvas, design, kit, pxPerM) {
  const box = SHIRT_DECAL;
  const W = Math.round((box.xMax - box.xMin) * pxPerM);
  const H = Math.round((box.yMax - box.yMin) * pxPerM);
  if (canvas.width !== W || canvas.height !== H) { canvas.width = W; canvas.height = H; }
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, W, H);
  const spec = PRINT_SPEC[design.sizeGroup || 'adult'];
  const style = FONT_STYLES.find((s) => s.id === design.font) || FONT_STYLES[0];
  const fill = design.color === 'custom' && design.customFill ? design.customFill : kit.print.fill;
  const outline = kit.print.outline;
  const yToPx = (y) => (box.yMax - y) * pxPerM;
  const cx = W / 2;
  const info = { nameWidthCm: 0, numberWidthCm: 0, squeeze: 1 };
  ctx.textBaseline = 'alphabetic';

  const name = (design.name || '').trim();
  const nameTopY = BACK_COLLAR_Y - spec.nameTop / 100;
  const nameBoxH = (spec.nameHeight / 100) * pxPerM;
  info.nameHeightCm = spec.nameHeight;
  if (name) {
    const arabic = isArabic(name);
    const txt = arabic ? name : name.toUpperCase();
    const maxW = (spec.nameMaxWidth / 100) * pxPerM;
    // Fit long names like a printer would: condense to 78%, then reduce the
    // letter height (down to the minimum), then condense further if needed.
    const measure = (h) => {
      const size = sizeForCap(ctx, style, h, arabic);
      ctx.font = fontCss(style, size, arabic);
      const spacing = arabic ? 0 : style.nameSpacing * h;
      const w = arabic ? ctx.measureText(txt).width : [...txt].reduce((a, c) => a + ctx.measureText(c).width, 0) + spacing * (txt.length - 1);
      return { size, spacing, w };
    };
    let nameH = nameBoxH;
    let m = measure(nameH);
    const minSqueeze = 0.78;
    if (m.w * minSqueeze > maxW) {
      const minH = ((spec.minNameHeight || spec.nameHeight) / 100) * pxPerM;
      nameH = Math.max(minH, (nameH * maxW) / (m.w * minSqueeze));
      m = measure(nameH);
    }
    const squeeze = Math.min(1, maxW / m.w);
    ctx.font = fontCss(style, m.size, arabic);
    if ('direction' in ctx) ctx.direction = arabic ? 'rtl' : 'ltr';
    info.squeeze = squeeze;
    info.nameWidthCm = (m.w * squeeze * 100) / pxPerM;
    info.nameHeightCm = (nameH * 100) / pxPerM;
    // Keep the name's vertical centre fixed when its height shrinks.
    const baseline = yToPx(nameTopY) + (nameBoxH + nameH) / 2 - (arabic ? nameH * 0.22 : 0);
    if (arabic) {
      ctx.save();
      ctx.translate(cx, baseline);
      ctx.scale(squeeze, 1);
      ctx.textAlign = 'center';
      if (outline) { ctx.lineWidth = nameH * 0.08; ctx.strokeStyle = outline; ctx.lineJoin = 'round'; ctx.strokeText(txt, 0, 0); }
      ctx.fillStyle = fill;
      ctx.fillText(txt, 0, 0);
      ctx.restore();
    } else {
      ctx.textAlign = 'left';
      drawSpaced(ctx, txt, cx, baseline, {
        spacing: m.spacing, squeeze, arc: design.arched ? 0.42 * pxPerM : 0, fill, outline, outlineW: nameH * 0.12,
      });
    }
  }

  const num = String(design.number ?? '').trim();
  if (num) {
    const numTopY = nameTopY - (spec.nameHeight + spec.gap) / 100;
    const numH = (spec.numberHeight / 100) * pxPerM;
    const size = sizeForCap(ctx, style, numH, false, '0');
    ctx.font = fontCss(style, size, false);
    if ('direction' in ctx) ctx.direction = 'ltr';
    ctx.textAlign = 'left';
    const w = drawSpaced(ctx, num, cx, yToPx(numTopY) + numH, {
      spacing: style.numSpacing * numH, squeeze: style.numScaleX, fill, outline, outlineW: numH * 0.05,
    });
    info.numberWidthCm = (w * 100) / pxPerM;
  }
  info.spec = spec;
  info.style = style;
  info.fill = fill;
  info.outline = outline;
  return info;
}

export function drawShortsPrint(canvas, design, kit, pxPerM) {
  const box = SHORTS_DECAL;
  const W = Math.round((box.xMax - box.xMin) * pxPerM);
  const H = Math.round((box.yMax - box.yMin) * pxPerM);
  if (canvas.width !== W || canvas.height !== H) { canvas.width = W; canvas.height = H; }
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, W, H);
  const num = String(design.number ?? '').trim();
  if (!num || !design.shortsNumber) return;
  const style = FONT_STYLES.find((s) => s.id === design.font) || FONT_STYLES[0];
  const spec = PRINT_SPEC[design.sizeGroup || 'adult'];
  const numH = (spec.shortsNumber / 100) * pxPerM;
  const size = sizeForCap(ctx, style, numH, false, '0');
  ctx.font = fontCss(style, size, false);
  ctx.textBaseline = 'alphabetic';
  ctx.textAlign = 'left';
  const fill = kit.shorts.trim === kit.shorts.base ? kit.print.fill : kit.shorts.trim;
  // Number centred at x = 0.125 m, y = -0.705 m (front of the wearer's left leg).
  const cx = (0.125 - box.xMin) * pxPerM;
  const cy = (box.yMax - -0.705) * pxPerM;
  drawSpaced(ctx, num, cx, cy + numH / 2, { spacing: style.numSpacing * numH, squeeze: style.numScaleX, fill });
}

// Printed care label inside the back neck: shop name + size.
export function drawLabel(canvas, shopName, size) {
  const box = SHIRT_LABEL;
  const px = 2400;
  canvas.width = Math.round((box.xMax - box.xMin) * px);
  canvas.height = Math.round((box.yMax - box.yMin) * px);
  const ctx = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;
  ctx.clearRect(0, 0, W, H);
  ctx.fillStyle = 'rgba(255,255,255,0.9)';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `700 ${H * 0.2}px "Barlow Condensed", sans-serif`;
  ctx.fillText(shopName.toUpperCase(), W / 2, H * 0.28);
  ctx.font = `800 ${H * 0.36}px "Barlow Condensed", sans-serif`;
  ctx.fillText(size || 'M', W / 2, H * 0.62);
  ctx.font = `500 ${H * 0.1}px "Inter", sans-serif`;
  ctx.fillText('100% RECYCLED POLYESTER', W / 2, H * 0.88);
}
