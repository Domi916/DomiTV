#!/usr/bin/env python3
"""Holt für jeden Rapper ALLE Songs (Alben + Singles, offizielle Audio-Versionen) von YouTube
und schreibt sie nach app/src/main/assets/songs/<name>.txt  (Zeile: videoId|Titel).
Läuft in GitHub Actions (siehe .github/workflows/songs.yml)."""
import json, os, re, subprocess, sys

ARTISTS = ["Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend",
           "Destroy Lonely", "Playboi Carti", "che", "Yeat", "xaviersobased"]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "songs")

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
        for pl in rel.get("entries") or []:
            url = pl.get("url") or ""
            if "list=" not in url: continue
            try:
                d = ytdlp(url, timeout=120)
            except Exception as e:
                print("  Playlist-Fehler", pl.get("title"), e); continue
            for t in d.get("entries") or []:
                vid, title = t.get("id"), (t.get("title") or "").replace("|", "/")
                key = norm(re.sub(r"\(.*?\)|\[.*?\]", "", title))
                if not vid or key in seen: continue
                seen.add(key)
                songs.append((vid, title))
        print(f"  {name}: Kanal {cid}, {len(rel.get('entries') or [])} Releases, {len(songs)} Songs")
        if songs:
            return songs
    return []

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
            ok += 1
        else:
            print("  zu wenig Songs, alte Liste bleibt")
    print("fertig:", ok, "von", len(ARTISTS))

if __name__ == "__main__":
    main()
