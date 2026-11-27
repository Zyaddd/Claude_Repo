# تلفاز عربي — Arab TV

An Android IPTV app for phones, tablets and Android TV (one APK) with Arabic **news**,
**documentary** and **cartoon/kids** channels.

**Download:** <https://github.com/Zyaddd/Claude_Repo/releases/download/arabtv/ArabTV.apk>
(install the same APK on the phone and on the Android TV).

## Channels

The built-in list merges two public lists of free channels:
[iptv-org](https://github.com/iptv-org/iptv) (Arabic-language streams) and
[Free-TV](https://github.com/Free-TV/IPTV) (only channels that broadcasters provide for free; its
Arab-country groups). The same channel from both lists becomes one channel with every link as a
backup, matched by channel id or by name in any spelling ("Al Jazeera", "الجزيرة", "Al-Jazeera HD").
About 390 channels, including Al Jazeera, Alarabiya, Al Hadath, Sky News Arabia, Asharq, BBC Arabic,
France 24, DW, **National Geographic Abu Dhabi** (Abu Dhabi Media's own stream), Asharq Documentary,
Al Jazeera Documentary, MBC 3, Spacetoon and Cartoon Network Arabic.

- **Find new channels** (Settings → Channels, or the banner on Home) checks the lists online and lets
  you pick which new channels to add. Channels you don't want can be hidden and brought back later.
- **Auto-repair:** when every link of a channel fails, the app downloads the lists again and switches
  to new links by itself. The lists are also refreshed every 6 hours while the app is open, and the
  link that worked last is tried first.
- **Quran** section: 15 channels, led by Al-Haram live from Makkah (Al Quran Al Kareem TV) and
  Al-Masjid An-Nabawi live from Madinah (Al Sunnah Al Nabawiyah TV), on the Saudi Broadcasting
  Authority's own servers with a Globecast backup (`src/main/assets/pinned.m3u`). Allowed in kids mode.
- **Best picture first:** iptv-org's per-country stream files for the 19 Arab countries add about
  100 extra links (they only add links, never channels). When the app checks the channels it reads
  each link's playlist, measures the best resolution on offer, and plays the sharpest working link
  first. Player → Options → Source lists every link with its resolution; a choice is remembered.
  Settings → Video quality: Best (default, always the top variant), Automatic, or Data saver (480p).
- **What's on now / next** comes from free XMLTV guides (epgshare01: Saudi, Egyptian, Al Jazeera),
  where a channel has one.

- A snapshot ships inside the app (works offline); the app downloads the current list on start.
- Channels with several sources fall back automatically when one fails.
- Streams come and go: the app checks every channel at start, marks offline ones, and can hide them.
  Channels marked *Geo-blocked* only play in some countries.
- **Add your own M3U playlist** (e.g. from an IPTV subscription) in Settings → Channel lists.

Refresh the bundled snapshot with `python3 iptv/tools/update_catalog.py`.

## Watching on the TV

| Method | Needs | How |
|---|---|---|
| **Same Wi-Fi** | The app open on the Android TV | Phone: TV icon → the TV appears automatically (or add it by the IP address shown in the TV's Settings). |
| **From anywhere** | Internet on both | TV: Settings → TV receiver shows a 10-character pairing code. Phone: TV icon → *Add TV by pairing code*. Commands go through an [ntfy](https://ntfy.sh) relay, signed with the code (HMAC-SHA256) and checked for replays. You can point both devices at a self-hosted ntfy server. |
| **Chromecast** | TV with Chromecast built-in | Cast icon. Some broadcasters block Chromecast; use the methods above for those. |

**Videos from the phone:** Library → My videos (or Share → Arab TV from the gallery) while a TV is
chosen plays the video on the TV. The phone serves the file over the Wi-Fi (seeking works) and keeps
doing so with the screen off; a notification has a Stop button, and it stops by itself 10 minutes
after the TV stops playing. Works with "Same Wi-Fi" TVs and Chromecast, not over the internet relay.

Once a TV is chosen, the phone becomes a remote: tapping a channel plays it on the TV, and the bar at
the bottom has previous/next channel, play/pause, volume, mute and stop.

## Full screen

- **Phone:** the full-screen button in the player (or rotate the phone) gives a landscape, edge-to-edge
  player with the system bars hidden. In portrait the player sits above the channel list.
- **TV:** playback is always full screen. The channel name shows for 2 seconds after switching
  (Settings → Channels to turn off). Remote keys: ▲▼ or CH+/CH− change channel, number keys jump to a
  channel, OK shows the controls, ◀▶ or Menu opens the options (channel list, favorites, quality,
  aspect ratio, sleep timer).

## Library (cartoons)

Only legal sources: the broadcasters' own YouTube channels (Maspero for Bakkar and Bogy & Tamtam,
Spacetoon, Majid, Iftah Ya Simsim, Freej, Shaabiat Al Cartoon, Mansour) and official apps
(Disney+ for the Arabic and Egyptian Disney dubs, Shahid, Spacetoon Go, ADtv, YouTube Kids).
"My videos" plays your own files from the device or a USB stick. The list is in
`src/main/assets/library.json`.

## Updates inside the app

The app checks this repository's `arabtv` release when it opens (and from Settings → App updates),
downloads the new APK and asks Android to install it. Two conditions:

1. **The repository must be public**, or the app cannot see the release.
2. **Every build must be signed with the same key.** Add a repository secret named
   `SIGNING_PASSWORD` (Settings → Secrets and variables → Actions → New repository secret) with a
   long random password. The next build creates a signing key, commits it to
   `iptv/signing/arabtv.jks.enc` encrypted with that password (AES-256, PBKDF2), and every later
   build reuses it. Keep the password: if it is lost, users must uninstall once to move to a new key.

The first build with the permanent key needs a one-time uninstall of the old app; after that,
updates install over it with favorites and settings kept.

## Prayer, Ramadan and family

- **Prayer times** on Home for your city (Umm al-Qura and each country's official method). At adhan
  time the app offers Al-Haram live, or switches to it (Settings → Prayer times).
- **Ramadan mode** (automatic by the Hijri calendar): iftar / suhoor countdown, Taraweeh from Makkah,
  Quran row first.
- **Profiles**: "Who's watching?", each with its own My List, history and link choices; kids
  profiles see only cartoons, Quran and the library.

## Watching

- Picture-in-picture (TV and phone), "keep watching in corner" while browsing, and two channels
  side by side (◀▶ moves the sound).
- Live channels always play at the live point: they start there, resume there after a pause,
  and jump back by themselves if they drift more than a minute behind.
- Programme guide grid (next 3 hours).
- Android TV home-screen row with your favorites; "Start with the TV" (Android may ask to allow
  "Display over other apps").
- Report a problem: remembered in Settings → Reported channels (shareable), and the channel's
  links are checked again right away. The sleep timer fades the sound out over the last minute.

## Other features

Netflix / Apple TV-style home (hero banner, rows, focus animations, Tajawal font), My List with
your own order, continue watching, channel numbers (type them on the remote), voice search,
search (Arabic or English names, channel number or country code), stream health check, quality selection, aspect ratio (fit / zoom / stretch), sleep timer, picture-in-picture on
phones, start with the last channel, kids mode locked by a PIN (cartoon channels only), Arabic and
English interface (Settings → Language).

## Notes

- The TV only receives while the app is open on it (Android does not let a background app start
  video on its own).
- The app hosts no video; it plays streams that broadcasters publish openly.
