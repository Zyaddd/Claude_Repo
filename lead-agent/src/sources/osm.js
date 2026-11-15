// OpenStreetMap (Nominatim geocoding + Overpass search). Free, no key; coverage varies by city.
// Usage policies: https://operations.osmfoundation.org/policies/nominatim/ — keep volumes low.
import { fetchWithTimeout } from '../http.js';

const UA = `LeadAgent/1.0${process.env.OSM_CONTACT ? ` (${process.env.OSM_CONTACT})` : ''}`;
const OVERPASS = process.env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter';

async function geocode(location) {
  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(location)}`;
  const res = await fetchWithTimeout(url, { headers: { 'User-Agent': UA, 'Accept-Language': 'en' } });
  if (!res.ok) throw new Error(`Nominatim error ${res.status}`);
  const [hit] = await res.json();
  if (!hit) throw new Error(`Could not find "${location}" on the map`);
  let [s, n, w, e] = hit.boundingbox.map(Number);
  // Cap the search box at ~40 km so a region/country name doesn't time out Overpass.
  const lat = Number(hit.lat), lon = Number(hit.lon), d = 0.18;
  s = Math.max(s, lat - d); n = Math.min(n, lat + d);
  w = Math.max(w, lon - d / Math.cos((lat * Math.PI) / 180)); e = Math.min(e, lon + d / Math.cos((lat * Math.PI) / 180));
  return { bbox: [s, w, n, e], name: hit.display_name };
}

const clean = (v) => (v ? String(v).split(';').map((x) => x.trim()).filter(Boolean) : []);

function socialsFromTags(t) {
  const s = {};
  const pick = (k, base) => {
    const v = t[`contact:${k}`] || t[k];
    if (!v) return;
    s[k] = /^https?:/.test(v) ? v : `${base}${v.replace(/^@/, '')}`;
  };
  pick('facebook', 'https://facebook.com/');
  pick('instagram', 'https://instagram.com/');
  pick('twitter', 'https://x.com/');
  pick('tiktok', 'https://tiktok.com/@');
  pick('linkedin', 'https://linkedin.com/company/');
  pick('youtube', 'https://youtube.com/');
  if (t['contact:whatsapp']) s.whatsapp = `https://wa.me/${t['contact:whatsapp'].replace(/\D/g, '')}`;
  return s;
}

export async function searchOsm({ category, keyword, location, limit, log, isCancelled }) {
  log(`OpenStreetMap: locating "${location}"`);
  const { bbox, name } = await geocode(location);
  log(`OpenStreetMap: searching around ${name.split(',').slice(0, 2).join(',')}`);
  if (isCancelled()) return [];
  const box = bbox.join(',');
  let filters;
  if (category?.osm?.length) {
    filters = category.osm.map((tag) => {
      const [k, v] = tag.split('=');
      return `nwr["${k}"="${v}"]["name"](${box});`;
    });
  } else {
    const kw = String(keyword || '').replace(/[^\p{L}\p{N} ]/gu, '').trim();
    if (!kw) throw new Error('Pick a category or type a keyword');
    filters = [`nwr["name"~"${kw}",i](${box});`];
  }
  const q = `[out:json][timeout:60];(${filters.join('')});out center tags ${Math.min(limit * 3, 500)};`;
  const res = await fetchWithTimeout(OVERPASS, {
    method: 'POST',
    headers: { 'User-Agent': UA, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'data=' + encodeURIComponent(q),
  }, 90000);
  if (!res.ok) throw new Error(`Overpass error ${res.status} (the public server may be busy; try again)`);
  const body = await res.json();
  const rows = (body.elements || []).map((el) => {
    const t = el.tags || {};
    const addr = [t['addr:housenumber'], t['addr:street'], t['addr:city']].filter(Boolean).join(' ');
    return {
      name: t.name,
      category: category?.label || keyword,
      address: addr,
      city: t['addr:city'] || '',
      phones: [...clean(t.phone), ...clean(t['contact:phone']), ...clean(t.mobile), ...clean(t['contact:mobile'])],
      emails: [...clean(t.email), ...clean(t['contact:email'])],
      website: t.website || t['contact:website'] || t.url || '',
      socials: socialsFromTags(t),
      lat: el.lat ?? el.center?.lat ?? null,
      lon: el.lon ?? el.center?.lon ?? null,
      mapsUrl: `https://www.openstreetmap.org/${el.type}/${el.id}`,
      source: 'osm',
    };
  });
  // Prefer places that already list a website or phone: they're reachable and enrich better.
  rows.sort((a, b) => (b.website ? 2 : 0) + (b.phones.length ? 1 : 0) - ((a.website ? 2 : 0) + (a.phones.length ? 1 : 0)));
  return rows.slice(0, limit);
}
