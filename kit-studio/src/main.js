import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';
import '@fontsource/barlow-condensed/600.css';
import '@fontsource/barlow-condensed/700.css';
import '@fontsource/oswald/600.css';
import '@fontsource/oswald/700.css';
import '@fontsource/graduate/400.css';
import '@fontsource/saira-condensed/800.css';
import '@fontsource/russo-one/400.css';
import '@fontsource/changa/700.css';
import './styles.css';

import { CONFIG, money } from './config.js';
import { CLUBS, KITS, clubOf, kitById } from './kits/kits.js';
import { kitThumbnail, crestImage, paintShirtAtlas, PATCHES } from './kits/painter.js';
import { FONT_STYLES, fontCss, isArabic } from './kits/lettering.js';
import { Studio } from './render/scene.js';
import { KitModel } from './render/kitModel.js';
import { loadGarments } from './garment/loader.js';
import { openJobSheet } from './ui/jobsheet.js';
import { encodeDesign, decodeDesign, shareDesign, saveImage } from './ui/share.js';

const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];

// ------------------------------------------------------------------
// State
// ------------------------------------------------------------------
const state = {
  kitId: KITS[0].id,
  name: '',
  number: '',
  font: 'block',
  arched: false,
  sizeGroup: 'adult',
  size: 'M',
  patch: 'none',
  shorts: false,
  shortsNumber: false,
  qty: 1,
  ...decodeDesign(location.hash),
};
if (!kitById(state.kitId)) state.kitId = KITS[0].id;

// Analytics hook: shops can read these from window.dataLayer (GTM etc.)
function track(event, data = {}) {
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push({ event: `kitstudio_${event}`, ...data });
}

// ------------------------------------------------------------------
// Branding
// ------------------------------------------------------------------
document.documentElement.style.setProperty('--accent', CONFIG.accent);
{
  const n = parseInt(CONFIG.accent.slice(1), 16);
  const lum = (0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
  document.documentElement.style.setProperty('--accent-ink', lum > 0.6 ? '#0f1400' : '#ffffff');
}
const bind = (key, value) => $$(`[data-bind="${key}"]`).forEach((el) => (el.textContent = value));
bind('shopName', CONFIG.shopName);
bind('tagline', CONFIG.tagline);
document.title = `${CONFIG.shopName} · Kit Studio`;
$('#nameInput').maxLength = 16;

// ------------------------------------------------------------------
// Pricing
// ------------------------------------------------------------------
function priceLines() {
  const kit = kitById(state.kitId);
  const p = CONFIG.prices;
  const kids = state.sizeGroup === 'kids';
  const mult = kids ? p.kidsMultiplier : 1;
  const lines = [{ label: `${clubOf(kit).name} ${kit.type} shirt`, sub: `${kids ? 'Kids' : 'Adult'} ${state.size}`, value: kit.price * mult }];
  if (state.name.trim()) lines.push({ label: 'Name printing', sub: state.name.trim().toUpperCase(), value: p.name });
  if (state.number !== '') lines.push({ label: 'Number printing', sub: `#${state.number}`, value: p.number });
  if (state.patch !== 'none') lines.push({ label: 'Sleeve patches', sub: PATCHES[state.patch].label, value: p.patch });
  if (state.shorts) lines.push({ label: 'Matching shorts', value: p.shorts * mult });
  if (state.shorts && state.shortsNumber && state.number !== '') lines.push({ label: 'Shorts number', value: p.shortsNumber });
  return lines;
}
const unitPrice = () => priceLines().reduce((a, l) => a + l.value, 0);
const totalPrice = () => unitPrice() * state.qty;

function renderPrice() {
  const ul = $('#priceLines');
  ul.innerHTML = '';
  for (const l of priceLines()) {
    const li = document.createElement('li');
    const a = document.createElement('span');
    a.textContent = l.label;
    if (l.sub) { const s = document.createElement('small'); s.textContent = l.sub; a.appendChild(s); }
    const b = document.createElement('span');
    b.textContent = money(l.value);
    li.append(a, b);
    ul.appendChild(li);
  }
  bind('total', money(totalPrice()));
  $('#qty').textContent = state.qty;
  bind('shortsPrice', `+${money(CONFIG.prices.shorts * (state.sizeGroup === 'kids' ? CONFIG.prices.kidsMultiplier : 1))}`);
  bind('shortsNumberPrice', `+${money(CONFIG.prices.shortsNumber)}`);
}

// ------------------------------------------------------------------
// 3D studio
// ------------------------------------------------------------------
const isMobile = matchMedia('(pointer: coarse)').matches;
const studio = new Studio($('#viewer'));
let model = null;

// Demo build for the hosted preview: no printing, downloads or long hash links there.
const DEMO_HOST = import.meta.env.VITE_ARTIFACT === '1';

function syncUrl() {
  if (DEMO_HOST) return;
  const h = encodeDesign(state);
  history.replaceState(null, '', `${location.pathname}${location.search}#${h}`);
}

let lastPrintInfo = null;
function applyDesign() {
  if (!model) return;
  lastPrintInfo = model.setDesign({
    name: state.name,
    number: state.number,
    font: state.font,
    arched: state.arched,
    sizeGroup: state.sizeGroup,
    shortsNumber: state.shorts && state.shortsNumber,
  });
}

async function applyKit() {
  const kit = kitById(state.kitId);
  const club = clubOf(kit);
  bind('club', club.name);
  bind('kitType', `${kit.type} kit · ${money(kit.price)}`);
  $('.kit-crest').src = crestImage(club, 48).toDataURL();
  if (model) {
    model.setKit(kit, state.patch);
    applyDesign();
  }
  // players
  const sel = $('#playerSelect');
  sel.innerHTML = '<option value="">Your own name &amp; number</option>';
  for (const [n, num] of club.players) {
    const o = document.createElement('option');
    o.value = `${n}|${num}`;
    o.textContent = `${num} · ${n}`;
    sel.appendChild(o);
  }
  $$('.kit-card').forEach((c) => c.classList.toggle('active', c.dataset.kit === state.kitId));
}

// ------------------------------------------------------------------
// Kit picker
// ------------------------------------------------------------------
function buildKitGrid() {
  const grid = $('#kitGrid');
  const atlas = document.createElement('canvas');
  for (const kit of KITS) {
    paintShirtAtlas(atlas, kit, {});
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'kit-card';
    b.dataset.kit = kit.id;
    b.dataset.type = kit.type;
    b.setAttribute('aria-label', `${clubOf(kit).name} ${kit.type}`);
    const thumb = kitThumbnail(kit, atlas, 144);
    const club = document.createElement('span');
    club.className = 'k-club';
    club.textContent = clubOf(kit).name;
    const type = document.createElement('span');
    type.className = 'k-type';
    type.textContent = kit.type;
    b.append(thumb, club, type);
    b.addEventListener('click', () => {
      if (state.kitId === kit.id) return;
      state.kitId = kit.id;
      applyKit();
      studio.turntable.applyView('front');
      setActiveView('front');
      update();
      track('kit_selected', { kit: kit.id });
    });
    grid.appendChild(b);
  }
  $$('#kitFilter .chip').forEach((chip) =>
    chip.addEventListener('click', () => {
      $$('#kitFilter .chip').forEach((c) => c.classList.toggle('active', c === chip));
      const f = chip.dataset.filter;
      $$('.kit-card').forEach((c) => (c.hidden = f !== 'all' && c.dataset.type !== f));
    }),
  );
}

// ------------------------------------------------------------------
// Name & number
// ------------------------------------------------------------------
const NAME_RE = /[^A-Za-zÀ-ɏ؀-ۿ .'\-]/g;
function validateName(raw) {
  let v = raw.replace(NAME_RE, '');
  const max = isArabic(v) ? CONFIG.nameMaxLength + 4 : CONFIG.nameMaxLength;
  let msg = '';
  if (raw !== v) msg = 'Letters, spaces, hyphens and apostrophes only.';
  if (v.length > max) { v = v.slice(0, max); msg = `Maximum ${max} characters.`; }
  const low = v.toLowerCase().replace(/[^a-z]/g, '');
  if (CONFIG.blockedWords.some((w) => low.includes(w))) return { value: v, error: 'Sorry, we can’t print that name.' };
  return { value: v, msg };
}

function setMsg(text, error = false) {
  const el = $('#printMsg');
  el.textContent = text || '';
  el.classList.toggle('error', !!error);
}

function bindPrintInputs() {
  const name = $('#nameInput');
  const num = $('#numberInput');
  const focusBack = () => {
    studio.turntable.applyView('name');
    studio.turntable.hold();
    setActiveView('back');
    hideHint();
  };
  name.addEventListener('focus', focusBack);
  num.addEventListener('focus', focusBack);
  name.addEventListener('pointerdown', focusBack);
  num.addEventListener('pointerdown', focusBack);
  const blur = () => studio.turntable.release();
  name.addEventListener('blur', blur);
  num.addEventListener('blur', blur);

  name.addEventListener('input', () => {
    const r = validateName(name.value);
    if (name.value !== r.value) {
      const pos = name.selectionStart - (name.value.length - r.value.length);
      name.value = r.value;
      name.setSelectionRange(Math.max(pos, 0), Math.max(pos, 0));
    }
    name.classList.toggle('invalid', !!r.error);
    setMsg(r.error || r.msg, !!r.error);
    state.name = r.error ? '' : r.value;
    $('#playerSelect').value = '';
    update();
  });
  num.addEventListener('input', () => {
    const v = num.value.replace(/\D/g, '').slice(0, 2);
    num.value = v;
    state.number = v;
    $('#playerSelect').value = '';
    update();
  });
  $('#playerSelect').addEventListener('change', (e) => {
    if (!e.target.value) return;
    const [n, no] = e.target.value.split('|');
    state.name = n;
    state.number = no;
    name.value = n;
    num.value = no;
    setMsg('');
    name.classList.remove('invalid');
    studio.turntable.applyView('name');
    setActiveView('back');
    update();
    track('player_selected', { player: n });
  });
  $('#arched').addEventListener('change', (e) => {
    state.arched = e.target.checked;
    studio.turntable.applyView('name');
    setActiveView('back');
    update();
  });
}

function buildFontGrid() {
  const grid = $('#fontGrid');
  for (const style of FONT_STYLES) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'font-opt';
    b.dataset.font = style.id;
    const c = document.createElement('canvas');
    c.width = 112; c.height = 68;
    const label = document.createElement('span');
    label.textContent = style.label;
    b.append(c, label);
    b.addEventListener('click', () => {
      state.font = style.id;
      studio.turntable.applyView('name');
      setActiveView('back');
      update();
      track('font_selected', { font: style.id });
    });
    grid.appendChild(b);
  }
  drawFontSamples();
}
function drawFontSamples() {
  for (const b of $$('.font-opt')) {
    const style = FONT_STYLES.find((s) => s.id === b.dataset.font);
    const c = b.querySelector('canvas');
    const ctx = c.getContext('2d');
    ctx.clearRect(0, 0, c.width, c.height);
    ctx.fillStyle = '#eef1f5';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = fontCss(style, 52, false);
    ctx.fillText('10', c.width / 2, c.height / 2 + 3);
    b.classList.toggle('active', state.font === style.id);
  }
}

// ------------------------------------------------------------------
// Size & extras
// ------------------------------------------------------------------
function buildSizes() {
  const grid = $('#sizeGrid');
  grid.innerHTML = '';
  for (const s of CONFIG.sizes[state.sizeGroup]) {
    const b = document.createElement('button');
    b.type = 'button';
    b.textContent = s;
    b.classList.toggle('active', s === state.size);
    b.addEventListener('click', () => {
      state.size = s;
      $$('#sizeGrid button').forEach((x) => x.classList.toggle('active', x === b));
      model?.setLabel(CONFIG.shopName, s);
      update();
    });
    grid.appendChild(b);
  }
  $$('#sizeGroup button').forEach((b) => b.classList.toggle('active', b.dataset.group === state.sizeGroup));
}

function bindExtras() {
  $$('#sizeGroup button').forEach((b) =>
    b.addEventListener('click', () => {
      if (state.sizeGroup === b.dataset.group) return;
      state.sizeGroup = b.dataset.group;
      const list = CONFIG.sizes[state.sizeGroup];
      state.size = list[Math.min(1, list.length - 1)];
      buildSizes();
      model?.setLabel(CONFIG.shopName, state.size);
      update();
    }),
  );
  $$('#patchSeg button').forEach((b) =>
    b.addEventListener('click', () => {
      state.patch = b.dataset.patch;
      $$('#patchSeg button').forEach((x) => x.classList.toggle('active', x === b));
      model?.setKit(kitById(state.kitId), state.patch);
      applyDesign();
      if (state.patch !== 'none') { studio.turntable.applyView('sleeve'); setActiveView(null); }
      update();
    }),
  );
  $('#addShorts').addEventListener('change', (e) => {
    state.shorts = e.target.checked;
    if (!state.shorts) state.shortsNumber = false;
    syncExtras();
    if (state.shorts) { studio.turntable.applyView('shorts'); setActiveView(null); }
    update();
  });
  $('#shortsNumber').addEventListener('change', (e) => {
    state.shortsNumber = e.target.checked;
    if (state.shortsNumber) state.shorts = true;
    syncExtras();
    studio.turntable.applyView('shorts');
    setActiveView(null);
    update();
  });
  $('#sizeGuideBtn').addEventListener('click', () => {
    const rows = (g) => CONFIG.sizeGuide[g].map((r) => `<tr><td><strong>${r[0]}</strong></td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('');
    $('#sizeTable').innerHTML = `
      <table><thead><tr><th>${state.sizeGroup === 'kids' ? 'Age' : 'Size'}</th><th>${state.sizeGroup === 'kids' ? 'Height (cm)' : 'Chest (cm)'}</th><th>Shirt length (cm)</th></tr></thead>
      <tbody>${rows(state.sizeGroup)}</tbody></table>`;
    $('#sizeDialog').showModal();
  });
  $('#qtyMinus').addEventListener('click', () => { state.qty = Math.max(1, state.qty - 1); update(); });
  $('#qtyPlus').addEventListener('click', () => { state.qty = Math.min(50, state.qty + 1); update(); });
}

function syncExtras() {
  $('#addShorts').checked = state.shorts;
  $('#shortsNumber').checked = state.shortsNumber;
  $$('#patchSeg button').forEach((x) => x.classList.toggle('active', x.dataset.patch === state.patch));
  $('#arched').checked = state.arched;
}

// ------------------------------------------------------------------
// Views
// ------------------------------------------------------------------
function setActiveView(v) {
  $$('.views button').forEach((b) => b.classList.toggle('active', b.dataset.view === v));
}
function hideHint() { $('#hint').classList.add('hidden'); }
$$('.views button').forEach((b) =>
  b.addEventListener('click', () => {
    const v = b.dataset.view;
    if (v === 'spin') {
      studio.turntable.applyView('front', true);
      studio.turntable.autoRotate = true;
      studio.turntable.release();
      studio.turntable.lastInput = performance.now();
    } else studio.turntable.applyView(v);
    setActiveView(v);
  }),
);
studio.turntable.on((type) => {
  if (type === 'interact') { hideHint(); setActiveView(null); }
});

// ------------------------------------------------------------------
// Order actions
// ------------------------------------------------------------------
function designSummary() {
  const kit = kitById(state.kitId);
  return {
    kit: kit.id,
    club: clubOf(kit).name,
    kitType: kit.type,
    name: state.name.trim() ? (isArabic(state.name) ? state.name.trim() : state.name.trim().toUpperCase()) : '',
    number: state.number,
    lettering: FONT_STYLES.find((f) => f.id === state.font).label,
    arched: state.arched,
    sizeGroup: state.sizeGroup,
    size: state.size,
    patch: state.patch,
    shorts: state.shorts,
    shortsNumber: state.shorts && state.shortsNumber,
    qty: state.qty,
    unitPrice: unitPrice(),
    total: totalPrice(),
    currency: CONFIG.currency,
    link: location.href,
  };
}

function stageBackground(ctx, w, h) {
  const g = ctx.createRadialGradient(w / 2, h * 0.42, 0, w / 2, h * 0.42, h * 0.7);
  g.addColorStop(0, '#323843');
  g.addColorStop(1, '#14161a');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, h);
}

function addToBasket() {
  const d = designSummary();
  track('add_to_basket', { kit: d.kit, value: d.total, currency: d.currency });
  window.parent?.postMessage({ type: 'kitstudio:add-to-basket', design: d }, '*');
  window.dispatchEvent(new CustomEvent('kitstudio:add-to-basket', { detail: d }));
  const img = studio.snapshot({ width: 440, height: 540, view: d.name || d.number ? 'back' : 'front', background: stageBackground });
  const rows = [
    ['Kit', `${d.club} ${d.kitType}`],
    ['Size', `${d.sizeGroup === 'kids' ? 'Kids' : 'Adult'} ${d.size}`],
    ['Name', d.name || '—'],
    ['Number', d.number || '—'],
    ['Qty', d.qty],
    ['Total', money(d.total)],
  ];
  $('#basketBody').innerHTML = `<img src="${img}" alt="Your kit" /><dl>${rows.map(([k, v]) => `<dt>${k}</dt><dd></dd>`).join('')}</dl>`;
  $$('#basketBody dd').forEach((dd, i) => (dd.textContent = rows[i][1]));
  $('#basketDialog').showModal();
}
$('#addToBasket').addEventListener('click', addToBasket);
$('#addToBasketMobile').addEventListener('click', addToBasket);
$('#basketDialog').addEventListener('close', () => {
  if ($('#basketDialog').returnValue === 'checkout') {
    if (CONFIG.checkoutUrl) location.href = CONFIG.checkoutUrl;
    else toast('Demo mode: checkout is connected when installed on the shop’s website.');
  }
});

$('#sheetBtn').addEventListener('click', async () => {
  track('job_sheet_opened');
  const front = studio.snapshot({ width: 600, height: 760, view: 'front' });
  const back = studio.snapshot({ width: 600, height: 760, view: 'back' });
  await openJobSheet({ demoHost: DEMO_HOST, design: designSummary(), kit: kitById(state.kitId), info: lastPrintInfo, front, back, artwork: model.canvases.back, config: CONFIG });
});
$('#shareBtn').addEventListener('click', async () => {
  syncUrl();
  if (DEMO_HOST) { toast('Share links are switched on when installed on the shop’s website.'); return; }
  const r = await shareDesign(location.href, `${CONFIG.shopName}: my custom kit`);
  if (r === 'copied') toast('Link copied. Anyone with it sees this exact design.');
  track('design_shared');
});
$('#imageBtn').addEventListener('click', async () => {
  const kit = kitById(state.kitId);
  const d = designSummary();
  const back = studio.snapshot({ width: 900, height: 1100, view: 'back' });
  const front = studio.snapshot({ width: 900, height: 1100, view: 'front' });
  const opts = { front, back, title: d.name ? `${d.name} ${d.number}` : `${d.club}`, subtitle: `${d.club} · ${kit.type}`, shop: CONFIG.shopName, accent: CONFIG.accent, previewOnly: DEMO_HOST };
  const url = await saveImage(opts);
  if (url) { $('#imagePreview').src = url; $('#imageDialog').showModal(); }
  track('image_saved');
});

let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}

// ------------------------------------------------------------------
// Update cycle
// ------------------------------------------------------------------
let urlTimer;
function update() {
  applyDesign();
  drawFontSamples();
  $('#nameCounter').textContent = `${[...state.name].length}/${isArabic(state.name) ? CONFIG.nameMaxLength + 4 : CONFIG.nameMaxLength}`;
  $('#shortsNumber').disabled = false;
  renderPrice();
  clearTimeout(urlTimer);
  urlTimer = setTimeout(syncUrl, 400);
}

// ------------------------------------------------------------------
// Boot
// ------------------------------------------------------------------
async function boot() {
  buildKitGrid();
  buildFontGrid();
  buildSizes();
  bindPrintInputs();
  bindExtras();
  syncExtras();
  $('#nameInput').value = state.name;
  $('#numberInput').value = state.number;
  await applyKit();
  renderPrice();

  const fontsReady = Promise.all([
    document.fonts.load('400 40px Graduate'),
    document.fonts.load('600 40px Oswald'),
    document.fonts.load('800 40px "Saira Condensed"'),
    document.fonts.load('400 40px "Russo One"'),
    document.fonts.load('700 40px "Barlow Condensed"'),
    document.fonts.load('600 40px "Barlow Condensed"'),
    document.fonts.load('700 40px Changa', 'ك'),
  ]).catch(() => {});
  const geos = await loadGarments(`${import.meta.env.BASE_URL}${DEMO_HOST ? 'garments.b64.txt' : 'garments.bin'}`, (p) => ($('#loaderBar').style.width = `${Math.round(p * 100)}%`));
  await fontsReady;
  // Repaint thumbnails & samples once fonts have arrived.
  $('#kitGrid').innerHTML = '';
  buildKitGrid();
  drawFontSamples();

  model = new KitModel(studio, geos, { decalPxPerM: isMobile ? 2200 : 3200 });
  model.setLabel(CONFIG.shopName, state.size);
  await applyKit();
  update();
  studio.turntable.applyView(state.name || state.number ? 'back' : 'front', true);
  studio.turntable.yaw += 0.6;
  studio.turntable.autoRotate = true;
  requestAnimationFrame(() => $('#loader').classList.add('done'));
  track('loaded');
}

boot().catch((err) => {
  console.error(err);
  $('.loader-text').textContent = 'Sorry, 3D preview could not start on this device.';
});
