#!/usr/bin/env python3
"""Zusatzdaten für Domi TV (läuft nach songs.py):

* assets/moods.json  – Songs aller Rapper nach Stimmung (Rage / Chill / Nachts / Auto) + BPM für den Party-Modus
* assets/drops.json  – angekündigte Releases mit Datum (Countdown-Karte); am Release-Tag mit Tracks

Tempo und Lautheit kommen von Deezer (BPM, Gain). Ergebnisse werden in tools/mood_cache.json gemerkt,
damit jeder Lauf nur neue Songs abfragt.
"""
import json, os, re, sys, time, datetime, urllib.request, urllib.parse

sys.path.insert(0, os.path.dirname(__file__))
from songs import ARTISTS, norm, slug, itunes_get  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
SONGS = os.path.join(ROOT, "songs")
ALB = os.path.join(ROOT, "albums")
CACHE = os.path.join(os.path.dirname(__file__), "mood_cache.json")
MANUAL = os.path.join(os.path.dirname(__file__), "drops_manual.json")

# Grundstimmung pro Rapper (+ = eher Rage, - = eher ruhig)
BASE = {"Ken Carson": .5, "Bleood": .6, "Protect": -.5, "OsamaSon": .7, "Nettspend": 0, "slattuhs": .6,
        "Destroy Lonely": .1, "Playboi Carti": .5, "che": .7, "Yeat": .4, "xaviersobased": .3, "Prettifun": -.2}
CALM = ("slowed", "reverb", "sad", "love", "alone", "night", "cry", "miss", "dream", "lonely", "rain", "sleep", "heart")
HARD = ("rage", "kill", "war", "blood", "mosh", "fight", "die", "stomp", "demon", "evil", "riot", "turn up", "chaos")

_last = [0.0]


def deezer(url):
    wait = 0.15 - (time.time() - _last[0])
    if wait > 0:
        time.sleep(wait)
    _last[0] = time.time()
    for attempt in range(3):
        try:
            d = json.loads(urllib.request.urlopen(url, timeout=20).read())
            if isinstance(d, dict) and d.get("error") and "Quota" in str(d["error"]):
                time.sleep(5); continue
            return d
        except Exception as e:
            if DEBUG[0] < 4: print("  Deezer-Fehler:", url[:80], e)
            time.sleep(2)
    return {}


def clean(title, artist):
    t = re.sub(r"\(.*?\)|\[.*?\]", "", title)
    t = re.sub(r"(?i)\b(official|music|video|audio|lyrics?|visualizer|prod\.?.*)$", "", t)
    if " - " in t:
        a, b = t.split(" - ", 1)
        t = b if norm(artist) in norm(a) or len(norm(a)) < len(norm(b)) else a
    return t.strip(" -|\"'")


DEBUG = [0]


def track_info(artist, title):
    t = clean(title, artist)
    if not norm(t):
        return None
    res = {}
    # erst einfache Suche (die erweiterte Syntax findet bei Deezer oft nichts), dann die genaue
    for q in ({"q": f"{artist} {t}", "limit": 15}, {"q": f'artist:"{artist}" track:"{t}"', "limit": 10}):
        res = deezer("https://api.deezer.com/search?" + urllib.parse.urlencode(q))
        if res.get("data"):
            break
    if DEBUG[0] < 6:
        DEBUG[0] += 1
        print("  Deezer-Test:", artist, "|", t, "->", str([(x.get("title"), (x.get("artist") or {}).get("name")) for x in res.get("data") or []][:4]))
    for r in (res.get("data") or []):
        rt = norm(re.sub(r"\(.*?\)|\[.*?\]", "", r.get("title") or ""))
        if norm((r.get("artist") or {}).get("name")) == norm(artist) and (rt == norm(t) or norm(t) in rt or rt in norm(t)):
            d = deezer(f"https://api.deezer.com/track/{r['id']}")
            return [float(d.get("bpm") or 0), float(d.get("gain") or 0)]
    return None


def moods():
    cache = {}
    if os.path.exists(CACHE):
        cache = json.load(open(CACHE))
    today = datetime.date.today().isoformat()
    rows = []  # (id, artist, title, bpm, gain)
    asked = 0
    for name in ARTISTS:
        p = os.path.join(SONGS, slug(name) + ".txt")
        if not os.path.exists(p):
            continue
        for line in open(p, encoding="utf-8"):
            if "|" not in line:
                continue
            vid, title = line.rstrip("\n").split("|", 1)
            c = cache.get(vid)
            # nicht gefundene Songs alle 14 Tage nochmal versuchen
            if c is None or (c.get("v") is None and c.get("d", "") < (datetime.date.today() - datetime.timedelta(days=14)).isoformat()):
                if asked < 2500:
                    asked += 1
                    cache[vid] = c = {"v": track_info(name, title), "d": today}
            v = (c or {}).get("v")
            rows.append((vid, name, title, v[0] if v else 0, v[1] if v else None))
    json.dump(cache, open(CACHE, "w"), separators=(",", ":"))
    print(f"Stimmungen: {len(rows)} Songs, {asked} neu bei Deezer abgefragt, "
          f"{sum(1 for r in rows if r[4] is not None)} mit Daten")

    def fold(b):
        if b <= 0:
            return None
        while b < 110: b *= 2
        while b > 200: b /= 2
        return b

    gains = [r[4] for r in rows if r[4] is not None]
    temps = [fold(r[3]) for r in rows if fold(r[3])]
    mg, sg = (sum(gains) / len(gains), (sum((g - sum(gains) / len(gains)) ** 2 for g in gains) / len(gains)) ** .5 or 1) if gains else (0, 1)
    mt, st = (sum(temps) / len(temps), (sum((t - sum(temps) / len(temps)) ** 2 for t in temps) / len(temps)) ** .5 or 1) if temps else (0, 1)
    scored = []
    for vid, name, title, bpm, gain in rows:
        e = BASE.get(name, 0)
        if gain is not None: e += .9 * (gain - mg) / sg
        f = fold(bpm)
        if f: e += .5 * (f - mt) / st
        low = title.lower()
        if any(w in low for w in CALM): e -= .6
        if any(w in low for w in HARD): e += .5
        scored.append((e, vid, low))
    scored.sort()
    n = len(scored)
    pick = lambda a, b: [s[1] for s in scored[int(n * a):int(n * b)]]
    night = pick(.08, .5) + [s[1] for s in scored if "night" in s[2] or "nacht" in s[2]]
    out = {
        "rage": pick(.7, 1.0),
        "auto": pick(.4, .82),
        "chill": pick(0, .32),
        "nacht": list(dict.fromkeys(night)),
        "bpm": {r[0]: round(fold(r[3])) for r in rows if fold(r[3])},
    }
    json.dump(out, open(os.path.join(ROOT, "moods.json"), "w"), separators=(",", ":"))
    print("  " + ", ".join(f"{k}: {len(v)}" for k, v in out.items()))


def drops():
    now = datetime.datetime.utcnow()
    today = now.date()
    found = {}

    def add(artist, title, date, cover):
        title = re.sub(r" - (Single|EP)$", "", title).strip()
        k = norm(artist) + "|" + norm(re.sub(r"\(.*?\)", "", title))
        if k not in found or (cover and not found[k].get("cover")):
            found[k] = {"artist": artist, "title": title, "date": date, "cover": cover}

    for name in ARTISTS:
        # Deezer: angekündigte Alben haben ein Datum in der Zukunft
        try:
            d = deezer("https://api.deezer.com/search/artist?" + urllib.parse.urlencode({"q": name}))
            a = next((x for x in d.get("data") or [] if norm(x.get("name")) == norm(name)), None)
            if a:
                for r in deezer(f"https://api.deezer.com/artist/{a['id']}/albums?limit=50").get("data") or []:
                    rd = r.get("release_date") or ""
                    if rd and datetime.date.fromisoformat(rd) >= today - datetime.timedelta(days=3):
                        add(name, r.get("title") or "", rd, r.get("cover_xl"))
        except Exception as e:
            print("  Deezer-Drops:", name, e)
        # iTunes: Pre-Order-Alben
        try:
            d = itunes_get("https://itunes.apple.com/search?" + urllib.parse.urlencode(
                {"term": name, "entity": "musicArtist", "limit": 5, "country": "US"}))
            a = next((x for x in d.get("results") or [] if norm(x.get("artistName")) == norm(name)), None)
            if a:
                u = f"https://itunes.apple.com/lookup?id={a['artistId']}&entity=album&limit=50&sort=recent&country=US"
                for r in itunes_get(u).get("results") or []:
                    rd = (r.get("releaseDate") or "")[:10]
                    if r.get("wrapperType") == "collection" and rd and \
                            datetime.date.fromisoformat(rd) >= today - datetime.timedelta(days=3):
                        art = (r.get("artworkUrl100") or "").replace("100x100bb", "600x600bb")
                        add(name, r.get("collectionName") or "", rd, art or None)
        except Exception as e:
            print("  iTunes-Drops:", name, e)
    # von Domi angesagte Drops (ohne Pre-Order): tools/drops_manual.json
    if os.path.exists(MANUAL):
        for m in json.load(open(MANUAL)):
            if datetime.date.fromisoformat(m["date"]) >= today - datetime.timedelta(days=3):
                add(m["artist"], m["title"], m["date"], m.get("cover"))

    out = []
    for dr in sorted(found.values(), key=lambda x: x["date"]):
        # schon draußen? Dann die Tracks aus der Releases-Liste dazu
        p = os.path.join(ALB, slug(dr["artist"]) + ".json")
        if os.path.exists(p):
            for al in json.load(open(p)):
                if norm(re.sub(r"\(.*?\)", "", al["title"])) == norm(re.sub(r"\(.*?\)", "", dr["title"])):
                    dr["tracks"] = al["tracks"]
                    dr["type"] = al.get("type", "album")
                    break
        out.append(dr)
        print(f"  Drop: {dr['artist']} – {dr['title']} am {dr['date']}" + (" (schon da)" if dr.get("tracks") else ""))
    json.dump(out, open(os.path.join(ROOT, "drops.json"), "w"), ensure_ascii=False, indent=0)


def releases():
    """assets/releases.json: die neuesten Songs/Releases deiner Rapper (mit Datum) für die Reihe "Neue Songs".
    Datum von Deezer + iTunes, abgespielt werden die offiziellen YouTube-Songs aus assets/albums."""
    today = datetime.date.today()
    key = lambda t: norm(re.sub(r"\(.*?\)|\[.*?\]", "", re.sub(r" - (Single|EP)$", "", t or "")))
    out = []
    for name in ("Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend", "slattuhs"):
        p = os.path.join(ALB, slug(name) + ".json")
        if not os.path.exists(p):
            continue
        dates = {}
        try:
            d = deezer("https://api.deezer.com/search/artist?" + urllib.parse.urlencode({"q": name}))
            a = next((x for x in d.get("data") or [] if norm(x.get("name")) == norm(name)), None)
            if a:
                for r in deezer(f"https://api.deezer.com/artist/{a['id']}/albums?limit=300").get("data") or []:
                    k, rd = key(r.get("title")), r.get("release_date") or ""
                    if k and rd:
                        dates[k] = min(rd, dates.get(k, rd))
        except Exception as e:
            print("  Deezer-Releases:", name, e)
        try:
            d = itunes_get("https://itunes.apple.com/search?" + urllib.parse.urlencode(
                {"term": name, "entity": "musicArtist", "limit": 5, "country": "US"}))
            a = next((x for x in d.get("results") or [] if norm(x.get("artistName")) == norm(name)), None)
            if a:
                u = f"https://itunes.apple.com/lookup?id={a['artistId']}&entity=album&limit=200&sort=recent&country=US"
                for r in itunes_get(u).get("results") or []:
                    k, rd = key(r.get("collectionName")), (r.get("releaseDate") or "")[:10]
                    if r.get("wrapperType") == "collection" and k and rd:
                        dates.setdefault(k, rd)
        except Exception as e:
            print("  iTunes-Releases:", name, e)
        mine = []
        for al in json.load(open(p)):
            rd = dates.get(key(al["title"]))
            if rd and rd <= today.isoformat():
                mine.append({"artist": name, "title": al["title"], "date": rd, "type": al.get("type", "album"),
                             "cover": al.get("cover"), "tracks": al["tracks"]})
        mine.sort(key=lambda x: x["date"], reverse=True)
        # pro Rapper: die letzten 3 aus dem letzten Jahr, mindestens aber das neueste
        year = (today - datetime.timedelta(days=365)).isoformat()
        pick = [x for x in mine[:3] if x["date"] >= year] or mine[:1]
        for x in pick:
            print(f"  Neu: {name} – {x['title']} ({x['date']})")
        if not pick:
            print(f"  Neu: {name} – kein Datum gefunden")
        out += pick
    out.sort(key=lambda x: x["date"], reverse=True)
    if out:   # bei API-Ausfall die alte Liste behalten
        json.dump(out, open(os.path.join(ROOT, "releases.json"), "w"), ensure_ascii=False, indent=0)


if __name__ == "__main__":
    moods()
    drops()
    releases()
