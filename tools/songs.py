#!/usr/bin/env python3
"""Holt für jeden Rapper ALLE Songs (Alben + Singles, offizielle Audio-Versionen) von YouTube
und schreibt sie nach app/src/main/assets/songs/<name>.txt  (Zeile: videoId|Titel).
Läuft in GitHub Actions (siehe .github/workflows/songs.yml)."""
import json, os, re, subprocess, sys

RELEASE_ARTISTS = {"Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend", "che", "Prettifun"}
ARTISTS = ["Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend",
           "Destroy Lonely", "Playboi Carti", "che", "Yeat", "xaviersobased", "Prettifun"]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "songs")
ALB = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "albums")

def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", (s or "").lower()).strip()

def slug(s):
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")

def ytdlp(*args, timeout=180):
    r = subprocess.run(["yt-dlp", "--flat-playlist", "-J", "--no-warnings", *args],
                       capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0 or not r.stdout.strip():
        raise RuntimeError(r.stderr.strip()[-300:])
    return json.loads(r.stdout)

def find_channel(name):
    """Kanal des Künstlers über die Suche finden (der mit den meisten Treffern und passendem Namen)."""
    counts = {}
    for q in (f"{name} official audio", f"{name} topic", f"{name} song"):
        try:
            d = ytdlp(f"ytsearch15:{q}")
        except Exception as e:
            print("  Suche fehlgeschlagen:", e); continue
        for e in d.get("entries") or []:
            ch, cid = e.get("channel"), e.get("channel_id")
            if not cid: continue
            if norm(ch).replace(" topic", "") == norm(name):
                counts[cid] = counts.get(cid, 0) + 1
    return sorted(counts, key=lambda k: -counts[k])

def songs_of(name):
    for cid in find_channel(name):
        try:
            rel = ytdlp(f"https://www.youtube.com/channel/{cid}/releases")
        except Exception as e:
            print("  kein Releases-Tab bei", cid, e); continue
        songs, seen = [], set()
        albums = []
        for pl in rel.get("entries") or []:
            url = pl.get("url") or ""
            if "list=" not in url: continue
            try:
                d = ytdlp(url, timeout=120)
            except Exception as e:
                print("  Playlist-Fehler", pl.get("title"), e); continue
            tracks = [t.get("id") for t in (d.get("entries") or []) if t.get("id")]
            # Nur für deine Rapper (+ che) alle Releases mit Cover: Alben, EPs und Singles
            if tracks and name in RELEASE_ARTISTS:
                typ = "single" if len(tracks) == 1 else "ep" if len(tracks) <= 6 else "album"
                al = {"title": pl.get("title") or d.get("title") or "", "tracks": tracks, "type": typ}
                # Cover: Vorschaubild eines "Official Audio"-Songs zeigt das Albumcover (Musikvideos nicht)
                ents = [t for t in (d.get("entries") or []) if t.get("id")]
                def is_audio(t):
                    ti = (t.get("title") or "").lower()
                    return "video" not in ti and "visualizer" not in ti and "live" not in ti
                audio = [t["id"] for t in ents if is_audio(t)]
                pick = audio[0] if audio else tracks[0]
                fix = os.path.join(FIX, f"{slug(name)}_{norm(al['title']).replace(' ', '')}.jpg")
                urls = ["file://" + os.path.abspath(fix)] if os.path.exists(fix) else itunes_art(name, al["title"])
                cov = cover(urls, pick, f"{slug(name)}_{len(albums)}.jpg")
                if cov: al["cover"] = cov
                albums.append(al)
            for t in d.get("entries") or []:
                vid, title = t.get("id"), (t.get("title") or "").replace("|", "/")
                key = norm(re.sub(r"\(.*?\)|\[.*?\]", "", title))
                if not vid or key in seen: continue
                seen.add(key)
                songs.append((vid, title))
        print(f"  {name}: Kanal {cid}, {len(rel.get('entries') or [])} Releases, {len(songs)} Songs")
        if songs:
            ALBUMS[name] = albums
            return songs
    return []

ALBUMS = {}
# Von Domi geschickte Cover: tools/cover_fix/<rapper>_<albumtitel ohne Leer-/Sonderzeichen>.jpg
FIX = os.path.join(os.path.dirname(__file__), "cover_fix")
COV = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "covers")

def itunes_art(artist, album):
    """Offizielles Albumcover über die öffentliche iTunes-Suche (600x600)."""
    import urllib.request, urllib.parse
    try:
        q = urllib.parse.urlencode({"term": f"{artist} {album}", "entity": "album", "limit": 10, "country": "US"})
        data = json.loads(urllib.request.urlopen("https://itunes.apple.com/search?" + q, timeout=20).read())
        na, nb = norm(artist), norm(re.sub(r"\(.*?\)|\[.*?\]", "", album))
        best = None
        for r in data.get("results") or []:
            ra, rb = norm(r.get("artistName")), norm(re.sub(r"\(.*?\)|\[.*?\]", "", r.get("collectionName") or ""))
            if na not in ra: continue
            if rb == nb or (nb and (nb in rb or rb in nb)):
                best = r; break
        if best and best.get("artworkUrl100"):
            return [best["artworkUrl100"].replace("100x100bb", "600x600bb")]
    except Exception as e:
        print("  iTunes:", e)
    return []

def cover(urls, video_id, fname):
    """Albumcover: zuerst das Bild der Album-Playlist (echtes Cover), sonst Vorschaubild des ersten Songs."""
    import io, urllib.request
    from PIL import Image
    cands = list(urls) + [f"https://i.ytimg.com/vi/{video_id}/{q}.jpg" for q in ("maxresdefault", "hqdefault")]
    for u in cands:
        try:
            data = urllib.request.urlopen(u, timeout=20).read()
            im = Image.open(io.BytesIO(data)).convert("RGB")
            w, h = im.size
            if "hqdefault" in u:
                # 4:3 mit schwarzen Balken: 16:9-Bereich in der Mitte
                vh = int(w * 9 / 16); top = (h - vh) // 2
                im = im.crop((0, top, w, top + vh)); w, h = im.size
            side = min(w, h)
            left = (w - side) // 2
            im = im.crop((left, (h - side) // 2, left + side, (h - side) // 2 + side))
            w = h = side
            left = 0
            im = im.resize((280, 280), Image.LANCZOS)
            os.makedirs(COV, exist_ok=True)
            im.save(os.path.join(COV, fname), quality=86)
            return "covers/" + fname
        except Exception:
            continue
    return None

def main():
    os.makedirs(OUT, exist_ok=True)
    ok = 0
    for name in ARTISTS:
        print(name)
        try:
            s = songs_of(name)
        except Exception as e:
            print("  Fehler:", e); s = []
        if len(s) >= 5:
            with open(os.path.join(OUT, slug(name) + ".txt"), "w", encoding="utf-8") as f:
                f.write("\n".join(f"{v}|{t}" for v, t in s) + "\n")
            if ALBUMS.get(name):
                os.makedirs(ALB, exist_ok=True)
                with open(os.path.join(ALB, slug(name) + ".json"), "w", encoding="utf-8") as f:
                    json.dump(ALBUMS[name], f, ensure_ascii=False)
                print(f"  {len(ALBUMS[name])} Alben")
            ok += 1
        else:
            print("  zu wenig Songs, alte Liste bleibt")
    print("fertig:", ok, "von", len(ARTISTS))

if __name__ == "__main__":
    main()
