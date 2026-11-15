// The lead-hunting agent: search listings -> visit each website -> (optionally) score with Claude.
import * as store from './store.js';
import { categoryById } from './categories.js';
import { searchGoogle, googleEnabled } from './sources/google.js';
import { searchOsm } from './sources/osm.js';
import { crawlSite } from './crawler.js';
import { qualifyLead, aiEnabled } from './qualify.js';

const cancelled = new Set();
const queue = [];
let running = false;

async function pool(items, size, fn) {
  let i = 0;
  await Promise.all(Array.from({ length: Math.min(size, items.length) }, async () => {
    while (i < items.length) await fn(items[i++]);
  }));
}

export async function enrichLead(lead) {
  const result = await crawlSite(lead.website);
  const { textSample, ...rest } = result;
  lead.enrichment = { ...rest, textSample: textSample.slice(0, 5000) };
  store.mergeContacts(lead, result);
  return lead;
}

export async function scoreLead(lead) {
  lead.ai = await qualifyLead(lead);
  store.save();
  return lead;
}

export function startHunt({ categoryId, keyword, location, limit, source, useAi }) {
  const category = categoryById(categoryId);
  if (!location?.trim()) throw new Error('Location is required');
  if (!category && !keyword?.trim()) throw new Error('Choose a category or type a keyword');
  const src = source === 'auto' || !source ? (googleEnabled() ? 'google' : 'osm') : source;
  if (src === 'google' && !googleEnabled()) throw new Error('Google Places is not configured (set GOOGLE_PLACES_API_KEY)');
  const hunt = store.putHunt({
    id: store.newId(),
    label: `${keyword?.trim() || category.label} in ${location.trim()}`,
    categoryId: category?.id || null,
    keyword: keyword?.trim() || '',
    location: location.trim(),
    limit: Math.max(1, Math.min(Number(limit) || 30, 200)),
    source: src,
    useAi: Boolean(useAi) && aiEnabled(),
    status: 'queued',
    stats: { found: 0, new: 0, crawled: 0, scored: 0, withEmail: 0, withPhone: 0, withSocial: 0 },
    log: [],
    leadIds: [],
    error: null,
    createdAt: new Date().toISOString(),
    finishedAt: null,
  });
  queue.push(hunt.id);
  drain();
  return hunt;
}

export function cancelHunt(id) {
  const h = store.getHunt(id);
  if (!h) return null;
  if (h.status === 'queued') {
    queue.splice(queue.indexOf(id), 1);
    h.status = 'cancelled';
  } else if (h.status === 'running') {
    cancelled.add(id);
  }
  store.save();
  return h;
}

async function drain() {
  if (running) return;
  running = true;
  while (queue.length) {
    const id = queue.shift();
    const hunt = store.getHunt(id);
    if (hunt) await runHunt(hunt);
  }
  running = false;
}

async function runHunt(hunt) {
  const log = (msg) => {
    hunt.log.push({ t: new Date().toISOString(), msg });
    if (hunt.log.length > 200) hunt.log.shift();
    store.save();
  };
  const isCancelled = () => cancelled.has(hunt.id);
  hunt.status = 'running';
  try {
    const category = categoryById(hunt.categoryId);
    const args = {
      query: hunt.keyword || category.query,
      category: hunt.keyword ? null : category,
      keyword: hunt.keyword,
      location: hunt.location,
      limit: hunt.limit,
      log,
      isCancelled,
    };
    const raw = hunt.source === 'google' ? await searchGoogle(args) : await searchOsm(args);
    log(`Found ${raw.length} businesses`);

    const leads = [];
    for (const r of raw) {
      if (!r.name) continue;
      const [lead, isNew] = store.upsertLead({ ...r, huntId: hunt.id });
      leads.push(lead);
      hunt.stats.found++;
      if (isNew) hunt.stats.new++;
    }
    hunt.leadIds = leads.map((l) => l.id);
    log(`${hunt.stats.new} new, ${hunt.stats.found - hunt.stats.new} already in your list`);

    log('Visiting websites for emails, phones and social pages…');
    await pool(leads, 5, async (lead) => {
      if (isCancelled()) return;
      if (lead.website) await enrichLead(lead);
      hunt.stats.crawled++;
      store.save();
    });

    if (hunt.useAi && !isCancelled()) {
      log('Scoring leads with Claude…');
      await pool(leads, 3, async (lead) => {
        if (isCancelled()) return;
        try {
          await scoreLead(lead);
        } catch (err) {
          log(`Couldn't score ${lead.name}: ${err.message}`);
        }
        hunt.stats.scored++;
        store.save();
      });
    }

    hunt.stats.withEmail = leads.filter((l) => l.emails.length).length;
    hunt.stats.withPhone = leads.filter((l) => l.phones.length).length;
    hunt.stats.withSocial = leads.filter((l) => Object.keys(l.socials).length).length;
    hunt.status = isCancelled() ? 'cancelled' : 'done';
    log(isCancelled() ? 'Stopped' :
      `Done: ${hunt.stats.withEmail} with email, ${hunt.stats.withPhone} with phone, ${hunt.stats.withSocial} with social pages`);
  } catch (err) {
    hunt.status = 'failed';
    hunt.error = err.message;
    log(`Failed: ${err.message}`);
  } finally {
    cancelled.delete(hunt.id);
    hunt.finishedAt = new Date().toISOString();
    store.save();
  }
}
