// HTTP server: serves the phone app (public/) and the JSON API that controls the agent.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import * as store from './src/store.js';
import * as agent from './src/agent.js';
import { CATEGORIES } from './src/categories.js';
import { googleEnabled } from './src/sources/google.js';
import { aiEnabled } from './src/qualify.js';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC = path.join(ROOT, 'public');
const PORT = Number(process.env.PORT) || 8080;

let PASSWORD = process.env.APP_PASSWORD;
if (!PASSWORD) {
  PASSWORD = crypto.randomBytes(9).toString('base64url');
  console.warn(`APP_PASSWORD is not set. Using a temporary password for this run: ${PASSWORD}`);
}
const SECRET = process.env.SESSION_SECRET || crypto.createHash('sha256').update('lead-agent:' + PASSWORD).digest('hex');
const TOKEN = crypto.createHmac('sha256', SECRET).update('session-v1').digest('base64url');

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
};

const safeEqual = (a, b) => {
  const x = Buffer.from(String(a)), y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
};

function send(res, status, data, headers = {}) {
  const body = typeof data === 'string' ? data : JSON.stringify(data);
  res.writeHead(status, {
    'Content-Type': typeof data === 'string' ? 'text/plain; charset=utf-8' : 'application/json',
    'Cache-Control': 'no-store',
    ...headers,
  });
  res.end(body);
}

async function readJson(req) {
  let body = '';
  for await (const chunk of req) {
    body += chunk;
    if (body.length > 100_000) throw Object.assign(new Error('Body too large'), { status: 413 });
  }
  if (!body) return {};
  try { return JSON.parse(body); } catch { throw Object.assign(new Error('Invalid JSON'), { status: 400 }); }
}

// Slow down password guessing.
const attempts = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const list = (attempts.get(ip) || []).filter((t) => now - t < 15 * 60_000);
  attempts.set(ip, list);
  return list.length >= 10;
}

/** Public view of a lead: drop internal fields and the bulky page text. */
function leadOut(l) {
  const { key, enrichment, ...rest } = l;
  if (!enrichment) return { ...rest, enrichment: null };
  const { textSample, pages, ...e } = enrichment;
  return { ...rest, enrichment: { ...e, pageCount: pages?.length || 0 } };
}

const csvCell = (v) => {
  let s = Array.isArray(v) ? v.join(' | ') : v == null ? '' : String(v);
  if (/^[=+\-@]/.test(s)) s = "'" + s; // stop spreadsheets running cell text as formulas
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

function leadsCsv(leads) {
  const socialKeys = ['facebook', 'instagram', 'linkedin', 'twitter', 'tiktok', 'youtube', 'snapchat', 'whatsapp'];
  const head = ['name', 'category', 'status', 'score', 'emails', 'phones', 'website', ...socialKeys, 'address', 'rating', 'reviews', 'maps', 'opener', 'opportunities', 'notes', 'added'];
  const rows = leads.map((l) => [
    l.name, l.category, l.status, l.ai?.score, l.emails, l.phones, l.website,
    ...socialKeys.map((k) => l.socials?.[k]), l.address, l.rating, l.reviews, l.mapsUrl,
    l.ai?.opener, l.ai?.opportunities, l.notes, l.createdAt,
  ]);
  return '﻿' + [head, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n');
}

function filterLeads(q) {
  let leads = store.allLeads();
  if (q.get('status')) leads = leads.filter((l) => l.status === q.get('status'));
  if (q.get('hunt')) leads = leads.filter((l) => l.huntIds?.includes(q.get('hunt')));
  if (q.get('has') === 'email') leads = leads.filter((l) => l.emails.length);
  if (q.get('has') === 'phone') leads = leads.filter((l) => l.phones.length);
  if (q.get('has') === 'social') leads = leads.filter((l) => Object.keys(l.socials).length);
  if (q.get('q')) {
    const needle = q.get('q').toLowerCase();
    leads = leads.filter((l) => [l.name, l.category, l.address, l.website, ...l.emails, l.notes].join(' ').toLowerCase().includes(needle));
  }
  if (q.get('sort') === 'score') leads.sort((a, b) => (b.ai?.score ?? -1) - (a.ai?.score ?? -1));
  return leads;
}

const STATUSES = ['new', 'contacted', 'replied', 'meeting', 'won', 'lost', 'skip'];

async function api(req, res, url) {
  const p = url.pathname;
  const m = req.method;

  if (p === '/api/login' && m === 'POST') {
    const ip = req.socket.remoteAddress;
    if (rateLimited(ip)) return send(res, 429, { error: 'Too many attempts. Try again in 15 minutes.' });
    const { password } = await readJson(req);
    if (!safeEqual(password || '', PASSWORD)) {
      attempts.get(ip).push(Date.now());
      return send(res, 401, { error: 'Wrong password' });
    }
    return send(res, 200, { token: TOKEN });
  }

  const auth = req.headers.authorization?.replace(/^Bearer /, '') || '';
  if (!safeEqual(auth, TOKEN)) return send(res, 401, { error: 'Please sign in' });

  if (p === '/api/config' && m === 'GET') {
    return send(res, 200, {
      categories: CATEGORIES.map(({ id, label }) => ({ id, label })),
      google: googleEnabled(),
      ai: aiEnabled(),
      statuses: STATUSES,
    });
  }

  if (p === '/api/stats' && m === 'GET') {
    const leads = store.allLeads();
    const byStatus = Object.fromEntries(STATUSES.map((s) => [s, 0]));
    leads.forEach((l) => byStatus[l.status]++);
    return send(res, 200, {
      total: leads.length,
      withEmail: leads.filter((l) => l.emails.length).length,
      withPhone: leads.filter((l) => l.phones.length).length,
      withSocial: leads.filter((l) => Object.keys(l.socials).length).length,
      hot: leads.filter((l) => (l.ai?.score ?? 0) >= 8).length,
      byStatus,
    });
  }

  if (p === '/api/hunts' && m === 'GET') {
    return send(res, 200, store.allHunts().slice(0, 50).map(({ leadIds, ...h }) => ({ ...h, log: h.log.slice(-1) })));
  }
  if (p === '/api/hunts' && m === 'POST') {
    const b = await readJson(req);
    try {
      return send(res, 201, agent.startHunt(b));
    } catch (err) {
      return send(res, 400, { error: err.message });
    }
  }
  let mm;
  if ((mm = p.match(/^\/api\/hunts\/(\w+)$/))) {
    const h = store.getHunt(mm[1]);
    if (!h) return send(res, 404, { error: 'Not found' });
    if (m === 'GET') return send(res, 200, h);
    if (m === 'DELETE') {
      if (h.status === 'running') return send(res, 409, { error: 'Stop the hunt first' });
      store.deleteHunt(h.id);
      return send(res, 200, { ok: true });
    }
  }
  if ((mm = p.match(/^\/api\/hunts\/(\w+)\/cancel$/)) && m === 'POST') {
    const h = agent.cancelHunt(mm[1]);
    return h ? send(res, 200, h) : send(res, 404, { error: 'Not found' });
  }

  if (p === '/api/leads' && m === 'GET') {
    return send(res, 200, filterLeads(url.searchParams).map(leadOut));
  }
  if (p === '/api/leads.csv' && m === 'GET') {
    return send(res, 200, leadsCsv(filterLeads(url.searchParams)), {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="leads-${new Date().toISOString().slice(0, 10)}.csv"`,
    });
  }
  if ((mm = p.match(/^\/api\/leads\/(\w+)$/))) {
    const lead = store.getLead(mm[1]);
    if (!lead) return send(res, 404, { error: 'Not found' });
    if (m === 'GET') return send(res, 200, leadOut(lead));
    if (m === 'PATCH') {
      const b = await readJson(req);
      if (b.status !== undefined) {
        if (!STATUSES.includes(b.status)) return send(res, 400, { error: 'Unknown status' });
        lead.status = b.status;
      }
      if (b.notes !== undefined) lead.notes = String(b.notes).slice(0, 5000);
      lead.updatedAt = new Date().toISOString();
      store.save();
      return send(res, 200, leadOut(lead));
    }
    if (m === 'DELETE') {
      store.deleteLead(lead.id);
      return send(res, 200, { ok: true });
    }
  }
  if ((mm = p.match(/^\/api\/leads\/(\w+)\/(enrich|score)$/)) && m === 'POST') {
    const lead = store.getLead(mm[1]);
    if (!lead) return send(res, 404, { error: 'Not found' });
    try {
      if (mm[2] === 'enrich') await agent.enrichLead(lead);
      else {
        if (!aiEnabled()) return send(res, 400, { error: 'AI scoring is off (set ANTHROPIC_API_KEY)' });
        await agent.scoreLead(lead);
      }
      return send(res, 200, leadOut(lead));
    } catch (err) {
      return send(res, 502, { error: err.message });
    }
  }

  return send(res, 404, { error: 'Not found' });
}

function serveStatic(req, res, url) {
  let rel = decodeURIComponent(url.pathname);
  if (rel === '/') rel = '/index.html';
  const file = path.normalize(path.join(PUBLIC, rel));
  if (!file.startsWith(PUBLIC + path.sep)) return send(res, 403, 'Forbidden');
  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) return send(res, 404, 'Not found');
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
      // The service worker handles offline caching; always revalidate so updates show up.
      'Cache-Control': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
    });
    fs.createReadStream(file).pipe(res);
  });
}

store.load();

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  try {
    if (url.pathname.startsWith('/api/')) await api(req, res, url);
    else if (req.method === 'GET' || req.method === 'HEAD') serveStatic(req, res, url);
    else send(res, 405, 'Method not allowed');
  } catch (err) {
    console.error(err);
    if (!res.headersSent) send(res, err.status || 500, { error: err.status ? err.message : 'Server error' });
  }
});

server.listen(PORT, () => {
  console.log(`Lead agent running on http://localhost:${PORT}`);
  console.log(`Sources: ${googleEnabled() ? 'Google Places + ' : ''}OpenStreetMap | AI scoring: ${aiEnabled() ? 'on' : 'off'}`);
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => { store.saveNow(); process.exit(0); });
}
