# ويدجت إسلامية — Islamic Widgets

Android home-screen widgets with glass styling and Arabic calligraphy:

- **أوقات الصلاة** — next prayer, live countdown and today's five times (Adhan library; Umm al-Qura by default).
- **التاريخ الهجري** — weekday, Umm al-Qura Hijri date and Gregorian date.
- **آية اليوم** — a verse each day (tap for another). Uthmani text from QuranEnc via the `quran-json` package, cross-checked with Tanzil.
- **التسبيح** — tap to count 33 × سبحان الله, 33 × الحمد لله, 34 × الله أكبر.

Settings: location (automatic or city list), calculation method, Asr method, Hijri adjustment,
glass opacity and colour, heading font (Ruqaa, Naskh, Kufi, or an imported TTF such as Thuluth), Arabic or Western digits.

## Install

Every push builds a signed APK with GitHub Actions and publishes it as a release:
<https://github.com/Zyaddd/Claude_Repo/releases/latest/download/IslamicWidgets.apk>

## Signing

The workflow signs with a permanent key when the repository secrets `SIGNING_KEYSTORE_BASE64`
(the keystore file, base64-encoded) and `SIGNING_PASSWORD` are set; the key alias must be `widgets`.
Without them each build gets a temporary key, so uninstall the old version before installing a new one.

## Fonts

Bundled fonts are under the SIL Open Font License: Amiri, Amiri Quran, Aref Ruqaa, Noto Kufi Arabic, Reem Kufi.
Other fonts (e.g. DecoType Thuluth) are not bundled; import them in Settings.

---

# تلفاز عربي — Arab TV (IPTV)

The `iptv/` module is a separate app: Arabic news, documentary and cartoon channels for phone and
Android TV, with casting to the TV on the same Wi-Fi or from anywhere. See [iptv/README.md](iptv/README.md).

Download: <https://github.com/Zyaddd/Claude_Repo/releases/download/arabtv/ArabTV.apk>
