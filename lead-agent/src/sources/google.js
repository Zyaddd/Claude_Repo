// Google Places API (New) — Text Search. Best coverage worldwide; needs GOOGLE_PLACES_API_KEY.
// Each page costs one Text Search request; Google returns at most 60 results (3 pages) per query.
import { fetchWithTimeout } from '../http.js';

const FIELDS = [
  'places.id', 'places.displayName', 'places.formattedAddress', 'places.nationalPhoneNumber',
  'places.internationalPhoneNumber', 'places.websiteUri', 'places.rating', 'places.userRatingCount',
  'places.googleMapsUri', 'places.location', 'places.primaryTypeDisplayName', 'places.businessStatus',
  'nextPageToken',
].join(',');

export const googleEnabled = () => Boolean(process.env.GOOGLE_PLACES_API_KEY);

export async function searchGoogle({ query, location, limit, log, isCancelled }) {
  const out = [];
  let pageToken;
  const textQuery = `${query} in ${location}`;
  log(`Google Places: searching "${textQuery}"`);
  for (let page = 0; page < 3 && out.length < limit; page++) {
    if (isCancelled()) break;
    const res = await fetchWithTimeout(process.env.GOOGLE_PLACES_URL || 'https://places.googleapis.com/v1/places:searchText', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': process.env.GOOGLE_PLACES_API_KEY,
        'X-Goog-FieldMask': FIELDS,
      },
      body: JSON.stringify({ textQuery, pageSize: 20, ...(pageToken ? { pageToken } : {}) }),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`Google Places error ${res.status}: ${body?.error?.message || res.statusText}`);
    for (const p of body.places || []) {
      if (p.businessStatus && p.businessStatus !== 'OPERATIONAL') continue;
      out.push({
        name: p.displayName?.text,
        category: p.primaryTypeDisplayName?.text || query,
        address: p.formattedAddress,
        phones: [p.internationalPhoneNumber || p.nationalPhoneNumber].filter(Boolean),
        website: p.websiteUri || '',
        rating: p.rating ?? null,
        reviews: p.userRatingCount ?? null,
        mapsUrl: p.googleMapsUri || '',
        lat: p.location?.latitude ?? null,
        lon: p.location?.longitude ?? null,
        source: 'google',
      });
    }
    pageToken = body.nextPageToken;
    if (!pageToken) break;
    await new Promise((r) => setTimeout(r, 1500)); // next page token needs a moment to become valid
  }
  return out.slice(0, limit);
}
