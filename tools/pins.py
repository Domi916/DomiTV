#!/usr/bin/env python3
"""Pinterest-Bilder (IDs aus tools/pins/<rapper>.txt, im Browser gesammelt) laden: Original-Auflösung,
sonst 736px. Kandidaten + Übersicht nach /tmp/kandidaten (Artifact zum Aussuchen)."""
import os, io, urllib.request
from PIL import Image, ImageDraw

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130 Safari/537.36"
SRC = os.path.join(os.path.dirname(__file__), "pins")
OUT = "/tmp/kandidaten"
for f in sorted(os.listdir(SRC)):
    r = f[:-4]
    d = os.path.join(OUT, r); os.makedirs(d, exist_ok=True)
    keep = []
    for i, pid in enumerate(l.strip() for l in open(os.path.join(SRC, f)) if l.strip()):
        im = None
        for size in ("originals", "736x"):
            try:
                b = urllib.request.urlopen(urllib.request.Request(f"https://i.pinimg.com/{size}/{pid}", headers={"User-Agent": UA}), timeout=20).read()
                im = Image.open(io.BytesIO(b)).convert("RGB"); break
            except Exception as e:
                err = e
        if im is None:
            print(" fehlt", pid, err); continue
        fn = os.path.join(d, f"{i:02d}.jpg"); im.save(fn, quality=94)
        keep.append((fn, im.size))
    print(r, len(keep), "Bilder", [s for _, s in keep][:6])
    T = 240; cols = 8; rows = (len(keep) + cols - 1) // cols
    sh = Image.new("RGB", (cols * T, rows * (T + 18)), "white"); dr = ImageDraw.Draw(sh)
    for k, (fn, s) in enumerate(keep):
        im = Image.open(fn); im.thumbnail((T, T))
        sh.paste(im, ((k % cols) * T, (k // cols) * (T + 18)))
        dr.text(((k % cols) * T + 3, (k // cols) * (T + 18) + T + 2), f"{os.path.basename(fn)} {s[0]}x{s[1]}", fill="black")
    sh.save(os.path.join(OUT, f"sheet_{r}.jpg"), quality=82)
