// Design <-> URL hash, share links and branded share images.

const KEYS = {
  k: 'kitId', n: 'name', no: 'number', f: 'font', a: 'arched', g: 'sizeGroup', s: 'size', p: 'patch', sh: 'shorts', sn: 'shortsNumber', q: 'qty',
};
const BOOL = new Set(['arched', 'shorts', 'shortsNumber']);

export function encodeDesign(state) {
  const p = new URLSearchParams();
  for (const [short, key] of Object.entries(KEYS)) {
    const v = state[key];
    if (v === '' || v === false || v === undefined || v === null) continue;
    p.set(short, BOOL.has(key) ? '1' : String(v));
  }
  return p.toString();
}

export function decodeDesign(hash) {
  const out = {};
  const p = new URLSearchParams((hash || '').replace(/^#/, ''));
  for (const [short, key] of Object.entries(KEYS)) {
    if (!p.has(short)) continue;
    const v = p.get(short);
    if (BOOL.has(key)) out[key] = v === '1';
    else if (key === 'qty') out[key] = Math.max(1, Math.min(50, parseInt(v, 10) || 1));
    else if (key === 'number') out[key] = v.replace(/\D/g, '').slice(0, 2);
    else if (key === 'name') out[key] = v.slice(0, 16);
    else out[key] = v.slice(0, 40);
  }
  return out;
}

export async function shareDesign(url, title) {
  if (navigator.share && matchMedia('(pointer: coarse)').matches) {
    try {
      await navigator.share({ title, url });
      return 'shared';
    } catch (e) {
      if (e.name === 'AbortError') return 'cancelled';
    }
  }
  try {
    await navigator.clipboard.writeText(url);
  } catch {
    const ta = document.createElement('textarea');
    ta.value = url;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    ta.remove();
  }
  return 'copied';
}

const loadImg = (src) => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });

// Square social image: front and back side by side with the shop branding.
export async function saveImage({ front, back, title, subtitle, shop, accent, previewOnly = false }) {
  const [fi, bi] = await Promise.all([loadImg(front), loadImg(back)]);
  const W = 1600, H = 1600;
  const c = document.createElement('canvas');
  c.width = W; c.height = H;
  const ctx = c.getContext('2d');
  const g = ctx.createRadialGradient(W / 2, H * 0.45, 0, W / 2, H * 0.45, W * 0.75);
  g.addColorStop(0, '#343a45');
  g.addColorStop(1, '#111316');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  const iw = 760, ih = (iw * fi.height) / fi.width;
  ctx.drawImage(fi, 30, 190, iw, ih);
  ctx.drawImage(bi, W - iw - 30, 190, iw, ih);
  ctx.fillStyle = accent;
  ctx.fillRect(80, 92, 10, 72);
  ctx.fillStyle = '#ffffff';
  ctx.font = '700 72px "Barlow Condensed", sans-serif';
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(title.toUpperCase(), 112, 148);
  ctx.fillStyle = '#9aa3af';
  ctx.font = '500 30px Inter, sans-serif';
  ctx.fillText(subtitle, 114, 192);
  ctx.textAlign = 'right';
  ctx.fillStyle = '#ffffff';
  ctx.font = '700 40px "Barlow Condensed", sans-serif';
  ctx.fillText(shop.toUpperCase(), W - 80, H - 70);
  ctx.fillStyle = accent;
  ctx.font = '600 26px Inter, sans-serif';
  ctx.fillText('Designed in Kit Studio', W - 80, H - 110);
  if (previewOnly) return c.toDataURL('image/png');
  const blob = await new Promise((r) => c.toBlob(r, 'image/png'));
  const file = new File([blob], 'my-kit.png', { type: 'image/png' });
  if (navigator.canShare && navigator.canShare({ files: [file] }) && matchMedia('(pointer: coarse)').matches) {
    try { await navigator.share({ files: [file], title }); return; } catch { /* fall through to download */ }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'my-kit.png';
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
