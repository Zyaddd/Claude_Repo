// Lead Agent phone app. Plain JS, no build step.
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* private mode */ } },
};

let token = store.get('token');
let config = null;
let view = 'hunt';
let huntTimer = null;
const filters = { q: '', has: '', status: '', sort: 'new', hunt: '', huntLabel: '' };

async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...opts.headers },
    body: opts.body && typeof opts.body !== 'string' ? JSON.stringify(opts.body) : opts.body,
  });
  if (res.status === 401 && path !== '/api/login') { signOut(); throw new Error('Please sign in'); }
  const isJson = res.headers.get('content-type')?.includes('json');
  const data = isJson ? await res.json() : await res.text();
  if (!res.ok) throw new Error(data?.error || `Error ${res.status}`);
  return data;
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => t.classList.remove('show'), 2400);
}

/* ---------- Icons ---------- */
const ICON = {
  phone: '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/>',
  mail: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/>',
  web: '<circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/>',
  chat: '<path d="M21 11.5a8.4 8.4 0 0 1-12.4 7.4L3 21l2.1-5.6A8.4 8.4 0 1 1 21 11.5z"/>',
  map: '<path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
};
const icon = (n) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICON[n]}</svg>`;
const SOCIAL_LABEL = { facebook: 'Facebook', instagram: 'Instagram', linkedin: 'LinkedIn', twitter: 'X', tiktok: 'TikTok', youtube: 'YouTube', snapchat: 'Snapchat', whatsapp: 'WhatsApp', threads: 'Threads', pinterest: 'Pinterest', telegram: 'Telegram' };

const waLink = (phone, text = '') => `https://wa.me/${String(phone).replace(/\D/g, '')}${text ? `?text=${encodeURIComponent(text)}` : ''}`;
const safeHref = (u) => (/^https?:\/\//i.test(u) ? u : '#');

/* ---------- Auth ---------- */
function signOut() {
  token = null;
  store.set('token', null);
  clearTimeout(huntTimer);
  $('#app').hidden = true;
  $('#login').hidden = false;
}

$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#login-error').textContent = '';
  try {
    const { token: t } = await api('/api/login', { method: 'POST', body: { password: $('#password').value } });
    token = t;
    store.set('token', t);
    $('#password').value = '';
    start();
  } catch (err) {
    $('#login-error').textContent = err.message;
  }
});
$('#logout').addEventListener('click', signOut);

/* ---------- Navigation ---------- */
const TITLES = { hunt: 'Find leads', leads: 'Leads', more: 'More' };
function show(v) {
  view = v;
  $$('.view').forEach((s) => (s.hidden = s.id !== `view-${v}`));
  $$('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.view === v));
  $('#title').textContent = TITLES[v];
  window.scrollTo(0, 0);
  if (v === 'leads') loadLeads();
  if (v === 'hunt') loadHunts();
}
$$('.tabs button').forEach((b) => b.addEventListener('click', () => show(b.dataset.view)));

/* ---------- Hunt tab ---------- */
function fillForm() {
  $('#category').innerHTML = '<option value="">— Choose —</option>' +
    config.categories.map((c) => `<option value="${c.id}">${esc(c.label)}</option>`).join('');
  const sources = [['auto', 'Best available'], ...(config.google ? [['google', 'Google Maps']] : []), ['osm', 'OpenStreetMap']];
  $('#source').innerHTML = sources.map(([v, l]) => `<option value="${v}">${l}</option>`).join('');
  $('#ai-row').hidden = !config.ai;
  $('#status-filter').innerHTML = '<option value="">Any status</option>' +
    config.statuses.map((s) => `<option value="${s}">${cap(s)}</option>`).join('');
  $('#location').value = store.get('lastLocation') || '';
  $('#category').value = store.get('lastCategory') || '';

  $('#setup').innerHTML = [
    ['Business search', config.google ? '✅ Google Maps + OpenStreetMap' : '⚠️ OpenStreetMap only. Add GOOGLE_PLACES_API_KEY on the server for far better coverage.'],
    ['AI lead scoring', config.ai ? '✅ Claude scores each lead and drafts an opener' : '⚠️ Off. Add ANTHROPIC_API_KEY on the server to turn it on.'],
    ['Website crawler', '✅ Finds emails, phones and social pages on each site'],
  ].map(([k, v]) => `<li><b>${k}</b><span class="muted">${v}</span></li>`).join('');
}
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

$('#hunt-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#hunt-error').textContent = '';
  const btn = $('#hunt-btn');
  btn.disabled = true;
  try {
    const body = {
      categoryId: $('#category').value,
      keyword: $('#keyword').value,
      location: $('#location').value,
      limit: $('#limit').value,
      source: $('#source').value,
      useAi: $('#use-ai').checked,
    };
    store.set('lastLocation', body.location);
    store.set('lastCategory', body.categoryId);
    await api('/api/hunts', { method: 'POST', body });
    toast('Agent is on it');
    $('#keyword').value = '';
    loadHunts();
  } catch (err) {
    $('#hunt-error').textContent = err.message;
  } finally {
    btn.disabled = false;
  }
});

async function loadStats() {
  const s = await api('/api/stats');
  $('#stats').innerHTML = [['Leads', s.total], ['Emails', s.withEmail], ['Phones', s.withPhone], [config.ai ? 'Hot (8+)' : 'Social', config.ai ? s.hot : s.withSocial]]
    .map(([l, n]) => `<div class="stat"><b>${n}</b><span>${l}</span></div>`).join('');
}

function huntProgress(h) {
  if (h.status === 'done') return 100;
  if (h.status !== 'running' || !h.stats.found) return h.status === 'running' ? 5 : 0;
  const steps = h.useAi ? 2 : 1;
  return Math.round(10 + 90 * ((h.stats.crawled + h.stats.scored) / (h.stats.found * steps)));
}

async function loadHunts() {
  clearTimeout(huntTimer);
  if (!token) return;
  try {
    const [hunts] = await Promise.all([api('/api/hunts'), loadStats()]);
    const box = $('#hunts');
    if (!hunts.length) {
      box.innerHTML = '<div class="empty">No hunts yet. Pick a business type and a city above, then tap <b>Start hunting</b>.</div>';
    } else {
      box.innerHTML = hunts.map((h) => {
        const active = h.status === 'running' || h.status === 'queued';
        const last = h.log[h.log.length - 1]?.msg || '';
        return `<div class="hunt">
          <div class="hunt-head"><b>${esc(h.label)}</b><span class="pill ${h.status}">${h.status}</span></div>
          ${active ? `<div class="progress"><i style="width:${huntProgress(h)}%"></i></div>` : ''}
          <div class="hunt-meta">${esc(h.error || last)}</div>
          <div class="hunt-meta">${h.stats.found} found · ${h.stats.new} new · ${h.source === 'google' ? 'Google' : 'OSM'} · ${new Date(h.createdAt).toLocaleString()}</div>
          <div class="hunt-actions">
            ${h.stats.found ? `<button class="btn sm" data-open="${h.id}">View leads</button>` : ''}
            ${active ? `<button class="btn sm" data-cancel="${h.id}">Stop</button>` : `<button class="btn sm ghost" data-del="${h.id}">Remove</button>`}
          </div>
        </div>`;
      }).join('');
    }
    if (hunts.some((h) => h.status === 'running' || h.status === 'queued')) {
      huntTimer = setTimeout(() => view === 'hunt' && loadHunts(), 2500);
    }
  } catch (err) {
    toast(err.message);
  }
}

$('#hunts').addEventListener('click', async (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  if (b.dataset.open) {
    const h = b.closest('.hunt');
    filters.hunt = b.dataset.open;
    filters.huntLabel = $('b', h).textContent;
    show('leads');
  } else if (b.dataset.cancel) {
    await api(`/api/hunts/${b.dataset.cancel}/cancel`, { method: 'POST' }).catch((err) => toast(err.message));
    loadHunts();
  } else if (b.dataset.del) {
    await api(`/api/hunts/${b.dataset.del}`, { method: 'DELETE' }).catch((err) => toast(err.message));
    loadHunts();
  }
});

/* ---------- Leads tab ---------- */
let leadsCache = [];

function leadQuery() {
  const p = new URLSearchParams();
  for (const k of ['q', 'has', 'status', 'sort', 'hunt']) if (filters[k]) p.set(k, filters[k]);
  return p.toString();
}

function scoreClass(s) { return s >= 8 ? 'hot' : s >= 6 ? 'warm' : ''; }

function leadActions(l) {
  const a = [];
  const phone = l.phones[0];
  if (phone) a.push(`<a class="act" href="tel:${esc(phone)}">${icon('phone')}Call</a>`);
  const wa = l.socials.whatsapp || (phone ? waLink(phone) : '');
  if (wa) a.push(`<a class="act" href="${esc(safeHref(wa))}" target="_blank" rel="noopener">${icon('chat')}WhatsApp</a>`);
  if (l.emails[0]) a.push(`<a class="act" href="mailto:${esc(l.emails[0])}">${icon('mail')}Email</a>`);
  if (l.website) a.push(`<a class="act" href="${esc(safeHref(l.website))}" target="_blank" rel="noopener">${icon('web')}Site</a>`);
  for (const [k, u] of Object.entries(l.socials)) {
    if (k === 'whatsapp') continue;
    a.push(`<a class="act" href="${esc(safeHref(u))}" target="_blank" rel="noopener">${esc(SOCIAL_LABEL[k] || k)}</a>`);
  }
  return a.join('');
}

function renderLeads() {
  const box = $('#leads');
  $('#lead-count').textContent = `${leadsCache.length} lead${leadsCache.length === 1 ? '' : 's'}`;
  if (!leadsCache.length) {
    box.innerHTML = '<div class="empty">No leads match. Start a hunt from the Hunt tab.</div>';
    return;
  }
  box.innerHTML = leadsCache.map((l) => `<article class="lead" data-id="${l.id}">
    <div class="lead-head" data-detail="${l.id}">
      <div class="score ${scoreClass(l.ai?.score)}">${l.ai ? l.ai.score : '–'}</div>
      <div class="grow">
        <b>${esc(l.name)}</b>
        <div class="sub">${esc([l.category, l.address].filter(Boolean).join(' · '))}</div>
      </div>
      <span class="status-dot">${esc(l.status)}</span>
    </div>
    ${l.ai?.summary ? `<div class="small muted">${esc(l.ai.summary)}</div>` : ''}
    <div class="actions">${leadActions(l) || '<span class="small muted">No contact details found</span>'}</div>
  </article>`).join('');
}

async function loadLeads() {
  const hf = $('#hunt-filter');
  hf.hidden = !filters.hunt;
  hf.innerHTML = `<span>Hunt: ${esc(filters.huntLabel)}</span><button class="btn sm ghost" id="clear-hunt">Show all</button>`;
  try {
    leadsCache = await api(`/api/leads?${leadQuery()}`);
    renderLeads();
  } catch (err) {
    toast(err.message);
  }
}

$('#view-leads').addEventListener('click', (e) => {
  if (e.target.id === 'clear-hunt') { filters.hunt = ''; loadLeads(); return; }
  const chip = e.target.closest('.chip');
  if (chip) {
    $$('#filter-chips .chip').forEach((c) => c.classList.toggle('on', c === chip));
    filters.has = chip.dataset.has;
    loadLeads();
    return;
  }
  const head = e.target.closest('[data-detail]');
  if (head) openLead(head.dataset.detail);
});
let searchT;
$('#search').addEventListener('input', (e) => { clearTimeout(searchT); searchT = setTimeout(() => { filters.q = e.target.value.trim(); loadLeads(); }, 250); });
$('#status-filter').addEventListener('change', (e) => { filters.status = e.target.value; loadLeads(); });
$('#sort').addEventListener('change', (e) => { filters.sort = e.target.value; loadLeads(); });

/* ---------- Lead detail ---------- */
const dialog = $('#lead-dialog');

function list(items, render) {
  return items.length ? items.map(render).join('<br>') : '<span class="muted">None found</span>';
}

function renderDetail(l) {
  const e = l.enrichment;
  const ai = l.ai;
  const opener = ai?.opener || '';
  $('#lead-detail').innerHTML = `<div class="sheet-inner">
    <div class="sheet-top">
      <div><h2>${esc(l.name)}</h2><div class="small muted">${esc(l.category)}${l.rating ? ` · ★ ${l.rating} (${l.reviews ?? 0})` : ''}</div></div>
      <button class="close" data-close aria-label="Close">×</button>
    </div>
    <div class="actions">${leadActions(l)}</div>
    <div class="row">
      <label class="grow">Status
        <select id="d-status">${config.statuses.map((s) => `<option value="${s}" ${s === l.status ? 'selected' : ''}>${cap(s)}</option>`).join('')}</select>
      </label>
    </div>
    ${ai ? `<div class="card">
      <div class="row" style="align-items:center"><div class="score ${scoreClass(ai.score)}">${ai.score}</div><b class="grow">Claude's take</b></div>
      <p>${esc(ai.summary)}</p>
      <div class="kv"><span>Automations to pitch</span><ul>${ai.opportunities.map((o) => `<li>${esc(o)}</li>`).join('')}</ul></div>
      <div class="kv"><span>Suggested opener</span><div class="opener" dir="auto">${esc(opener)}</div></div>
      <div class="actions">
        <button class="act" data-copy>Copy opener</button>
        ${l.phones[0] ? `<a class="act" href="${esc(waLink(l.phones[0], opener))}" target="_blank" rel="noopener">${icon('chat')}Send on WhatsApp</a>` : ''}
        ${l.emails[0] ? `<a class="act" href="mailto:${esc(l.emails[0])}?body=${encodeURIComponent(opener)}">${icon('mail')}Email it</a>` : ''}
      </div>
    </div>` : ''}
    <div class="kv"><span>Emails</span><div>${list(l.emails, (m) => `<a href="mailto:${esc(m)}">${esc(m)}</a>`)}</div></div>
    <div class="kv"><span>Phones</span><div>${list(l.phones, (p) => `<a href="tel:${esc(p)}">${esc(p)}</a>`)}</div></div>
    <div class="kv"><span>Website</span><div>${l.website ? `<a href="${esc(safeHref(l.website))}" target="_blank" rel="noopener">${esc(l.website)}</a>` : '<span class="muted">None listed</span>'}</div></div>
    <div class="kv"><span>Social pages</span><div>${list(Object.entries(l.socials), ([k, u]) => `${esc(SOCIAL_LABEL[k] || k)}: <a href="${esc(safeHref(u))}" target="_blank" rel="noopener">${esc(u.replace(/^https?:\/\/(www\.)?/, ''))}</a>`)}</div></div>
    ${l.address ? `<div class="kv"><span>Address</span><div>${esc(l.address)}${l.mapsUrl ? ` · <a href="${esc(safeHref(l.mapsUrl))}" target="_blank" rel="noopener">Map</a>` : ''}</div></div>` : ''}
    ${e ? `<div class="kv"><span>Website check</span><div class="tags">
      ${e.error ? `<span class="tag">${esc(e.error)}</span>` : `<span class="tag">${e.pageCount} page${e.pageCount === 1 ? '' : 's'} read</span>`}
      ${e.error ? '' : `<span class="tag">${e.https ? 'HTTPS' : 'No HTTPS'}</span>`}
      ${e.error ? '' : `<span class="tag">${e.chatWidgets.length ? 'Chat: ' + esc(e.chatWidgets.join(', ')) : 'No live chat'}</span>`}
      ${e.error ? '' : `<span class="tag">${e.bookingTools.length ? 'Booking: ' + esc(e.bookingTools.join(', ')) : 'No online booking'}</span>`}
    </div></div>` : ''}
    <label>Notes<textarea id="d-notes" placeholder="Call notes, who you spoke to, next step…">${esc(l.notes)}</textarea></label>
    <div class="actions">
      ${l.website ? '<button class="btn sm" data-enrich>Re-check website</button>' : ''}
      ${config.ai ? `<button class="btn sm" data-score>${ai ? 'Re-score' : 'Score with Claude'}</button>` : ''}
      <button class="btn sm ghost danger" data-delete>Delete lead</button>
    </div>
  </div>`;
}

async function openLead(id) {
  const l = leadsCache.find((x) => x.id === id) || await api(`/api/leads/${id}`);
  renderDetail(l);
  dialog.dataset.id = id;
  dialog.showModal();
}

function replaceLead(l) {
  const i = leadsCache.findIndex((x) => x.id === l.id);
  if (i >= 0) leadsCache[i] = l;
  renderLeads();
}

dialog.addEventListener('click', async (e) => {
  if (e.target === dialog || e.target.closest('[data-close]')) { dialog.close(); return; }
  const id = dialog.dataset.id;
  const l = leadsCache.find((x) => x.id === id);
  const b = e.target.closest('button');
  if (!b) return;
  if (b.hasAttribute('data-copy')) {
    try { await navigator.clipboard.writeText(l.ai.opener); toast('Copied'); } catch { toast('Copy failed'); }
  } else if (b.hasAttribute('data-enrich') || b.hasAttribute('data-score')) {
    const what = b.hasAttribute('data-enrich') ? 'enrich' : 'score';
    b.disabled = true;
    b.textContent = what === 'enrich' ? 'Checking…' : 'Scoring…';
    try {
      const updated = await api(`/api/leads/${id}/${what}`, { method: 'POST' });
      replaceLead(updated);
      renderDetail(updated);
      toast('Updated');
    } catch (err) {
      toast(err.message);
      b.disabled = false;
    }
  } else if (b.hasAttribute('data-delete')) {
    if (!confirm(`Delete ${l.name}?`)) return;
    await api(`/api/leads/${id}`, { method: 'DELETE' });
    leadsCache = leadsCache.filter((x) => x.id !== id);
    renderLeads();
    dialog.close();
  }
});

dialog.addEventListener('change', async (e) => {
  if (e.target.id !== 'd-status') return;
  const updated = await api(`/api/leads/${dialog.dataset.id}`, { method: 'PATCH', body: { status: e.target.value } });
  replaceLead(updated);
  toast(`Marked ${updated.status}`);
});
dialog.addEventListener('focusout', async (e) => {
  if (e.target.id !== 'd-notes') return;
  const l = leadsCache.find((x) => x.id === dialog.dataset.id);
  if (!l || l.notes === e.target.value) return;
  const updated = await api(`/api/leads/${l.id}`, { method: 'PATCH', body: { notes: e.target.value } });
  replaceLead(updated);
  toast('Notes saved');
});

/* ---------- More tab ---------- */
$('#export').addEventListener('click', async () => {
  try {
    const res = await fetch(`/api/leads.csv?${leadQuery()}`, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) throw new Error('Export failed');
    const blob = await res.blob();
    const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: `leads-${new Date().toISOString().slice(0, 10)}.csv` });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  } catch (err) {
    toast(err.message);
  }
});

let installEvt;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installEvt = e; $('#install').hidden = false; });
$('#install').addEventListener('click', async () => { if (!installEvt) return; installEvt.prompt(); installEvt = null; $('#install').hidden = true; });

/* ---------- Boot ---------- */
async function start() {
  try {
    config = await api('/api/config');
  } catch {
    return signOut();
  }
  $('#login').hidden = true;
  $('#app').hidden = false;
  fillForm();
  show('hunt');
}

if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
document.addEventListener('visibilitychange', () => { if (!document.hidden && token && view === 'hunt') loadHunts(); });
token ? start() : signOut();
