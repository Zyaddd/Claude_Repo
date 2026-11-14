#!/usr/bin/env python3
"""Refresh the channel snapshot bundled with the app.

Downloads iptv-org's Arabic-language playlist and its channel database, and writes:
  src/main/assets/ara.m3u            - the playlist (the app refreshes it online at runtime too)
  src/main/assets/arabic_names.json  - channel id -> Arabic name, for channels in the playlist
Run from anywhere:  python3 iptv/tools/update_catalog.py
"""
import csv, io, json, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "assets")
PLAYLIST = "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/ara.m3u"
DATABASE = "https://raw.githubusercontent.com/iptv-org/database/master/data/channels.csv"
ARABIC = re.compile(r"[؀-ۿ]")


def get(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read().decode("utf-8")


def main():
    m3u = get(PLAYLIST)
    if not m3u.startswith("#EXTM3U") or m3u.count("#EXTINF") < 100:
        sys.exit("playlist looks wrong; not overwriting")
    ids = {m.split("@")[0] for m in re.findall(r'tvg-id="([^"]+)"', m3u)}
    names = {}
    for row in csv.DictReader(io.StringIO(get(DATABASE))):
        if row["id"] not in ids:
            continue
        for alt in (row.get("alt_names") or "").split(";"):
            alt = re.sub("[\u0640\u200e\u200f]", "", alt).strip()
            if alt and ARABIC.search(alt):
                names[row["id"]] = alt
                break
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, "ara.m3u"), "w", encoding="utf-8") as f:
        f.write(m3u)
    with open(os.path.join(ASSETS, "arabic_names.json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(names.items())), f, ensure_ascii=False, indent=0)
    print(f"{m3u.count('#EXTINF')} streams, {len(names)} Arabic names")


if __name__ == "__main__":
    main()
