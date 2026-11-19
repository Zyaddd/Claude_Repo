// Interface language (English / Arabic) with right-to-left support.
//
// Language is chosen in this order:
//   ?lang=ar|en  →  the visitor's saved choice  →  a GCC currency in the link
//   (SAR, AED, QAR, KWD, BHD, OMR)  →  browser language  →  English.
// So a demo link sent to a Gulf shop with &currency=SAR opens in Arabic.

const STRINGS = {
  en: {
    tagline: 'Custom Kit Studio',
    total: 'Total',
    langLabel: 'Language',
    'view.group': 'Camera views',
    'view.front': 'Front',
    'view.back': 'Back',
    'view.side': 'Side',
    'view.spinTitle': 'Auto-rotate',
    hint: 'Drag to rotate 360° · pinch or scroll to zoom',
    loading: 'Preparing your 3D kit…',
    loadFail: 'Sorry, 3D preview could not start on this device.',
    stageLabel: '3D kit preview',

    'step.kit': 'Choose your kit',
    'filter.all': 'All',
    'type.Home': 'Home',
    'type.Away': 'Away',
    'typeLong.Home': 'Home kit',
    'typeLong.Away': 'Away kit',

    'step.print': 'Name & number',
    pickPlayer: 'Pick a player',
    optional: '(optional)',
    ownName: 'Your own name & number',
    name: 'Name',
    number: 'Number',
    namePh: 'e.g. SMITH',
    lettering: 'Lettering style',
    arched: 'Arched name',
    arabicNote: 'Arabic names are supported. Type in Arabic script and it prints right-to-left.',
    'font.block': 'Classic Block',
    'font.modern': 'Modern',
    'font.sport': 'Sport Italic',
    'font.rounded': 'Rounded',
    'msg.chars': 'Letters, spaces, hyphens and apostrophes only.',
    'msg.max': 'Maximum {n} characters.',
    'msg.blocked': 'Sorry, we can’t print that name.',

    'step.size': 'Size',
    sizeGuide: 'Size guide',
    'group.adult': 'Adult',
    'group.kids': 'Kids',

    'step.extras': 'Extras',
    patches: 'Sleeve patches',
    'patch.none': 'None',
    'patch.league': 'League',
    'patch.champions': 'Champions',
    'patchLong.league': 'League patch',
    'patchLong.champions': 'Champions patch',
    addShorts: 'Add matching shorts',
    shortsNumber: 'Number on shorts',

    'step.order': 'Your order',
    qty: 'Quantity',
    decQty: 'Decrease quantity',
    incQty: 'Increase quantity',
    addToBasket: 'Add to basket',
    share: 'Share design',
    saveImage: 'Save image',
    jobSheet: 'Print job sheet',
    footer: 'Kits shown are original demo designs. 3D preview is for guidance; printed sizes follow the job sheet.',

    'line.shirt': '{club} {type} shirt',
    'line.name': 'Name printing',
    'line.number': 'Number printing',
    'line.patches': 'Sleeve patches',
    'line.shorts': 'Matching shorts',
    'line.shortsNumber': 'Shorts number',

    sizeGuideTitle: 'Size guide',
    close: 'Close',
    sizeNote: 'Measure around the fullest part of the chest. Between sizes? Choose the larger size for a relaxed fit.',
    'sg.size': 'Size',
    'sg.age': 'Age',
    'sg.chest': 'Chest (cm)',
    'sg.height': 'Height (cm)',
    'sg.length': 'Shirt length (cm)',

    basketTitle: 'Added to your basket',
    keepDesigning: 'Keep designing',
    checkout: 'Checkout',
    yourKit: 'Your kit',
    'row.kit': 'Kit',
    'row.size': 'Size',
    'row.name': 'Name',
    'row.number': 'Number',
    'row.qty': 'Qty',
    'row.total': 'Total',

    imageTitle: 'Your kit image',
    imageAlt: 'Front and back of your kit',
    imageNote: 'Press and hold (or right-click) the image to save it.',
    designedIn: 'Designed in Kit Studio',

    'toast.checkout': 'Demo mode: checkout is connected when installed on the shop’s website.',
    'toast.shareDemo': 'Share links are switched on when installed on the shop’s website.',
    'toast.copied': 'Link copied. Anyone with it sees this exact design.',
    shareTitle: '{shop}: my custom kit',

    // Job sheet
    'js.dialog': 'Print job sheet',
    'js.title': 'Print job sheet',
    'js.close': 'Close',
    'js.print': 'Print / Save PDF',
    'js.printNote': 'Printing is switched on when installed on the shop’s website.',
    'js.order': 'Order',
    'js.printing': 'Printing',
    'js.preview': 'Preview',
    'js.front': 'Front',
    'js.back': 'Back',
    'js.artwork': 'Artwork (actual layout)',
    'js.artworkAlt': 'Name and number artwork',
    'js.printArea': 'Print area ≈ {w} × {h} cm',
    'js.kit': 'Kit',
    'js.size': 'Size',
    'js.qty': 'Quantity',
    'js.patches': 'Sleeve patches',
    'js.shorts': 'Shorts',
    'js.unit': 'Unit price',
    'js.total': 'Total',
    'js.yes': 'Yes',
    'js.no': 'No',
    'js.none': 'None',
    'js.shortsWithNumber': 'Yes — number {n} on front left leg',
    'js.name': 'Name',
    'js.number': 'Number',
    'js.lettering': 'Lettering',
    'js.archedSuffix': ' · arched name',
    'js.colour': 'Colour',
    'js.outline': 'outline',
    'js.nameHeight': 'Name height',
    'js.nameHeightVal': '{h} cm (capitals)',
    'js.nameWidth': 'Name width',
    'js.condensed': ' (condensed to {p}%)',
    'js.numberHeight': 'Number height',
    'js.numberWidth': 'Number width',
    'js.placement': 'Placement',
    'js.placementVal': 'Top of name {top} cm below back collar seam; {gap} cm gap to number; centred',
    'js.shortsNumber': 'Shorts number',
    'js.shortsNumberVal': '{h} cm, front left leg',
    'js.noPrint': 'No name or number printing on this order.',
    'js.warnLong': 'Long name: lettering is condensed and/or reduced in height to fit the maximum width. Check spacing before pressing.',
    'js.printedBy': 'Printed by',
    'js.checkedBy': 'Checked by',
    'js.shipped': 'Collected / shipped',
    'js.qr': 'Scan to reopen design',
    'js.qrAlt': 'QR code to reopen this design',
  },

  ar: {
    tagline: 'استوديو تصميم الأطقم',
    total: 'الإجمالي',
    langLabel: 'اللغة',
    'view.group': 'زوايا العرض',
    'view.front': 'أمام',
    'view.back': 'خلف',
    'view.side': 'جانب',
    'view.spinTitle': 'دوران تلقائي',
    hint: 'اسحب لتدوير القميص 360° · قرّب بإصبعين أو بعجلة الفأرة',
    loading: 'جارٍ تجهيز طقمك ثلاثي الأبعاد…',
    loadFail: 'عذراً، تعذّر تشغيل المعاينة ثلاثية الأبعاد على هذا الجهاز.',
    stageLabel: 'معاينة الطقم ثلاثية الأبعاد',

    'step.kit': 'اختر الطقم',
    'filter.all': 'الكل',
    'type.Home': 'أساسي',
    'type.Away': 'احتياطي',
    'typeLong.Home': 'الطقم الأساسي',
    'typeLong.Away': 'الطقم الاحتياطي',

    'step.print': 'الاسم والرقم',
    pickPlayer: 'اختر لاعباً',
    optional: '(اختياري)',
    ownName: 'اسمك ورقمك الخاص',
    name: 'الاسم',
    number: 'الرقم',
    namePh: 'مثال: محمد أو SMITH',
    lettering: 'نوع الخط',
    arched: 'اسم مقوّس',
    arabicNote: 'اكتب الاسم بالعربية أو بالإنجليزية، ويُطبع الاسم العربي من اليمين إلى اليسار.',
    'font.block': 'كلاسيكي',
    'font.modern': 'عصري',
    'font.sport': 'رياضي مائل',
    'font.rounded': 'دائري',
    'msg.chars': 'يُسمح بالحروف والمسافات والشرطة والفاصلة العليا فقط.',
    'msg.max': 'الحد الأقصى {n} حرفاً.',
    'msg.blocked': 'عذراً، لا يمكننا طباعة هذا الاسم.',

    'step.size': 'المقاس',
    sizeGuide: 'دليل المقاسات',
    'group.adult': 'كبار',
    'group.kids': 'أطفال',

    'step.extras': 'إضافات',
    patches: 'شارات الأكمام',
    'patch.none': 'بدون',
    'patch.league': 'الدوري',
    'patch.champions': 'الأبطال',
    'patchLong.league': 'شارة الدوري',
    'patchLong.champions': 'شارة الأبطال',
    addShorts: 'إضافة شورت مطابق',
    shortsNumber: 'الرقم على الشورت',

    'step.order': 'طلبك',
    qty: 'الكمية',
    decQty: 'إنقاص الكمية',
    incQty: 'زيادة الكمية',
    addToBasket: 'أضف إلى السلة',
    share: 'مشاركة التصميم',
    saveImage: 'حفظ الصورة',
    jobSheet: 'ورقة الطباعة',
    footer: 'الأطقم المعروضة تصاميم تجريبية أصلية. المعاينة ثلاثية الأبعاد للاسترشاد، والمقاسات المطبوعة تتبع ورقة الطباعة.',

    'line.shirt': 'قميص {club} {type}',
    'line.name': 'طباعة الاسم',
    'line.number': 'طباعة الرقم',
    'line.patches': 'شارات الأكمام',
    'line.shorts': 'شورت مطابق',
    'line.shortsNumber': 'رقم الشورت',

    sizeGuideTitle: 'دليل المقاسات',
    close: 'إغلاق',
    sizeNote: 'قِس محيط الصدر عند أعرض نقطة. إذا كنت بين مقاسين، اختر المقاس الأكبر لراحة أكثر.',
    'sg.size': 'المقاس',
    'sg.age': 'العمر',
    'sg.chest': 'محيط الصدر (سم)',
    'sg.height': 'الطول (سم)',
    'sg.length': 'طول القميص (سم)',

    basketTitle: 'أُضيف إلى سلتك',
    keepDesigning: 'متابعة التصميم',
    checkout: 'إتمام الشراء',
    yourKit: 'طقمك',
    'row.kit': 'الطقم',
    'row.size': 'المقاس',
    'row.name': 'الاسم',
    'row.number': 'الرقم',
    'row.qty': 'الكمية',
    'row.total': 'الإجمالي',

    imageTitle: 'صورة طقمك',
    imageAlt: 'الواجهة الأمامية والخلفية لطقمك',
    imageNote: 'اضغط مطوّلاً على الصورة (أو انقر بزر الفأرة الأيمن) لحفظها.',
    designedIn: 'صُمّم في Kit Studio',

    'toast.checkout': 'نسخة تجريبية: يُربط إتمام الشراء عند تركيب الأداة على موقع المتجر.',
    'toast.shareDemo': 'تُفعّل روابط المشاركة عند تركيب الأداة على موقع المتجر.',
    'toast.copied': 'نُسخ الرابط. أي شخص يفتحه يرى هذا التصميم نفسه.',
    shareTitle: '{shop}: طقمي المخصّص',

    'js.dialog': 'ورقة الطباعة',
    'js.title': 'ورقة الطباعة',
    'js.close': 'إغلاق',
    'js.print': 'طباعة / حفظ PDF',
    'js.printNote': 'تُفعّل الطباعة عند تركيب الأداة على موقع المتجر.',
    'js.order': 'الطلب',
    'js.printing': 'الطباعة',
    'js.preview': 'المعاينة',
    'js.front': 'أمام',
    'js.back': 'خلف',
    'js.artwork': 'التصميم (بالمقاس الفعلي)',
    'js.artworkAlt': 'تصميم الاسم والرقم',
    'js.printArea': 'مساحة الطباعة ≈ {w} × {h} سم',
    'js.kit': 'الطقم',
    'js.size': 'المقاس',
    'js.qty': 'الكمية',
    'js.patches': 'شارات الأكمام',
    'js.shorts': 'الشورت',
    'js.unit': 'سعر القطعة',
    'js.total': 'الإجمالي',
    'js.yes': 'نعم',
    'js.no': 'لا',
    'js.none': 'بدون',
    'js.shortsWithNumber': 'نعم، الرقم {n} على الساق اليسرى من الأمام',
    'js.name': 'الاسم',
    'js.number': 'الرقم',
    'js.lettering': 'نوع الخط',
    'js.archedSuffix': ' · اسم مقوّس',
    'js.colour': 'اللون',
    'js.outline': 'الحدّ',
    'js.nameHeight': 'ارتفاع الاسم',
    'js.nameHeightVal': '{h} سم (ارتفاع الحروف)',
    'js.nameWidth': 'عرض الاسم',
    'js.condensed': ' (مضغوط إلى {p}%)',
    'js.numberHeight': 'ارتفاع الرقم',
    'js.numberWidth': 'عرض الرقم',
    'js.placement': 'الموضع',
    'js.placementVal': 'أعلى الاسم على بُعد {top} سم أسفل خياطة الياقة الخلفية، والمسافة إلى الرقم {gap} سم، في المنتصف',
    'js.shortsNumber': 'رقم الشورت',
    'js.shortsNumberVal': '{h} سم، على الساق اليسرى من الأمام',
    'js.noPrint': 'لا توجد طباعة اسم أو رقم في هذا الطلب.',
    'js.warnLong': 'اسم طويل: ضُغطت الحروف و/أو صُغّر ارتفاعها لتناسب أقصى عرض. راجعوا المسافات قبل الكبس.',
    'js.printedBy': 'طُبع بواسطة',
    'js.checkedBy': 'رُوجع بواسطة',
    'js.shipped': 'سُلّم / شُحن',
    'js.qr': 'امسح الرمز لفتح التصميم',
    'js.qrAlt': 'رمز QR لفتح هذا التصميم',
  },
};

const GCC_CURRENCIES = ['SAR', 'AED', 'QAR', 'KWD', 'BHD', 'OMR'];
const STORE_KEY = 'kitstudio-lang';
const listeners = new Set();

function detectLang() {
  const q = new URLSearchParams(location.search);
  const fromUrl = q.get('lang');
  if (fromUrl === 'ar' || fromUrl === 'en') return fromUrl;
  try {
    const saved = localStorage.getItem(STORE_KEY);
    if (saved === 'ar' || saved === 'en') return saved;
  } catch { /* storage unavailable */ }
  if (GCC_CURRENCIES.includes((q.get('currency') || '').toUpperCase())) return 'ar';
  if ((navigator.language || '').toLowerCase().startsWith('ar')) return 'ar';
  return 'en';
}

let current = detectLang();

export const getLang = () => current;
export const isRTL = () => current === 'ar';

export function t(key, vars = {}) {
  const s = STRINGS[current][key] ?? STRINGS.en[key] ?? key;
  return s.replace(/\{(\w+)\}/g, (_, k) => (vars[k] ?? ''));
}

// Kids sizes like "5–6Y" read better as "5–6 سنوات" in Arabic.
export function sizeLabel(s) {
  return current === 'ar' ? s.replace(/Y$/, ' سنوات') : s;
}

export function onLangChange(fn) { listeners.add(fn); }

function applyDocument() {
  document.documentElement.lang = current;
  document.documentElement.dir = current === 'ar' ? 'rtl' : 'ltr';
}

// Fill every element marked with data-i18n* attributes.
export function applyStatic(root = document) {
  root.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n); });
  root.querySelectorAll('[data-i18n-placeholder]').forEach((el) => { el.placeholder = t(el.dataset.i18nPlaceholder); });
  root.querySelectorAll('[data-i18n-aria]').forEach((el) => { el.setAttribute('aria-label', t(el.dataset.i18nAria)); });
  root.querySelectorAll('[data-i18n-title]').forEach((el) => { el.title = t(el.dataset.i18nTitle); });
  root.querySelectorAll('[data-i18n-alt]').forEach((el) => { el.alt = t(el.dataset.i18nAlt); });
}

export function setLang(lang, { persist = true } = {}) {
  if (lang !== 'ar' && lang !== 'en') return;
  current = lang;
  if (persist) {
    try { localStorage.setItem(STORE_KEY, lang); } catch { /* ignore */ }
    const q = new URLSearchParams(location.search);
    q.set('lang', lang);
    history.replaceState(null, '', `${location.pathname}?${q.toString()}${location.hash}`);
  }
  applyDocument();
  applyStatic();
  for (const fn of listeners) fn(lang);
}

applyDocument();
