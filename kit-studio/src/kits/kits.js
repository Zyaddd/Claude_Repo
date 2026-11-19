// Demo catalogue. Every club, crest, sponsor and design here is fictional and
// original, so the product can be demonstrated and sold anywhere. Shops that
// hold licences replace this catalogue with their official kit artwork
// (see README: "Adding licensed kits").

export const CLUBS = [
  {
    id: 'northbridge', name: 'Northbridge FC', short: 'NFC', founded: 1884,
    crest: { shape: 'shield', fill: '#0f2a52', ring: '#8fc3ff', accent: '#ffffff', emblem: 'bridge' },
    players: [['ASHWORTH', 1], ['OKAFOR', 4], ['LINDQVIST', 8], ['MARLOWE', 9], ['DA SILVA', 10], ['KESTREL', 11]],
  },
  {
    id: 'kingsmoor', name: 'Kingsmoor Athletic', short: 'KA', founded: 1879,
    crest: { shape: 'round', fill: '#b3121f', ring: '#ffffff', accent: '#f2c14e', emblem: 'crown' },
    players: [['HALLORAN', 1], ['BRENNAN', 5], ['MOREAU', 7], ['ADEYEMI', 9], ['CASTELLI', 10], ['VOSS', 17]],
  },
  {
    id: 'harbourside', name: 'Harbourside Rovers', short: 'HR', founded: 1892,
    crest: { shape: 'pentagon', fill: '#0b1f3a', ring: '#e8eef6', accent: '#3fa9f5', emblem: 'anchor' },
    players: [['QUINLAN', 2], ['SATO', 6], ['FERREIRA', 8], ['NKEMELU', 9], ['RYDER', 14], ['BALOGUN', 19]],
  },
  {
    id: 'ashvale', name: 'Ashvale United', short: 'AU', founded: 1901,
    crest: { shape: 'shield', fill: '#5c1a2b', ring: '#e2b74a', accent: '#e2b74a', emblem: 'oak' },
    players: [['CALDWELL', 3], ['IVANOV', 4], ['PEMBERTON', 8], ['MENSAH', 9], ['ARIAS', 10], ['HOLT', 23]],
  },
  {
    id: 'westford', name: 'Westford City', short: 'WC', founded: 1888,
    crest: { shape: 'diamond', fill: '#0d4d2f', ring: '#f4f4f0', accent: '#101010', emblem: 'star' },
    players: [['WHITCOMBE', 1], ['DIALLO', 5], ['NAKAMURA', 7], ['STERLING-ROSS', 9], ['VALDEZ', 11], ['KOWALSKI', 21]],
  },
  {
    id: 'redcliffe', name: 'Redcliffe Town', short: 'RT', founded: 1910,
    crest: { shape: 'round', fill: '#141414', ring: '#ff7a1a', accent: '#ff7a1a', emblem: 'flame' },
    players: [['THORNE', 1], ['EZE-BANKS', 2], ['MORALES', 6], ['FINNEGAN', 9], ['OYELARAN', 10], ['STRAND', 18]],
  },
  {
    id: 'eastgate', name: 'Eastgate Wanderers', short: 'EW', founded: 1897,
    crest: { shape: 'pentagon', fill: '#3d1f6e', ring: '#ffffff', accent: '#c9b6ff', emblem: 'wing' },
    players: [['BECKFORD', 4], ['HUSSAIN', 6], ['LAURENT', 8], ['OKONKWO', 9], ['REYES', 10], ['MAGNUSSON', 15]],
  },
  {
    id: 'silverlake', name: 'Silverlake FC', short: 'SFC', founded: 1919,
    crest: { shape: 'diamond', fill: '#16181d', ring: '#c7ccd6', accent: '#c7ccd6', emblem: 'wave' },
    players: [['ABERNATHY', 1], ['CHUKWU', 3], ['DEVLIN', 7], ['FONSECA', 9], ['KARLSSON', 10], ['MBAPPA', 20]],
  },
];

// pattern types: solid, stripes, hoops, sash, halves, pinstripe, gradient, chevron, yoke, sidepanel
export const KITS = [
  {
    id: 'northbridge-home', club: 'northbridge', type: 'Home', price: 75,
    base: '#7ab8ec', second: '#0f2a52', trim: '#0f2a52', sleeve: null, cuff: '#0f2a52',
    pattern: { type: 'gradient', to: '#5d9fd9', tonal: 'diagonal' },
    print: { fill: '#0f2a52', outline: null },
    sponsor: { text: 'AERIX', color: '#0f2a52', style: 'wide' },
    shorts: { base: '#ffffff', trim: '#0f2a52', stripe: '#7ab8ec' },
  },
  {
    id: 'northbridge-away', club: 'northbridge', type: 'Away', price: 75,
    base: '#0f2a52', second: '#ffffff', trim: '#7ab8ec', sleeve: null, cuff: '#7ab8ec',
    pattern: { type: 'chevron', color: '#7ab8ec' },
    print: { fill: '#ffffff', outline: null },
    sponsor: { text: 'AERIX', color: '#ffffff', style: 'wide' },
    shorts: { base: '#0f2a52', trim: '#7ab8ec', stripe: '#7ab8ec' },
  },
  {
    id: 'kingsmoor-home', club: 'kingsmoor', type: 'Home', price: 75,
    base: '#c8141f', second: '#ffffff', trim: '#ffffff', sleeve: '#ffffff', cuff: '#c8141f',
    pattern: { type: 'solid', tonal: 'pin' },
    print: { fill: '#ffffff', outline: null },
    sponsor: { text: 'VELORA', color: '#ffffff', style: 'serif' },
    shorts: { base: '#ffffff', trim: '#c8141f', stripe: '#c8141f' },
  },
  {
    id: 'kingsmoor-away', club: 'kingsmoor', type: 'Away', price: 75,
    base: '#f3efe4', second: '#c8141f', trim: '#c8141f', sleeve: null, cuff: '#c8141f',
    pattern: { type: 'sash', color: '#c8141f', width: 0.09 },
    print: { fill: '#1b1b1b', outline: '#f3efe4' },
    sponsor: { text: 'VELORA', color: '#1b1b1b', style: 'serif' },
    shorts: { base: '#1b1b1b', trim: '#c8141f', stripe: '#c8141f' },
  },
  {
    id: 'harbourside-home', club: 'harbourside', type: 'Home', price: 75,
    base: '#0b1f3a', second: '#e8eef6', trim: '#e8eef6', sleeve: null, cuff: '#e8eef6',
    pattern: { type: 'hoops', color: '#e8eef6', height: 0.055 },
    print: { fill: '#ffffff', outline: '#0b1f3a' },
    sponsor: { text: 'TIDEWAY', color: '#0b1f3a', style: 'italic', panel: '#e8eef6' },
    shorts: { base: '#0b1f3a', trim: '#e8eef6', stripe: '#e8eef6' },
  },
  {
    id: 'harbourside-away', club: 'harbourside', type: 'Away', price: 75,
    base: '#3fa9f5', second: '#0b1f3a', trim: '#0b1f3a', sleeve: null, cuff: '#0b1f3a',
    pattern: { type: 'yoke', color: '#0b1f3a', tonal: 'wave' },
    print: { fill: '#0b1f3a', outline: null },
    sponsor: { text: 'TIDEWAY', color: '#0b1f3a', style: 'italic' },
    shorts: { base: '#3fa9f5', trim: '#0b1f3a', stripe: '#0b1f3a' },
  },
  {
    id: 'ashvale-home', club: 'ashvale', type: 'Home', price: 75,
    base: '#6b1d31', second: '#e2b74a', trim: '#e2b74a', sleeve: null, cuff: '#e2b74a',
    pattern: { type: 'pinstripe', color: '#8a2a43', tonal: 'diagonal' },
    print: { fill: '#e2b74a', outline: null },
    sponsor: { text: 'KYNETO', color: '#e2b74a', style: 'wide' },
    shorts: { base: '#e2b74a', trim: '#6b1d31', stripe: '#6b1d31' },
  },
  {
    id: 'ashvale-away', club: 'ashvale', type: 'Away', price: 75,
    base: '#f1e6c8', second: '#6b1d31', trim: '#6b1d31', sleeve: null, cuff: '#6b1d31',
    pattern: { type: 'halves', color: '#e6d5a6' },
    print: { fill: '#6b1d31', outline: null },
    sponsor: { text: 'KYNETO', color: '#6b1d31', style: 'wide' },
    shorts: { base: '#6b1d31', trim: '#e2b74a', stripe: '#e2b74a' },
  },
  {
    id: 'westford-home', club: 'westford', type: 'Home', price: 75,
    base: '#0d4d2f', second: '#101010', trim: '#101010', sleeve: '#101010', cuff: '#0d4d2f',
    pattern: { type: 'stripes', color: '#101010', width: 0.06 },
    print: { fill: '#ffffff', outline: '#101010' },
    sponsor: { text: 'LUMIO', color: '#ffffff', style: 'round', panel: '#101010' },
    shorts: { base: '#101010', trim: '#0d4d2f', stripe: '#0d4d2f' },
  },
  {
    id: 'westford-away', club: 'westford', type: 'Away', price: 75,
    base: '#f4f4f0', second: '#0d4d2f', trim: '#0d4d2f', sleeve: null, cuff: '#0d4d2f',
    pattern: { type: 'sidepanel', color: '#0d4d2f', tonal: 'pin' },
    print: { fill: '#0d4d2f', outline: null },
    sponsor: { text: 'LUMIO', color: '#0d4d2f', style: 'round' },
    shorts: { base: '#f4f4f0', trim: '#0d4d2f', stripe: '#0d4d2f' },
  },
  {
    id: 'redcliffe-home', club: 'redcliffe', type: 'Home', price: 75,
    base: '#ff7a1a', second: '#141414', trim: '#141414', sleeve: null, cuff: '#141414',
    pattern: { type: 'yoke', color: '#141414', tonal: 'diagonal' },
    print: { fill: '#141414', outline: null },
    sponsor: { text: 'SOLVEX', color: '#141414', style: 'wide' },
    shorts: { base: '#141414', trim: '#ff7a1a', stripe: '#ff7a1a' },
  },
  {
    id: 'redcliffe-away', club: 'redcliffe', type: 'Away', price: 75,
    base: '#141414', second: '#ff7a1a', trim: '#ff7a1a', sleeve: null, cuff: '#ff7a1a',
    pattern: { type: 'stripes', color: '#262626', width: 0.03, tonal: 'none' },
    print: { fill: '#ff7a1a', outline: null },
    sponsor: { text: 'SOLVEX', color: '#ff7a1a', style: 'wide' },
    shorts: { base: '#141414', trim: '#ff7a1a', stripe: '#ff7a1a' },
  },
  {
    id: 'eastgate-home', club: 'eastgate', type: 'Home', price: 75,
    base: '#3d1f6e', second: '#ffffff', trim: '#ffffff', sleeve: null, cuff: '#ffffff',
    pattern: { type: 'sash', color: '#ffffff', width: 0.07 },
    print: { fill: '#ffffff', outline: '#3d1f6e' },
    sponsor: { text: 'ORBITAL', color: '#ffffff', style: 'round' },
    shorts: { base: '#ffffff', trim: '#3d1f6e', stripe: '#3d1f6e' },
  },
  {
    id: 'eastgate-away', club: 'eastgate', type: 'Away', price: 75,
    base: '#c9b6ff', second: '#3d1f6e', trim: '#3d1f6e', sleeve: null, cuff: '#3d1f6e',
    pattern: { type: 'gradient', to: '#a993f0', tonal: 'wave' },
    print: { fill: '#3d1f6e', outline: null },
    sponsor: { text: 'ORBITAL', color: '#3d1f6e', style: 'round' },
    shorts: { base: '#3d1f6e', trim: '#c9b6ff', stripe: '#c9b6ff' },
  },
  {
    id: 'silverlake-home', club: 'silverlake', type: 'Home', price: 75,
    base: '#1a1c22', second: '#c7ccd6', trim: '#c7ccd6', sleeve: null, cuff: '#c7ccd6',
    pattern: { type: 'pinstripe', color: '#3a3e48', tonal: 'none' },
    print: { fill: '#e7eaf0', outline: null },
    sponsor: { text: 'NORTHWAVE', color: '#c7ccd6', style: 'wide' },
    shorts: { base: '#1a1c22', trim: '#c7ccd6', stripe: '#c7ccd6' },
  },
  {
    id: 'silverlake-away', club: 'silverlake', type: 'Away', price: 75,
    base: '#d9dde4', second: '#1a1c22', trim: '#1a1c22', sleeve: null, cuff: '#1a1c22',
    pattern: { type: 'hoops', color: '#c5cad3', height: 0.018, tonal: 'none' },
    print: { fill: '#1a1c22', outline: null },
    sponsor: { text: 'NORTHWAVE', color: '#1a1c22', style: 'wide' },
    shorts: { base: '#1a1c22', trim: '#d9dde4', stripe: '#d9dde4' },
  },
];

export const clubOf = (kit) => CLUBS.find((c) => c.id === kit.club);
export const kitById = (id) => KITS.find((k) => k.id === id);
