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
# iptv-org's per-country stream files list every known link for a channel (the language list keeps
# one), so they add backup and higher-quality links. Must match ChannelRepository.ARAB_COUNTRIES.
ARAB_COUNTRIES = ["sa", "ae", "eg", "qa", "kw", "bh", "om", "ye", "iq", "sy", "lb", "jo", "ps", "sd", "ly", "tn", "dz", "ma", "mr"]
STREAMS = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/{}.m3u"
# Free, ad-supported English documentary channels (Pluto TV, Samsung TV Plus, Rakuten, ...).
# Only official free providers: pay channels re-streamed from unknown servers are left out.
# Must match ChannelRepository.keepWorldDoc in the app.
WORLD_DOCS = "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/categories/documentary.m3u"
OFFICIAL_FREE = re.compile(r"(?i)(amagi\.tv|wurl\.(tv|com)|pluto\.tv|jmp2\.uk/plu-|mediatailor\.[a-z0-9-]+\.amazonaws\.com|rakuten|samsung|xumo|stirr|plex\.tv)")
NOT_DOCS = re.compile(r"(?i)ink master|teen mom|spike|court tv|food|petrolheads|turbo|obsesion|negocio|dateline|on the case|alien|conspiracy|paranormal|mystery|sensing murder|crime|fbi|forensic|homicid|tough jobs|animales|animaux|animais|aventure|documentari|dokumentar|doku\b|histoire|historia|storia|storie|investiga|natura\b|naturaleza|policial|reportagen|varldskrigen|verdenskrigene|archivos|cazador|crimen|detetives|dossiers|germany|sweden|latam|france|\(es\)| es \(| it \(| de \(")
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
    "AlQuranAlKareemTV.sa": "قناة القرآن الكريم – الحرم المكي",
    "AlSunnahAlNabawiyahTV.sa": "قناة السنة النبوية – المسجد النبوي",
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
    parts = ["#EXTM3U"]
    for cc in ARAB_COUNTRIES:
        try:
            parts += [l for l in get(STREAMS.format(cc)).splitlines() if l and not l.startswith("#EXTM3U")]
        except Exception as e:  # a missing country file is fine
            print("skip", cc, e)
    streams = "\n".join(parts) + "\n"
    if streams.count("#EXTINF") < 200:
        sys.exit("stream files look wrong; not overwriting")
    with open(os.path.join(ASSETS, "iptvorg_streams.m3u"), "w", encoding="utf-8") as f:
        f.write(streams)
    docs = filter_world_docs(get(WORLD_DOCS))
    with open(os.path.join(ASSETS, "world_docs.m3u"), "w", encoding="utf-8") as f:
        f.write(docs)
    with open(os.path.join(ASSETS, "freetv_arab.m3u"), "w", encoding="utf-8") as f:
        f.write(freetv)
    with open(os.path.join(ASSETS, "arabic_names.json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(names.items())), f, ensure_ascii=False, indent=0)
    print(f"{m3u.count('#EXTINF')} iptv-org streams, {freetv.count('#EXTINF')} Free-TV streams, {len(names)} Arabic names")


def filter_world_docs(text):
    out, lines = ["#EXTM3U"], text.splitlines()
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF"):
            continue
        j = i + 1
        while j < len(lines) and lines[j].startswith("#"):
            j += 1
        if j >= len(lines):
            continue
        title = line.rsplit(",", 1)[-1]
        if OFFICIAL_FREE.search(lines[j]) and "Geo-blocked" not in title and not NOT_DOCS.search(title):
            out += lines[i:j + 1]
    if len(out) < 20:
        sys.exit("documentary list looks wrong; not overwriting")
    return "\n".join(out) + "\n"


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
