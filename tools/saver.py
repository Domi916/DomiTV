#!/usr/bin/env python3
"""Bildschirmschoner-Fotos: Vorschaubilder (HD) der offiziellen Musikvideos deiner Rapper.
Filtert Platzhalter, zu dunkle, doppelte und textlastige Bilder; skaliert auf 1920x1080 und schärft nach.
Ergebnis: app/src/main/assets/saver/v_<rapper>_<n>.jpg
"""
import io, json, os, subprocess, sys, urllib.request
import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from songs import norm, slug  # noqa: E402

RAPPER = ["Ken Carson", "Bleood", "Protect", "OsamaSon", "Nettspend", "slattuhs"]
PER = 16
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "saver")


def search(q, n=40):
    r = subprocess.run(["yt-dlp", "--flat-playlist", "-J", f"ytsearch{n}:{q}"], capture_output=True, text=True, timeout=240)
    return (json.loads(r.stdout or "{}").get("entries") or [])


def thumb(vid):
    for q in ("maxresdefault", "sddefault"):
        try:
            b = urllib.request.urlopen(f"https://i.ytimg.com/vi/{vid}/{q}.jpg", timeout=20).read()
            im = Image.open(io.BytesIO(b)).convert("RGB")
            if im.width >= 640:
                return im
        except Exception:
            pass
    return None


def ahash(im):
    a = np.asarray(im.convert("L").resize((16, 9), Image.BILINEAR), np.float32)
    return (a > a.mean()).flatten()


def texty(im):
    """Grob: viele harte, waagerechte Kanten in einer Zeile = Schrift/Logo-Banner."""
    g = np.asarray(im.convert("L").resize((480, 270)), np.float32)
    e = np.abs(np.diff(g, axis=1)) > 60
    rows = e.mean(axis=1)
    return (rows > 0.22).sum() > 14


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.startswith("v_"):
            os.remove(os.path.join(OUT, f))
    for name in RAPPER:
        seen, n, ids = [], 0, []
        for q in (f"{name} official music video", f"{name} music video", f"{name} video"):
            for e in search(q):
                t = norm(e.get("title"))
                ch = norm(e.get("channel") or e.get("uploader") or "")
                if norm(name) not in t and norm(name) not in ch:
                    continue
                if any(w in t for w in ("reaction", "interview", "review", "type beat", "lyrics", "slowed", "live", "podcast", "tiktok")):
                    continue
                if e.get("id") and e["id"] not in ids:
                    ids.append(e["id"])
        print(name, len(ids), "Videos")
        for vid in ids:
            if n >= PER:
                break
            im = thumb(vid)
            if im is None:
                continue
            # schwarze Balken oben/unten abschneiden (4:3-Vorschaubilder)
            a = np.asarray(im.convert("L"), np.float32)
            rows = np.where(a.mean(axis=1) > 12)[0]
            if len(rows):
                im = im.crop((0, rows[0], im.width, rows[-1] + 1))
            if im.height < 300 or np.asarray(im.convert("L")).mean() < 28:
                continue
            if texty(im):
                print("  Text erkannt, übersprungen:", vid); continue
            h = ahash(im)
            if any((h != s).mean() < 0.12 for s in seen):
                continue
            seen.append(h)
            # auf 16:9 zuschneiden, 1920x1080, nachschärfen
            w, ht = im.size
            tw = min(w, int(ht * 16 / 9)); th = int(tw * 9 / 16)
            im = im.crop(((w - tw) // 2, (ht - th) // 2, (w - tw) // 2 + tw, (ht - th) // 2 + th))
            im = im.resize((1920, 1080), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=2.2, percent=70, threshold=2))
            im.save(os.path.join(OUT, f"v_{slug(name)}_{n:02d}.jpg"), quality=84, optimize=True)
            n += 1
        print("  ", n, "Bilder")


if __name__ == "__main__":
    main()
