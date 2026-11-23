#!/usr/bin/env python3
"""Community-Fotos deiner Rapper sammeln (Reddit-Subs + Pinterest), nur Nahaufnahmen:
Gesichtserkennung (OpenCV-DNN) – das größte Gesicht muss mind. 16 % der Bildhöhe haben.
Kandidaten landen in /tmp/kandidaten/<rapper>/ + Übersicht, als Artifact zum Aussuchen.
"""
import io, json, os, re, sys, time, urllib.request, urllib.parse, hashlib
import numpy as np, cv2
from PIL import Image, ImageDraw

RAPPER = {
    "Ken Carson": ["KenCarson"], "Bleood": ["bleood"], "Protect": [], "OsamaSon": ["OsamaSon"],
    "Nettspend": ["nettspend"], "slattuhs": [],
}
OUT = "/tmp/kandidaten"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

proto = "/tmp/deploy.prototxt"; model = "/tmp/res10.caffemodel"
if not os.path.exists(model):
    urllib.request.urlretrieve("https://raw.githubusercontent.com/opencv/opencv/master/samples/dnn/face_detector/deploy.prototxt", proto)
    urllib.request.urlretrieve("https://raw.githubusercontent.com/opencv/opencv_3rdparty/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel", model)
net = cv2.dnn.readNetFromCaffe(proto, model)


def get(url, js=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json" if js else "*/*"})
    b = urllib.request.urlopen(req, timeout=25).read()
    return json.loads(b) if js else b


def reddit(sub, q=None):
    urls = []
    for kind in ("top.json?t=all&limit=100", "top.json?t=year&limit=100", "hot.json?limit=100"):
        for host in ("https://old.reddit.com", "https://www.reddit.com"):
            try:
                d = get(f"{host}/r/{sub}/{kind}", js=True)
                for c in d["data"]["children"]:
                    p = c["data"]
                    u = p.get("url_overridden_by_dest") or p.get("url") or ""
                    if re.search(r"\.(jpe?g|png|webp)$", u):
                        urls.append(u)
                    for m in (p.get("media_metadata") or {}).values():
                        s = (m.get("s") or {}).get("u")
                        if s: urls.append(s.replace("&amp;", "&"))
                break
            except Exception as e:
                print("   reddit", sub, host, kind, type(e).__name__, str(e)[:80])
            time.sleep(2)
    return urls


def pinterest(q):
    urls = []
    data = {"options": {"query": q, "scope": "pins", "page_size": 100}, "context": {}}
    u = "https://www.pinterest.com/resource/BaseSearchResource/get/?" + urllib.parse.urlencode(
        {"source_url": "/search/pins/?q=" + urllib.parse.quote(q), "data": json.dumps(data)})
    try:
        d = get(u, js=True)
        for r in (d.get("resource_response") or {}).get("data", {}).get("results", []) or []:
            im = (r.get("images") or {}).get("orig") or {}
            if im.get("url"): urls.append(im["url"])
    except Exception as e:
        print("   pinterest", q, type(e).__name__, str(e)[:80])
    return urls


def face_frac(img):
    h, w = img.shape[:2]
    blob = cv2.dnn.blobFromImage(cv2.resize(img, (300, 300)), 1.0, (300, 300), (104, 177, 123))
    net.setInput(blob)
    det = net.forward()[0, 0]
    best = 0
    for d in det:
        if d[2] > 0.6:
            best = max(best, (d[6] - d[4]))
    return best


os.makedirs(OUT, exist_ok=True)
for name, subs in RAPPER.items():
    d = os.path.join(OUT, name.replace(" ", "_").lower())
    os.makedirs(d, exist_ok=True)
    cands = []
    for s in subs:
        cands += [("reddit", u) for u in reddit(s)]
    for q in (f"{name} rapper", f"{name} aesthetic", f"{name} pfp", f"{name} wallpaper"):
        cands += [("pinterest", u) for u in pinterest(q)]
        time.sleep(1.5)
    print(name, "Kandidaten:", len(cands), {k: sum(1 for c in cands if c[0] == k) for k in ("reddit", "pinterest")})
    seen, keep = set(), []
    for src, u in cands:
        if len(keep) >= 60: break
        try:
            b = get(u)
        except Exception:
            continue
        hsh = hashlib.md5(b).hexdigest()
        if hsh in seen: continue
        seen.add(hsh)
        arr = cv2.imdecode(np.frombuffer(b, np.uint8), cv2.IMREAD_COLOR)
        if arr is None: continue
        h, w = arr.shape[:2]
        if min(h, w) < 600: continue
        f = face_frac(arr)
        if f < 0.16: continue
        fn = os.path.join(d, f"{len(keep):02d}_{src[0]}.jpg")
        cv2.imwrite(fn, arr, [cv2.IMWRITE_JPEG_QUALITY, 92])
        keep.append(fn)
    print("  behalten:", len(keep))
    # Übersicht
    if keep:
        T = 220; cols = 10; rows = (len(keep) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * T, rows * (T + 16)), "white"); dr = ImageDraw.Draw(sheet)
        for i, fn in enumerate(keep):
            im = Image.open(fn).convert("RGB"); im.thumbnail((T, T))
            sheet.paste(im, ((i % cols) * T, (i // cols) * (T + 16)))
            dr.text(((i % cols) * T + 3, (i // cols) * (T + 16) + T), os.path.basename(fn), fill="black")
        sheet.save(os.path.join(OUT, f"sheet_{os.path.basename(d)}.jpg"), quality=80)
