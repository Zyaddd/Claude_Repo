// Shop configuration. Each shop that buys Kit Studio edits this file (or sets
// window.KIT_STUDIO_CONFIG before the app loads) to brand it and set prices.
//
// For demos you can also override a few values in the URL, e.g.
//   ?shop=Jersey%20House&accent=%23ff3d6e&currency=SAR

import { getLang } from './i18n.js';

const DEFAULTS = {
  shopName: 'Demo Sports',
  tagline: 'Custom Kit Studio',
  accent: '#c6ff3d',
  currency: 'GBP',
  locale: 'en-GB',

  // Prices (in the currency above). Kit base prices live in the kit catalogue.
  prices: {
    name: 12,
    number: 8,
    shorts: 28,
    shortsNumber: 5,
    patch: 10,
    kidsMultiplier: 0.75, // kids kit price = adult price x this
  },

  nameMaxLength: 12,
  // Names the shop refuses to print (lower-case, matched as whole words or substrings).
  blockedWords: ['fuck', 'shit', 'cunt', 'bitch', 'nigg', 'fag', 'wank', 'twat', 'dick', 'pussy', 'whore', 'slut', 'nazi', 'hitler'],

  sizes: {
    adult: ['S', 'M', 'L', 'XL', '2XL', '3XL'],
    kids: ['3–4Y', '5–6Y', '7–8Y', '9–10Y', '11–12Y', '13–14Y'],
  },
  sizeGuide: {
    adult: [
      ['S', '88–96', '69'], ['M', '96–104', '71'], ['L', '104–112', '73'],
      ['XL', '112–124', '75'], ['2XL', '124–136', '77'], ['3XL', '136–148', '79'],
    ],
    kids: [
      ['3–4Y', '98–104', '40'], ['5–6Y', '110–116', '43'], ['7–8Y', '122–128', '47'],
      ['9–10Y', '134–140', '51'], ['11–12Y', '146–152', '55'], ['13–14Y', '158–164', '59'],
    ],
  },

  // Optional: where "Add to basket" sends the customer. The design is also
  // posted to the parent page (window.postMessage) when embedded in an iframe.
  checkoutUrl: null,
};

function fromUrl() {
  const q = new URLSearchParams(location.search);
  const o = {};
  if (q.get('shop')) o.shopName = q.get('shop').slice(0, 40);
  if (q.get('accent') && /^#[0-9a-f]{6}$/i.test(q.get('accent'))) o.accent = q.get('accent');
  if (q.get('currency') && /^[A-Z]{3}$/.test(q.get('currency'))) o.currency = q.get('currency');
  return o;
}

export const CONFIG = { ...DEFAULTS, ...(window.KIT_STUDIO_CONFIG || {}), ...fromUrl() };
CONFIG.prices = { ...DEFAULTS.prices, ...(window.KIT_STUDIO_CONFIG?.prices || {}) };

// Prices follow the interface language: Arabic uses Arabic currency names with
// Western digits (common in Gulf retail).
const formatters = {};
export const money = (v) => {
  const locale = getLang() === 'ar' ? 'ar-u-nu-latn' : CONFIG.locale;
  formatters[locale] ||= new Intl.NumberFormat(locale, { style: 'currency', currency: CONFIG.currency });
  return formatters[locale].format(v);
};
