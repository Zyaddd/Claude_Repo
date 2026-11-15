# Lead Agent

The first agent of the agency: it finds local businesses that could use AI automation and collects
their **emails, phone numbers, websites and social media pages**. You control it from a small web app
that installs on your phone's home screen.

## What it does

1. **Search.** You pick a business type (dentists, salons, gyms, real estate…) or type a keyword, plus a city.
   The agent pulls listings from **Google Maps** (Places API) or, without a key, **OpenStreetMap**.
2. **Visit each website.** It reads the home page and up to 4 contact/about pages, and pulls out
   emails (including `name [at] domain [dot] com`), phone numbers (`tel:` links, schema.org data, `+` numbers),
   and social profiles (Facebook, Instagram, LinkedIn, X, TikTok, YouTube, Snapchat, WhatsApp, Telegram…).
   It also notes whether the site already has live chat or online booking: a business without them is a better prospect.
3. **Score (optional).** With an Anthropic API key, Claude rates each lead 1–10 for AI automation,
   lists 2–4 automations to pitch, and drafts a short opener in the website's language.
4. **Work the list.** In the app: call, WhatsApp, email or open socials with one tap, set a status
   (new → contacted → replied → meeting → won/lost), keep notes, export to CSV.

Leads are de-duplicated across hunts, so running "dentists in Jeddah" twice won't double your list.

## Run it locally

```bash
cd lead-agent
npm install
cp .env.example .env        # then fill in APP_PASSWORD and your keys
node --env-file=.env server.js
```

Open http://localhost:8080 and sign in with `APP_PASSWORD`.

## Put it on your phone

The app must be served over **HTTPS** for the phone to install it. Easiest options:

- **Render / Railway / Fly.io**: deploy this folder with the included `Dockerfile`, set the environment
  variables from `.env.example`, and attach a persistent disk/volume mounted at `/data` (leads are stored there).
- **Your own server**: `docker build -t lead-agent . && docker run -d -p 8080:8080 -v leads:/data --env-file .env lead-agent`,
  with Caddy or nginx in front for HTTPS.

Then on the phone open the URL and:
- **iPhone (Safari):** Share → *Add to Home Screen*
- **Android (Chrome):** ⋮ → *Install app*

## Keys and costs

| Setting | Needed? | Notes |
| --- | --- | --- |
| `APP_PASSWORD` | Yes | Without it a random password is printed in the server log on each start. |
| `GOOGLE_PLACES_API_KEY` | Recommended | Places API (New) "Text Search". Up to 60 results per search (3 requests). Google bills per request after the monthly free usage; check your Cloud console. |
| `ANTHROPIC_API_KEY` | Optional | One short Claude request per lead (Claude Opus 5.5, low effort). Turn it off per hunt with the checkbox. |

OpenStreetMap is free but coverage of phones/websites varies a lot by city (often thin in the Gulf);
its public servers ask for light use only.

## Using the leads responsibly

Contact details on a business's own public website or listing are generally fine to use for B2B outreach,
but rules differ by country (e.g. GDPR in the EU, PDPL in Saudi Arabia, CAN-SPAM in the US).
Always say who you are, keep messages relevant to the business, and honour opt-outs (mark them **Skip**).

## Project layout

```
server.js              HTTP server + JSON API + password sign-in
src/agent.js           runs a hunt: search -> crawl -> score
src/sources/google.js  Google Places search
src/sources/osm.js     OpenStreetMap search
src/crawler.js         website contact extractor
src/qualify.js         Claude lead scoring
src/store.js           JSON file storage (DATA_DIR/db.json)
public/                the phone app (PWA: manifest, service worker, icons)
test/                  `npm test`
```
