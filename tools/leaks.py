#!/usr/bin/env python3
"""Frische Leaks (unreleased Songs) deiner Rapper auf YouTube, für die Reihe "Neue Songs".

Ein Leak zählt nur, wenn er wirklich neu ist:
  * das YouTube-Video ist höchstens NEW_DAYS Tage alt,
  * derselbe Song war vorher nirgends auf YouTube (sonst ist es nur ein Re-Upload eines alten Leaks),
  * der Song ist (noch) nicht offiziell erschienen.
Gemerkt wird in tools/leaks_cache.json: wann ein Song zum ersten Mal gesehen wurde und ob er alt ist.
"""
import datetime, json, os, re, subprocess, sys, time

sys.path.insert(0, os.path.dirname(__file__))
from songs import norm, slug  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
CACHE = os.path.join(os.path.dirname(__file__), "leaks_cache.json")
OWN = ["Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend", "slattuhs"]
NEW_DAYS = 21          # so lange ist ein Leak "neu"
OLD_DAYS = 35          # gab es den Song schon vor so vielen Tagen auf YouTube -> alter Leak
PER_RAPPER = 3

LEAK = re.compile(r"\b(unreleased|unrealeased|unrelased|unreleasd|leak|leaked|leaks|unrls)\b", re.I)
BAD = re.compile(r"\b(snippets?|preview|live|reaction|reacts?|reacting|review|type ?beat|instrumental|remix|slowed|sped|speed ?up|"
                 r"reverb|nightcore|8d|bass ?boost(ed)?|tracker|compilation|playlist|mix|full album|all songs|every|ranking|"
                 r"ranked|ranks?|tier ?list|interview|podcast|clips?|tiktok|edits?|loop|acapella|cover|tutorial|how to|"
                 r"vlog|album leak|tape leak|leaked album|songs|top \d+|\d+ songs|minutes|hours?|1 hour)\b", re.I)
QUALITY = re.compile(r"\b(cdq|hq|lq|og|v\d|full version|og version|og file|best quality|mastered|unmastered|song)\b", re.I)

DEBUG = "--probe" in sys.argv


def log(*a):
    print(*a, flush=True)


def search(q, n, by_date):
    import urllib.parse
    # nur Videos aus diesem Monat (YouTube-Filter "Upload-Datum: Diesen Monat", "Typ: Video")
    src = ("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + "&sp=EgQIBBAB") if by_date \
        else f"ytsearch{n}:{q}"
    try:
        r = subprocess.run(["yt-dlp", "--flat-playlist", "-J", "--no-warnings", "--playlist-end", str(n),
                            "--extractor-args", "youtubetab:approximate_date", src],
                           capture_output=True, text=True, timeout=150)
        if r.returncode != 0 or not r.stdout.strip():
            log("  Suche fehlgeschlagen:", q, r.stderr.strip()[-200:])
            return []
        return json.loads(r.stdout).get("entries") or []
    except Exception as e:
        log("  Suche fehlgeschlagen:", q, e)
        return []


def when(e):
    """Upload-Zeitpunkt (ungefähr, aus "vor 3 Tagen") als datetime, sonst None."""
    for k in ("timestamp", "release_timestamp"):
        if e.get(k):
            return datetime.datetime.fromtimestamp(int(e[k]), datetime.timezone.utc).replace(tzinfo=None)
    d = e.get("upload_date")
    if d and len(d) == 8:
        return datetime.datetime.strptime(d, "%Y%m%d")
    return None


def song_name(title, artist):
    """"Ken Carson - Song Name (Unreleased) [prod. x]" -> "Song Name" """
    title = title or ""
    q = re.search(r'["“]([^"“”]{2,40})["”]', title)
    t = q.group(1) if q else title
    t = re.sub(r"[\(\[\{【][^\)\]\}】]*[\)\]\}】]", " ", t)
    t = re.sub(r"(?i)\b(prod\.?|prod by|produced by)\b.*$", " ", t)
    t = re.sub(r"(?i)\s(ft\.?|feat\.?|featuring|w/|with)\s.*$", " ", t)
    t = re.sub(r"[\"“”‘’*#]", "", t)
    a = norm(artist)
    parts = [p.strip() for p in re.split(r"\s+[-–—|:•]\s+|\s*[–—|•]\s*|\s-\s?|\s?-\s", t) if p.strip()]
    if len(parts) > 1:
        # "Künstler - Song": alles nach dem Teil mit dem Künstlernamen, sonst die Teile ohne ihn
        i = next((k for k, p in enumerate(parts) if a in norm(p)), -1)
        rest = parts[i + 1:] if i >= 0 and i < len(parts) - 1 else [p for p in parts if a not in norm(p)]
        t = " ".join(rest) if rest else parts[-1]
    t = re.sub("(?i)" + r"\s*".join(re.escape(w) for w in artist.split()), " ", t)
    t = LEAK.sub(" ", t)
    t = QUALITY.sub(" ", t)
    t = re.sub(r"[^\w\s&!?.$'-]", " ", t)
    t = re.sub(r"\s+", " ", t).strip(" -.'")
    return t


JOIN = {"x", "and", "with", "ft", "feat", "featuring", "&", ",", "-", "–", "—", "|", ":", "•", "~"}


def credited(title, artist):
    """Steht der Rapper als Künstler im Titel ("Ken Carson - Song", "Song - Ken Carson", "Carti, Ken Carson - Song")?
    Verhindert Treffer wie "Protect Your Home: Water Leak Alert"."""
    t = re.sub(r"[\(\[\{【][^\)\]\}】]*[\)\]\}】]", " ", title or "")
    t = LEAK.sub(" ", t).lower().replace(".", "")
    tok = re.findall(r"[a-z0-9$'_]+|[-–—|:•~&,]", t)
    art = re.findall(r"[a-z0-9$'_]+", artist.lower())
    n = len(art)
    for i in range(len(tok) - n + 1):
        if tok[i:i + n] != art:
            continue
        before = tok[i - 1] if i > 0 else None
        after = tok[i + n] if i + n < len(tok) else None
        if (before is None or before in JOIN) and (after is None or after in JOIN):
            return True
        # "Ken Carson unreleased "Song"": Songname in Anführungszeichen
        if before is None and re.search(r'["“][^"“”]{2,40}["”]', title or ""):
            return True
    return False


def official_keys(artist):
    """Normierte Titel aller offiziellen Songs des Rappers (Songliste + Releases)."""
    keys = set()
    p = os.path.join(ROOT, "songs", slug(artist) + ".txt")
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            if "|" in line:
                keys.add(norm(song_name(line.split("|", 1)[1], artist)))
    p = os.path.join(ROOT, "albums", slug(artist) + ".json")
    if os.path.exists(p):
        for al in json.load(open(p)):
            keys.add(norm(song_name(al.get("title"), artist)))
    keys.discard("")
    return keys


def leaks():
    now = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
    today = now.date().isoformat()
    cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    out = []
    for name in OWN:
        a = norm(name)
        official = official_keys(name)
        seen = {}
        res = search(f"{name} unreleased", 40, True) + search(f"{name} leak", 40, True) + search(f"{name} leaked", 25, True)
        dated = sum(1 for e in res if when(e))
        log(f"{name}: {len(res)} Treffer, {dated} mit Datum")
        for e in res:
            t, vid = e.get("title") or "", e.get("id")
            dt, dur = when(e), e.get("duration")
            why = None
            if not vid or len(vid) != 11: why = "keine ID"
            elif not credited(t, name): why = "anderer Künstler"
            elif not LEAK.search(t): why = "kein Leak"
            elif BAD.search(t): why = "kein Song"
            elif dur and not (80 <= dur <= 420): why = f"Länge {dur}s"
            elif not dt: why = "kein Datum"
            elif (now - dt).days > NEW_DAYS: why = f"{(now - dt).days} Tage alt"
            song = song_name(t, name)
            k = norm(song)
            if not why and len(k) < 2: why = "kein Titel"
            if not why and (k in official or any(len(k) > 4 and (k in o or o in k) for o in official if len(o) > 4)):
                why = "schon offiziell"
            if DEBUG:
                log(f"   {'OK ' if not why else '-- '}{(dt.date().isoformat() if dt else '?'):10s} {str(dur):>5} "
                    f"{(e.get('view_count') or 0):>8}  {t[:70]}  -> {song!r}" + (f"  [{why}]" if why else ""))
            if why:
                continue
            s = seen.get(k)
            if s is None or (e.get("view_count") or 0) > (s["views"] or 0):
                seen[k] = {"title": song, "id": vid, "views": e.get("view_count") or 0,
                           "date": min(dt, s["date"]) if s else dt}
            elif dt < s["date"]:
                s["date"] = dt
        picked = 0
        for k, s in sorted(seen.items(), key=lambda kv: -kv[1]["date"].timestamp()):
            if picked >= PER_RAPPER:
                break
            ck = a + "|" + k
            c = cache.get(ck)
            if c is None:
                # gab es den Song schon früher auf YouTube? Dann ist es ein alter Leak (Re-Upload)
                first = s["date"]
                for e in search(f"{name} {s['title']}", 20, False):
                    ek = norm(song_name(e.get("title"), name))
                    edt = when(e)
                    edur = e.get("duration")
                    # Snippets zählen nicht: ein Song kann vorher angeteasert und erst jetzt geleakt sein
                    if BAD.search(e.get("title") or "") or (edur and edur < 80):
                        continue
                    if edt and ek and (ek == k or (len(k) > 4 and (k in ek or ek in k))) and credited(e.get("title"), name):
                        first = min(first, edt)
                old = (now - first).days > OLD_DAYS
                c = cache[ck] = {"first": first.date().isoformat(), "old": old, "seen": today}
                log(f"  {'ALT ' if old else 'NEU '} {name} – {s['title']} (zuerst {c['first']})")
            c["seen"] = today
            if c["old"]:
                continue
            if (now.date() - datetime.date.fromisoformat(c["first"])).days > NEW_DAYS:
                continue
            out.append({"artist": name, "title": s["title"], "date": c["first"], "type": "leak",
                        "cover": None, "tracks": [s["id"]]})
            picked += 1
            log(f"  Leak: {name} – {s['title']} ({c['first']}, {s['id']})")
    # Cache klein halten: was 120 Tage nicht mehr auftauchte, vergessen
    lim = (now.date() - datetime.timedelta(days=120)).isoformat()
    cache = {k: v for k, v in cache.items() if v.get("seen", "") >= lim}
    json.dump(cache, open(CACHE, "w"), ensure_ascii=False, indent=0, sort_keys=True)
    return out


if __name__ == "__main__":
    r = leaks()
    log(json.dumps(r, ensure_ascii=False, indent=1))
