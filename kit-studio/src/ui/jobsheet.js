// Print-ready job sheet for the shop's printing staff: exact name, number,
// lettering, sizes in cm, colours, renders, flat artwork and a QR code that
// reopens the design. Shown in-page and printed (A4) with the browser.

import QRCode from 'qrcode';
import { money } from '../config.js';
import { SHIRT_DECAL } from '../garment/layout.js';
import { t, getLang, sizeLabel } from '../i18n.js';

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function orderRef() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  const rnd = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `KS-${String(d.getFullYear()).slice(2)}${p(d.getMonth() + 1)}${p(d.getDate())}-${rnd}`;
}

// Crop the decal to the printed area and put it on the shirt colour.
function artworkImage(canvas, background) {
  const ctx = canvas.getContext('2d');
  const { width: W, height: H } = canvas;
  const data = ctx.getImageData(0, 0, W, H).data;
  let x0 = W, y0 = H, x1 = 0, y1 = 0;
  for (let y = 0; y < H; y += 2)
    for (let x = 0; x < W; x += 2)
      if (data[(y * W + x) * 4 + 3] > 20) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  if (x1 <= x0) return null;
  const pad = Math.round(W * 0.03);
  x0 = Math.max(0, x0 - pad); y0 = Math.max(0, y0 - pad); x1 = Math.min(W, x1 + pad); y1 = Math.min(H, y1 + pad);
  const out = document.createElement('canvas');
  const scale = Math.min(1, 900 / (x1 - x0));
  out.width = Math.round((x1 - x0) * scale);
  out.height = Math.round((y1 - y0) * scale);
  const o = out.getContext('2d');
  o.fillStyle = background;
  o.fillRect(0, 0, out.width, out.height);
  o.drawImage(canvas, x0, y0, x1 - x0, y1 - y0, 0, 0, out.width, out.height);
  const pxPerCm = W / ((SHIRT_DECAL.xMax - SHIRT_DECAL.xMin) * 100);
  return { url: out.toDataURL('image/png'), widthCm: (x1 - x0) / pxPerCm, heightCm: (y1 - y0) / pxPerCm };
}

export async function openJobSheet({ demoHost = false, design: d, kit, info, front, back, artwork, config }) {
  const ref = orderRef();
  const qr = await QRCode.toDataURL(d.link, { margin: 1, width: 240 });
  const art = artworkImage(artwork, kit.base);
  const spec = info?.spec;
  const sw = (hex) => `<span class="swatch" style="background:${esc(hex)}"></span>${esc(hex.toUpperCase())}`;
  const hasPrint = d.name || d.number;
  const ar = getLang() === 'ar';
  const now = new Date().toLocaleString(ar ? 'ar-u-nu-latn' : config.locale);
  const cm = (v) => `${v} ${ar ? 'سم' : 'cm'}`;

  const rows = (list) => list.map(([k, v]) => `<tr><td>${esc(k)}</td><td>${v}</td></tr>`).join('');
  const orderRows = rows([
    [t('js.kit'), `<strong>${esc(d.club)} — ${esc(t(`type.${d.kitType}`))}</strong>`],
    [t('js.size'), `<strong>${esc(t(d.sizeGroup === 'kids' ? 'group.kids' : 'group.adult'))} ${esc(sizeLabel(d.size))}</strong>`],
    [t('js.qty'), `<strong>${esc(d.qty)}</strong>`],
    [t('js.patches'), esc(d.patch !== 'none' ? t(`patchLong.${d.patch}`) : t('js.none'))],
    [t('js.shorts'), d.shorts ? (d.shortsNumber ? esc(t('js.shortsWithNumber', { n: d.number })) : t('js.yes')) : t('js.no')],
    [t('js.unit'), esc(money(d.unitPrice))],
    [t('js.total'), `<strong>${esc(money(d.total))}</strong>`],
  ]);
  const printRows = hasPrint
    ? rows([
        [t('js.name'), d.name ? `<strong style="font-size:18px" dir="auto">${esc(d.name)}</strong>` : '—'],
        [t('js.number'), d.number ? `<strong style="font-size:18px">${esc(d.number)}</strong>` : '—'],
        [t('js.lettering'), `${esc(d.lettering)}${d.arched ? esc(t('js.archedSuffix')) : ''}`],
        [t('js.colour'), `${sw(info.fill)}${info.outline ? ` &nbsp;${esc(t('js.outline'))} ${sw(info.outline)}` : ''}`],
        [t('js.nameHeight'), esc(t('js.nameHeightVal', { h: info.nameHeightCm.toFixed(1) }))],
        [t('js.nameWidth'), d.name ? `${cm(info.nameWidthCm.toFixed(1))}${info.squeeze < 0.999 ? esc(t('js.condensed', { p: Math.round(info.squeeze * 100) })) : ''}` : '—'],
        [t('js.numberHeight'), cm(spec.numberHeight)],
        [t('js.numberWidth'), d.number ? cm(info.numberWidthCm.toFixed(1)) : '—'],
        [t('js.placement'), esc(t('js.placementVal', { top: spec.nameTop, gap: spec.gap }))],
        [t('js.shortsNumber'), d.shortsNumber ? esc(t('js.shortsNumberVal', { h: spec.shortsNumber })) : '—'],
      ])
    : `<tr><td colspan="2">${esc(t('js.noPrint'))}</td></tr>`;

  const root = document.getElementById('sheetRoot');
  root.innerHTML = `
    <div class="sheet-overlay" role="dialog" aria-label="${esc(t('js.dialog'))}">
      <div class="sheet-toolbar">
        <button type="button" class="close">${esc(t('js.close'))}</button>
        ${demoHost ? `<span class="sheet-note">${esc(t('js.printNote'))}</span>` : `<button type="button" class="print">${esc(t('js.print'))}</button>`}
      </div>
      <article class="sheet" dir="${ar ? 'rtl' : 'ltr'}" lang="${ar ? 'ar' : 'en'}">
        <div class="s-head">
          <div>
            <div style="color:#666;font-weight:600;text-transform:uppercase;letter-spacing:.08em;font-size:11px">${esc(config.shopName)}</div>
            <h1>${esc(t('js.title'))}</h1>
          </div>
          <div class="s-meta">
            <div class="s-ref" dir="ltr">${esc(ref)}</div>
            <div>${esc(now)}</div>
          </div>
        </div>
        <div class="s-grid">
          <div>
            <h2>${esc(t('js.order'))}</h2>
            <table>${orderRows}</table>
            <h2>${esc(t('js.printing'))}</h2>
            <table>${printRows}</table>
            ${d.name && (info.squeeze < 0.85 || info.nameHeightCm < spec.nameHeight - 0.05) ? `<div class="warn">${esc(t('js.warnLong'))}</div>` : ''}
          </div>
          <div>
            <h2>${esc(t('js.preview'))}</h2>
            <div class="s-renders"><img src="${front}" alt="${esc(t('js.front'))}" /><img src="${back}" alt="${esc(t('js.back'))}" /></div>
            ${art ? `<h2>${esc(t('js.artwork'))}</h2><div class="s-art"><img src="${art.url}" alt="${esc(t('js.artworkAlt'))}" /><div style="color:#555;margin-top:6px">${esc(t('js.printArea', { w: art.widthCm.toFixed(1), h: art.heightCm.toFixed(1) }))}</div></div>` : ''}
          </div>
        </div>
        <div class="s-foot">
          <div><div class="sign"></div><div class="sign-label">${esc(t('js.printedBy'))}</div></div>
          <div><div class="sign"></div><div class="sign-label">${esc(t('js.checkedBy'))}</div></div>
          <div><div class="sign"></div><div class="sign-label">${esc(t('js.shipped'))}</div></div>
          <div class="s-qr"><img src="${qr}" alt="${esc(t('js.qrAlt'))}" /><div class="sign-label">${esc(t('js.qr'))}</div></div>
        </div>
      </article>
    </div>`;
  const overlay = root.firstElementChild;
  const close = () => { root.innerHTML = ''; document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  overlay.querySelector('.close').addEventListener('click', close);
  overlay.querySelector('.print')?.addEventListener('click', () => window.print());
  overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });
  overlay.querySelector('.close').focus();
}
