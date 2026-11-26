#!/usr/bin/env python3
"""Refresh the channel snapshot bundled with the app.

Downloads iptv-org's Arabic-language playlist and its channel database, and writes:
  src/main/assets/ara.m3u            - the playlist (the app refreshes it online at runtime too)
  src/main/assets/freetv_arab.m3u    - the Arab-world part of the Free-TV playlist (officially free channels)
  src/main/assets/arabic_names.json  - channel id -> Arabic name, for channels in both playlists
Run from anywhere:  python3 iptv/tools/update_catalog.py
"""
import csv, io, json, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "assets")
PLAYLIST = "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/ara.m3u"
FREETV = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
# Must match ChannelRepository.FREETV_GROUPS / FREETV_EXCLUDE in the app.
FREETV_GROUPS = {
    "News (AR)", "Saudi Arabia", "United Arab Emirates", "Qatar", "Kuwait", "Bahrain", "Oman", "Yemen",
    "Iraq", "Syria", "Lebanon", "Jordan", "Palestine", "Egypt", "Sudan", "Libya", "Tunisia", "Algeria",
    "Morocco", "Mauritania",
}
FREETV_EXCLUDE = re.compile(r"(?i)persia|english|zagros|turkmen|kurd")
# Arabic names the database lacks.
NAME_OVERRIDES = {
    "NationalGeographicAbuDhabi.ae": "ناشيونال جيوغرافيك أبوظبي",
    "MBC3.ae": "إم بي سي 3",
    "MBC1.ae": "إم بي سي 1",
    "AbuDhabiTV.ae": "قناة أبوظبي",
    "DubaiTV.ae": "تلفزيون دبي",
}
DATABASE = "https://raw.githubusercontent.com/iptv-org/database/master/data/channels.csv"
ARABIC = re.compile(r"[؀-ۿ]")


def get(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read().decode("utf-8")


def main():
    m3u = get(PLAYLIST)
    if not m3u.startswith("#EXTM3U") or m3u.count("#EXTINF") < 100:
        sys.exit("playlist looks wrong; not overwriting")
    freetv = filter_freetv(get(FREETV))
    ids = {m.split("@")[0] for m in re.findall(r'tvg-id="([^"]+)"', m3u + freetv)}
    names = {}
    for row in csv.DictReader(io.StringIO(get(DATABASE))):
        if row["id"] not in ids:
            continue
        for alt in (row.get("alt_names") or "").split(";"):
            alt = re.sub("[\u0640\u200e\u200f]", "", alt).strip()
            if alt and ARABIC.search(alt):
                names[row["id"]] = alt
                break
    names.update({k: v for k, v in NAME_OVERRIDES.items() if k in ids})
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, "ara.m3u"), "w", encoding="utf-8") as f:
        f.write(m3u)
    with open(os.path.join(ASSETS, "freetv_arab.m3u"), "w", encoding="utf-8") as f:
        f.write(freetv)
    with open(os.path.join(ASSETS, "arabic_names.json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(names.items())), f, ensure_ascii=False, indent=0)
    print(f"{m3u.count('#EXTINF')} iptv-org streams, {freetv.count('#EXTINF')} Free-TV streams, {len(names)} Arabic names")


def filter_freetv(text):
    """Keeps entries whose group is an Arab country (or Arabic news) and that are not in another language."""
    out, lines = ["#EXTM3U"], text.splitlines()
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF"):
            continue
        group = re.search(r'group-title="([^"]*)"', line)
        if not group or group.group(1) not in FREETV_GROUPS or FREETV_EXCLUDE.search(line):
            continue
        j = i + 1
        while j < len(lines) and lines[j].startswith("#"):
            j += 1
        if j < len(lines) and lines[j].startswith("http"):
            out += lines[i:j + 1]
    if len(out) < 50:
        sys.exit("Free-TV playlist looks wrong; not overwriting")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    main()
