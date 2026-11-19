// Print-ready job sheet for the shop's printing staff: exact name, number,
// lettering, sizes in cm, colours, renders, flat artwork and a QR code that
// reopens the design. Shown in-page and printed (A4) with the browser.

import QRCode from 'qrcode';
import { money } from '../config.js';
import { PATCHES } from '../kits/painter.js';
import { SHIRT_DECAL } from '../garment/layout.js';

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

export async function openJobSheet({ design: d, kit, info, front, back, artwork, config }) {
  const ref = orderRef();
  const qr = await QRCode.toDataURL(d.link, { margin: 1, width: 240 });
  const art = artworkImage(artwork, kit.base);
  const spec = info?.spec;
  const sw = (hex) => `<span class="swatch" style="background:${esc(hex)}"></span>${esc(hex.toUpperCase())}`;
  const hasPrint = d.name || d.number;
  const now = new Date().toLocaleString(config.locale);

  const rows = (list) => list.map(([k, v]) => `<tr><td>${esc(k)}</td><td>${v}</td></tr>`).join('');
  const orderRows = rows([
    ['Kit', `<strong>${esc(d.club)} — ${esc(d.kitType)}</strong>`],
    ['Size', `<strong>${d.sizeGroup === 'kids' ? 'Kids' : 'Adult'} ${esc(d.size)}</strong>`],
    ['Quantity', `<strong>${esc(d.qty)}</strong>`],
    ['Sleeve patches', esc(PATCHES[d.patch]?.label || 'None')],
    ['Shorts', d.shorts ? `Yes${d.shortsNumber ? ` — number ${esc(d.number)} on front left leg` : ''}` : 'No'],
    ['Unit price', esc(money(d.unitPrice))],
    ['Total', `<strong>${esc(money(d.total))}</strong>`],
  ]);
  const printRows = hasPrint
    ? rows([
        ['Name', d.name ? `<strong style="font-size:18px">${esc(d.name)}</strong>` : '—'],
        ['Number', d.number ? `<strong style="font-size:18px">${esc(d.number)}</strong>` : '—'],
        ['Lettering', `${esc(d.lettering)}${d.arched ? ' · arched name' : ''}`],
        ['Colour', `${sw(info.fill)}${info.outline ? ` &nbsp;outline ${sw(info.outline)}` : ''}`],
        ['Name height', `${info.nameHeightCm.toFixed(1)} cm (capitals)`],
        ['Name width', d.name ? `${info.nameWidthCm.toFixed(1)} cm${info.squeeze < 0.999 ? ` (condensed to ${Math.round(info.squeeze * 100)}%)` : ''}` : '—'],
        ['Number height', `${spec.numberHeight} cm`],
        ['Number width', d.number ? `${info.numberWidthCm.toFixed(1)} cm` : '—'],
        ['Placement', `Top of name ${spec.nameTop} cm below back collar seam; ${spec.gap} cm gap to number; centred`],
        ['Shorts number', d.shortsNumber ? `${spec.shortsNumber} cm, front left leg` : '—'],
      ])
    : '<tr><td colspan="2">No name or number printing on this order.</td></tr>';

  const root = document.getElementById('sheetRoot');
  root.innerHTML = `
    <div class="sheet-overlay" role="dialog" aria-label="Print job sheet">
      <div class="sheet-toolbar">
        <button type="button" class="close">Close</button>
        <button type="button" class="print">Print / Save PDF</button>
      </div>
      <article class="sheet">
        <div class="s-head">
          <div>
            <div style="color:#666;font-weight:600;text-transform:uppercase;letter-spacing:.08em;font-size:11px">${esc(config.shopName)}</div>
            <h1>Print job sheet</h1>
          </div>
          <div class="s-meta">
            <div class="s-ref">${esc(ref)}</div>
            <div>${esc(now)}</div>
          </div>
        </div>
        <div class="s-grid">
          <div>
            <h2>Order</h2>
            <table>${orderRows}</table>
            <h2>Printing</h2>
            <table>${printRows}</table>
            ${d.name && (info.squeeze < 0.85 || info.nameHeightCm < spec.nameHeight - 0.05) ? '<div class="warn">Long name: lettering is condensed and/or reduced in height to fit the maximum width. Check spacing before pressing.</div>' : ''}
          </div>
          <div>
            <h2>Preview</h2>
            <div class="s-renders"><img src="${front}" alt="Front" /><img src="${back}" alt="Back" /></div>
            ${art ? `<h2>Artwork (actual layout)</h2><div class="s-art"><img src="${art.url}" alt="Name and number artwork" /><div style="color:#555;margin-top:6px">Print area ≈ ${art.widthCm.toFixed(1)} × ${art.heightCm.toFixed(1)} cm</div></div>` : ''}
          </div>
        </div>
        <div class="s-foot">
          <div><div class="sign"></div><div class="sign-label">Printed by</div></div>
          <div><div class="sign"></div><div class="sign-label">Checked by</div></div>
          <div><div class="sign"></div><div class="sign-label">Collected / shipped</div></div>
          <div class="s-qr"><img src="${qr}" alt="QR code to reopen this design" /><div class="sign-label">Scan to reopen design</div></div>
        </div>
      </article>
    </div>`;
  const overlay = root.firstElementChild;
  const close = () => { root.innerHTML = ''; document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  overlay.querySelector('.close').addEventListener('click', close);
  overlay.querySelector('.print').addEventListener('click', () => window.print());
  overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });
  overlay.querySelector('.close').focus();
}
