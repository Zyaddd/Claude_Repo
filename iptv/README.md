# تلفاز عربي — Arab TV

An Android IPTV app for phones, tablets and Android TV (one APK) with Arabic **news**,
**documentary** and **cartoon/kids** channels.

**Download:** <https://github.com/Zyaddd/Claude_Repo/releases/download/arabtv/ArabTV.apk>
(install the same APK on the phone and on the Android TV).

## Channels

The built-in list is the public [iptv-org](https://github.com/iptv-org/iptv) catalog of free-to-air
Arabic streams (about 350 streams, e.g. Al Jazeera, Alarabiya, Al Hadath, Sky News Arabia, Asharq,
BBC Arabic, France 24 Arabic, DW Arabic, CNBC Arabiya, Al Jazeera Documentary, Asharq Documentary,
Asharq Discovery, Spacetoon, Cartoon Network Arabic…), with Arabic names from the iptv-org database.

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

Once a TV is chosen, the phone becomes a remote: tapping a channel plays it on the TV, and the bar at
the bottom has previous/next channel, play/pause, volume, mute and stop.

## Full screen

- **Phone:** the full-screen button in the player (or rotate the phone) gives a landscape, edge-to-edge
  player with the system bars hidden. In portrait the player sits above the channel list.
- **TV:** playback is always full screen. Remote keys: ▲▼ or CH+/CH− change channel, OK shows the
  controls, ◀▶ or Menu opens the options (channel list, favorites, quality, aspect ratio, sleep timer).

## Other features

Favorites, recently watched, search (Arabic or English names, or a country code), stream health
check, quality selection, aspect ratio (fit / zoom / stretch), sleep timer, picture-in-picture on
phones, start with the last channel, kids mode locked by a PIN (cartoon channels only), Arabic and
English interface (Settings → Language).

## Notes

- The TV only receives while the app is open on it (Android does not let a background app start
  video on its own).
- The app hosts no video; it plays streams that broadcasters publish openly.
