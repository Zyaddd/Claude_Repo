// Visits a business website (home page + a few contact/about pages) and pulls out
// emails, phone numbers, social profiles and signals useful for an AI-automation pitch.
import { fetchWithTimeout, assertPublicUrl, USER_AGENT } from './http.js';

const MAX_BYTES = 1_500_000;
const MAX_EXTRA_PAGES = 4;

const SOCIAL_PATTERNS = {
  facebook: /^(?:[\w-]+\.)?(?:facebook|fb)\.com$/,
  instagram: /^(?:www\.)?instagram\.com$/,
  linkedin: /^(?:[\w-]+\.)?linkedin\.com$/,
  twitter: /^(?:www\.|mobile\.)?(?:twitter|x)\.com$/,
  tiktok: /^(?:www\.)?tiktok\.com$/,
  youtube: /^(?:www\.|m\.)?youtube\.com$|^youtu\.be$/,
  snapchat: /^(?:www\.)?snapchat\.com$/,
  whatsapp: /^(?:wa\.me|(?:api|web|chat)\.whatsapp\.com)$/,
  threads: /^(?:www\.)?threads\.(?:net|com)$/,
  pinterest: /^(?:[\w-]+\.)?pinterest\.com$/,
  telegram: /^t\.me$/,
};
// Share buttons, plugins and generic pages aren't the business's own profile.
const SOCIAL_JUNK = /\/(sharer|share|intent|plugins|dialog|tr\b|login|signup|hashtag|explore|policies|legal|help|about\/privacy|privacy|terms|home\.php|watch\?|embed|status\/)|[?&]u=/i;

const CONTACT_LINK = /contact|kontakt|contacto|about|team|reach|location|impressum|appointment|book|اتصل|تواصل|من-نحن|عن/i;

const EMAIL_RE = /[a-z0-9][a-z0-9._%+-]{0,63}@(?:[a-z0-9-]+\.)+[a-z]{2,24}/gi;
const EMAIL_JUNK = /\.(png|jpe?g|gif|svg|webp|css|js)$|@(example|domain|email|sentry|wixpress|sentry-next|yoursite|company)\.|^(name|user|your|email|info@example)/i;

const CHAT_WIDGETS = {
  Intercom: /widget\.intercom\.io|intercomcdn/, Tawk: /embed\.tawk\.to/, Crisp: /client\.crisp\.chat/,
  Drift: /js\.driftt\.com/, LiveChat: /cdn\.livechatinc\.com/, Zendesk: /static\.zdassets\.com/,
  Tidio: /code\.tidio\.co/, HubSpot: /js\.hs-scripts\.com|js\.usemessages\.com/, ManyChat: /manychat/,
  'WhatsApp widget': /wa\.me\/|whatsapp-widget|elfsight.*whatsapp/,
};
const BOOKING_TOOLS = {
  Calendly: /calendly\.com/, Fresha: /fresha\.com/, Booksy: /booksy\.com/, Acuity: /acuityscheduling/,
  Setmore: /setmore\.com/, SimplyBook: /simplybook\./, Square: /squareup\.com\/appointments/,
  OpenTable: /opentable\./, Zocdoc: /zocdoc\.com/, Mindbody: /mindbodyonline/, Vagaro: /vagaro\.com/,
};

const decodeEntities = (s) => s
  .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
  .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)))
  .replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&nbsp;/g, ' ')
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>');

function stripHtml(html) {
  return decodeEntities(html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, ' ')
    .replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim();
}

/** Fetch one page, following redirects by hand so every hop is checked. */
async function getPage(url) {
  let current = url;
  for (let hop = 0; hop < 5; hop++) {
    await assertPublicUrl(current);
    const res = await fetchWithTimeout(current, {
      redirect: 'manual',
      headers: { 'User-Agent': USER_AGENT, Accept: 'text/html,application/xhtml+xml', 'Accept-Language': 'en,ar;q=0.8' },
    }, 12000);
    if (res.status >= 300 && res.status < 400 && res.headers.get('location')) {
      current = new URL(res.headers.get('location'), current).href;
      continue;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const type = res.headers.get('content-type') || '';
    if (!/html|xml|text\/plain/i.test(type)) throw new Error(`Not a web page (${type})`);
    // Read at most MAX_BYTES.
    const reader = res.body.getReader();
    const chunks = [];
    let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      size += value.length;
      if (size > MAX_BYTES) { await reader.cancel(); break; }
    }
    return { url: current, html: new TextDecoder().decode(Buffer.concat(chunks)) };
  }
  throw new Error('Too many redirects');
}

function hrefs(html, base) {
  const out = [];
  for (const m of html.matchAll(/<a\b[^>]*?href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    const raw = decodeEntities(m[1].trim());
    try {
      out.push({ href: raw, abs: new URL(raw, base), text: stripHtml(m[2]).slice(0, 80) });
    } catch { /* bad URL */ }
  }
  return out;
}

export function normalizePhone(p) {
  const s = decodeURIComponent(String(p)).replace(/^tel:|^callto:/i, '').replace(/[^\d+]/g, '');
  const d = s.replace(/\D/g, '');
  if (d.length < 7 || d.length > 15) return null;
  return s.startsWith('+') ? '+' + d : (s.startsWith('00') ? '+' + d.slice(2) : d);
}

function jsonLdContacts(html) {
  const phones = [], emails = [], sameAs = [];
  for (const m of html.matchAll(/<script[^>]+application\/ld\+json[^>]*>([\s\S]*?)<\/script>/gi)) {
    let data;
    try { data = JSON.parse(m[1].trim()); } catch { continue; }
    const walk = (o) => {
      if (!o || typeof o !== 'object') return;
      if (Array.isArray(o)) return o.forEach(walk);
      if (o.telephone) [].concat(o.telephone).forEach((t) => phones.push(t));
      if (o.email) [].concat(o.email).forEach((e) => emails.push(String(e).replace(/^mailto:/i, '')));
      if (o.sameAs) [].concat(o.sameAs).forEach((u) => sameAs.push(u));
      Object.values(o).forEach(walk);
    };
    walk(data);
  }
  return { phones, emails, sameAs };
}

/** Pull contacts out of one HTML page. Exported for tests. */
export function extract(html, pageUrl) {
  const emails = new Set(), phones = new Set(), socials = {};
  const links = hrefs(html, pageUrl);

  const addSocial = (u) => {
    let url;
    try { url = u instanceof URL ? u : new URL(u); } catch { return; }
    const host = url.hostname.toLowerCase();
    for (const [name, re] of Object.entries(SOCIAL_PATTERNS)) {
      if (!re.test(host)) continue;
      const path = url.pathname + url.search;
      if (name !== 'whatsapp' && (path === '/' || path === '' || SOCIAL_JUNK.test(path))) return;
      if (name === 'instagram' && /^\/(p|reel|reels|stories|tv)\//.test(url.pathname)) return;
      if (name === 'whatsapp' && !/\d{7,}|phone=\d|invite/.test(url.href)) return;
      if (!socials[name]) socials[name] = url.href.replace(/[?#].*$/, (q) => (name === 'whatsapp' ? q : ''));
      return;
    }
  };

  for (const { href, abs } of links) {
    if (/^mailto:/i.test(href)) {
      const e = decodeURIComponent(href.slice(7).split('?')[0]).trim();
      if (e) emails.add(e);
    } else if (/^(tel|callto):/i.test(href)) {
      const p = normalizePhone(href);
      if (p) phones.add(p);
    } else {
      addSocial(abs);
    }
  }

  const ld = jsonLdContacts(html);
  ld.emails.forEach((e) => emails.add(e));
  ld.phones.forEach((p) => { const n = normalizePhone(p); if (n) phones.add(n); });
  ld.sameAs.forEach(addSocial);

  // Visible text, including lightly obfuscated "name [at] domain [dot] com".
  const text = stripHtml(html)
    .replace(/\s*[[(]\s*at\s*[\])]\s*/gi, '@')
    .replace(/\s*[[(]\s*dot\s*[\])]\s*/gi, '.');
  for (const e of text.match(EMAIL_RE) || []) emails.add(e);
  // Also catch emails sitting in attributes / data (e.g. Cloudflare-free plain HTML).
  for (const e of decodeEntities(html).match(EMAIL_RE) || []) emails.add(e);
  // International-format numbers in text, e.g. "+966 12 345 6789". Local formats are too ambiguous.
  for (const m of text.matchAll(/(?<![\w+])\+\d[\d\s().-]{7,18}\d/g)) {
    const p = normalizePhone(m[0]);
    if (p) phones.add(p);
  }

  const cleanEmails = [...emails]
    .map((e) => e.toLowerCase().replace(/^[^a-z0-9]+|[^a-z0-9]+$/g, ''))
    .filter((e) => e.length < 80 && !EMAIL_JUNK.test(e));

  const title = (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || '').trim();
  const description = html.match(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']*)["']/i)?.[1] ||
    html.match(/<meta[^>]+content=["']([^"']*)["'][^>]+name=["']description["']/i)?.[1] || '';

  return {
    emails: [...new Set(cleanEmails)],
    phones: [...phones],
    socials,
    links,
    title: decodeEntities(title).slice(0, 200),
    description: decodeEntities(description).slice(0, 300),
    text,
    chatWidgets: Object.keys(CHAT_WIDGETS).filter((k) => CHAT_WIDGETS[k].test(html)),
    bookingTools: Object.keys(BOOKING_TOOLS).filter((k) => BOOKING_TOOLS[k].test(html)),
    hasForm: /<form\b/i.test(html),
  };
}

/** Crawl a site. Returns contacts plus signals; never throws (errors go in `.error`). */
export async function crawlSite(website) {
  const result = {
    emails: [], phones: [], socials: {}, pages: [], title: '', description: '', textSample: '',
    chatWidgets: [], bookingTools: [], hasForm: false, https: false, error: null,
    crawledAt: new Date().toISOString(),
  };
  if (!website) { result.error = 'No website'; return result; }
  let start;
  try {
    start = new URL(/^https?:\/\//i.test(website) ? website : `https://${website}`);
  } catch {
    result.error = 'Invalid website URL';
    return result;
  }
  // Pages hosted on social networks are profiles, not crawlable sites.
  const hostIsSocial = Object.values(SOCIAL_PATTERNS).some((re) => re.test(start.hostname));
  if (hostIsSocial) {
    const ex = extract(`<a href="${start.href}"></a>`, start.href);
    Object.assign(result.socials, ex.socials);
    result.error = 'Website is a social profile';
    return result;
  }

  const merge = (ex) => {
    ex.emails.forEach((e) => result.emails.includes(e) || result.emails.push(e));
    ex.phones.forEach((p) => result.phones.includes(p) || result.phones.push(p));
    for (const [k, v] of Object.entries(ex.socials)) result.socials[k] ||= v;
    ex.chatWidgets.forEach((w) => result.chatWidgets.includes(w) || result.chatWidgets.push(w));
    ex.bookingTools.forEach((w) => result.bookingTools.includes(w) || result.bookingTools.push(w));
    result.hasForm ||= ex.hasForm;
  };

  let home;
  try {
    home = await getPage(start.href);
  } catch (err) {
    if (start.protocol === 'https:') {
      try { home = await getPage(start.href.replace(/^https:/, 'http:')); } catch { /* fall through */ }
    }
    if (!home) { result.error = `Site unreachable: ${err.message}`; return result; }
  }
  const homeUrl = new URL(home.url);
  result.https = homeUrl.protocol === 'https:';
  result.pages.push(home.url);
  const ex = extract(home.html, home.url);
  merge(ex);
  result.title = ex.title;
  result.description = ex.description;
  result.textSample = ex.text.slice(0, 3000);

  // Follow a few same-site links that look like contact / about pages.
  const sameSite = (u) => u.hostname.replace(/^www\./, '') === homeUrl.hostname.replace(/^www\./, '');
  const candidates = [];
  for (const l of ex.links) {
    if (!/^https?:$/.test(l.abs.protocol) || !sameSite(l.abs)) continue;
    if (/\.(pdf|jpe?g|png|gif|zip|docx?)$/i.test(l.abs.pathname)) continue;
    if (!CONTACT_LINK.test(l.abs.pathname + ' ' + l.text)) continue;
    const u = l.abs.origin + l.abs.pathname;
    if (u !== home.url && !candidates.includes(u)) candidates.push(u);
  }
  // Rank real contact pages first.
  candidates.sort((a, b) => /contact|اتصل|تواصل/i.test(b) - /contact|اتصل|تواصل/i.test(a));
  for (const url of candidates.slice(0, MAX_EXTRA_PAGES)) {
    try {
      const page = await getPage(url);
      result.pages.push(page.url);
      const pex = extract(page.html, page.url);
      merge(pex);
      if (result.textSample.length < 5000) result.textSample += '\n' + pex.text.slice(0, 1500);
    } catch { /* skip broken pages */ }
  }

  // Emails on the business's own domain are the most useful; list them first.
  const domain = homeUrl.hostname.replace(/^www\./, '');
  result.emails.sort((a, b) => b.endsWith('@' + domain) - a.endsWith('@' + domain));
  return result;
}
