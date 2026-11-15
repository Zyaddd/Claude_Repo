// Tiny JSON-file database. Fine for thousands of leads on one small server.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const DATA_DIR = process.env.DATA_DIR || path.resolve('data');
const FILE = path.join(DATA_DIR, 'db.json');

let db = { leads: {}, hunts: {} };
let saveTimer = null;

export function load() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  if (fs.existsSync(FILE)) {
    db = JSON.parse(fs.readFileSync(FILE, 'utf8'));
    db.leads ||= {};
    db.hunts ||= {};
    // Hunts interrupted by a restart can't resume.
    for (const h of Object.values(db.hunts)) {
      if (h.status === 'running' || h.status === 'queued') {
        h.status = 'failed';
        h.error = 'Server restarted while this hunt was running';
      }
    }
  }
}

function flush() {
  saveTimer = null;
  const tmp = FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(db));
  fs.renameSync(tmp, FILE);
}

export function save() {
  if (!saveTimer) saveTimer = setTimeout(flush, 300);
}

export function saveNow() {
  if (saveTimer) clearTimeout(saveTimer);
  flush();
}

export const newId = () => crypto.randomBytes(6).toString('hex');

export function domainOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '').toLowerCase();
  } catch {
    return '';
  }
}

const digits = (s) => String(s || '').replace(/\D/g, '');

function dedupeKey(lead) {
  // Big platforms (Facebook pages, Linktree...) are shared by many businesses, so they don't identify one.
  const d = domainOf(lead.website);
  if (d && !/facebook\.com|instagram\.com|linktr\.ee|google\.com|wa\.me/.test(d)) {
    // Keep the first path segment so e.g. example.com/branch-a and /branch-b stay separate.
    let seg = '';
    try { seg = new URL(lead.website).pathname.split('/')[1]?.toLowerCase() || ''; } catch { /* ignore */ }
    return 'd:' + d + (seg && !seg.includes('.') ? '/' + seg : '');
  }
  const p = digits(lead.phones?.[0]).slice(-9);
  if (p.length >= 7) return 'p:' + p;
  return 'n:' + String(lead.name || '').toLowerCase().replace(/\W+/g, '') + '|' + String(lead.address || '').toLowerCase().slice(0, 20);
}

const uniq = (arr) => [...new Set((arr || []).filter(Boolean))];
// The same number often appears as "+966 12 555 0100" and "0125550100"; keep the first spelling.
function uniqPhones(arr) {
  const seen = new Set();
  return (arr || []).filter((p) => {
    const k = digits(p).slice(-9);
    if (!k || seen.has(k)) return false;
    seen.add(k);
    return true;
  });
}

/** Insert or merge a lead. Returns [lead, isNew]. */
export function upsertLead(input) {
  const key = dedupeKey(input);
  const existing = Object.values(db.leads).find((l) => l.key === key);
  const now = new Date().toISOString();
  if (existing) {
    existing.phones = uniqPhones([...existing.phones, ...(input.phones || [])]);
    existing.emails = uniq([...existing.emails, ...(input.emails || [])]);
    existing.website ||= input.website || '';
    existing.socials = { ...(input.socials || {}), ...existing.socials };
    for (const f of ['address', 'category', 'rating', 'reviews', 'mapsUrl', 'lat', 'lon']) {
      if (existing[f] == null || existing[f] === '') existing[f] = input[f] ?? existing[f];
    }
    existing.huntIds = uniq([...(existing.huntIds || []), input.huntId]);
    existing.updatedAt = now;
    save();
    return [existing, false];
  }
  const lead = {
    id: newId(),
    key,
    name: input.name || 'Unknown business',
    category: input.category || '',
    address: input.address || '',
    city: input.city || '',
    lat: input.lat ?? null,
    lon: input.lon ?? null,
    phones: uniqPhones(input.phones),
    emails: uniq(input.emails),
    website: input.website || '',
    socials: input.socials || {},
    rating: input.rating ?? null,
    reviews: input.reviews ?? null,
    mapsUrl: input.mapsUrl || '',
    source: input.source || '',
    huntIds: uniq([input.huntId]),
    status: 'new',
    notes: '',
    enrichment: null,
    ai: null,
    createdAt: now,
    updatedAt: now,
  };
  db.leads[lead.id] = lead;
  save();
  return [lead, true];
}

export function mergeContacts(lead, { phones, emails, socials }) {
  lead.phones = uniqPhones([...lead.phones, ...(phones || [])]);
  lead.emails = uniq([...lead.emails, ...(emails || [])]);
  lead.socials = { ...lead.socials, ...(socials || {}) };
  lead.updatedAt = new Date().toISOString();
  save();
}

export const getLead = (id) => db.leads[id];
export const allLeads = () => Object.values(db.leads).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
export function deleteLead(id) {
  delete db.leads[id];
  save();
}

export const getHunt = (id) => db.hunts[id];
export const allHunts = () => Object.values(db.hunts).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
export function putHunt(h) {
  db.hunts[h.id] = h;
  save();
  return h;
}
export function deleteHunt(id) {
  delete db.hunts[id];
  save();
}
