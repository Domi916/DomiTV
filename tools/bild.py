#!/usr/bin/env python3
"""Rapper-Hintergrund bauen: Schrift weg, Qualität hoch (EDSR x4), Hintergrund freistellen (BiRefNet),
auf schwarz mit Farb-Glow setzen → 1920x1080 wie die anderen Rapper-Fotos.

python3 tools/bild.py <eingabe> <ausgabe.jpg> <farbe-hex> [text-box x0,y0,x1,y1 in Prozent]
"""
import sys, os, urllib.request
import numpy as np, cv2
from PIL import Image, ImageFilter

src, out, col = sys.argv[1], sys.argv[2], sys.argv[3]
box = [float(v) for v in sys.argv[4].split(",")] if len(sys.argv) > 4 else None

img = cv2.imread(src, cv2.IMREAD_COLOR)
h, w = img.shape[:2]

# 1) Schrift entfernen (Inpainting im Textbereich, nur helle/pinke Schrift-Pixel + Rand)
if box:
    x0, y0, x1, y1 = int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)
    roi = img[y0:y1, x0:x1]
    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
    # helle, eher entsättigte/rosa Pixel = Schrift
    m = ((hsv[..., 2] > 150) & (hsv[..., 1] < 120)).astype(np.uint8) * 255
    m = cv2.dilate(m, np.ones((7, 7), np.uint8), iterations=2)
    mask = np.zeros((h, w), np.uint8)
    mask[y0:y1, x0:x1] = m
    img = cv2.inpaint(img, mask, 9, cv2.INPAINT_TELEA)
    print("Schrift entfernt:", int(m.sum() / 255), "Pixel")

# 2) Qualität: EDSR x4 Super-Resolution
model = "/tmp/EDSR_x4.pb"
if not os.path.exists(model):
    urllib.request.urlretrieve("https://github.com/Saafke/EDSR_Tensorflow/raw/master/models/EDSR_x4.pb", model)
sr = cv2.dnn_superres.DnnSuperResImpl_create()
sr.readModel(model)
sr.setModel("edsr", 4)
big = sr.upsample(img)
print("hochskaliert:", big.shape)
# leicht nachschärfen
blur = cv2.GaussianBlur(big, (0, 0), 2.0)
big = cv2.addWeighted(big, 1.35, blur, -0.35, 0)

# 3) Hintergrund freistellen
from rembg import remove, new_session
try:
    sess = new_session("birefnet-portrait")
except Exception as e:
    print("birefnet nicht da, nehme isnet:", e)
    sess = new_session("isnet-general-use")
pil = Image.fromarray(cv2.cvtColor(big, cv2.COLOR_BGR2RGB))
cut = remove(pil, session=sess, post_process_mask=True)
alpha = cut.split()[-1].filter(ImageFilter.GaussianBlur(1.2))
cut.putalpha(alpha)

# 4) auf 1920x1080: schwarz + Glow in Rapperfarbe, Person rechts (wie die anderen Fotos)
W, H = 1920, 1080
r, g, b = int(col[0:2], 16), int(col[2:4], 16), int(col[4:6], 16)
yy, xx = np.mgrid[0:H, 0:W]
d = np.sqrt(((xx - 1380) / 900.0) ** 2 + ((yy - 520) / 700.0) ** 2)
glow = np.clip(1 - d, 0, 1) ** 2.2 * 0.55
bg = np.zeros((H, W, 3), np.float32)
bg[..., 0], bg[..., 1], bg[..., 2] = r * glow, g * glow, b * glow
bgi = Image.fromarray(bg.astype(np.uint8))
bb = cut.getbbox()
cut = cut.crop(bb)
sc = H * 1.02 / cut.height
cut = cut.resize((int(cut.width * sc), int(cut.height * sc)), Image.LANCZOS)
# Bildränder (wo das Foto abgeschnitten ist) weich ins Schwarz auslaufen lassen
al = np.asarray(cut.split()[-1]).astype(np.float32)
cw, ch = cut.size
ramp_x = np.clip(np.arange(cw) / (cw * 0.30), 0, 1) ** 1.4
ramp_y = np.clip((ch - np.arange(ch)) / (ch * 0.22), 0, 1) ** 1.2
al = al * ramp_x[None, :] * ramp_y[:, None]
cut.putalpha(Image.fromarray(al.astype(np.uint8)))
x = W - cut.width + 40
bgi.paste(cut, (x, H - cut.height + 10), cut)
bgi.save(out, quality=93)
print("fertig:", out, cut.size)
