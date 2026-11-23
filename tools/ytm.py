#!/usr/bin/env python3
"""YouTube-Music-Verlauf für den Rückblick (läuft täglich in GitHub Actions).

Login: GitHub-Secret YTM_HEADERS = "Copy as cURL (bash)" oder "Copy request headers" einer
music.youtube.com-Anfrage. Gespeichert werden NUR Songs deiner Rapper (Datum, Video-ID, Rapper, Titel)
in app/src/main/assets/ytm.json – die App holt die Datei täglich und zählt sie im Rückblick mit.
"""
import json, os, re, sys, datetime

sys.path.insert(0, os.path.dirname(__file__))
from songs import ARTISTS, norm  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "ytm.json")


def headers_from(raw):
    h = {}
    if raw.lstrip().startswith("curl"):
        for k, v in re.findall(r"-H\s+'([^:']+):\s?([^']*)'", raw):
            h[k.strip().lower()] = v
        m = re.search(r"(?:-b|--cookie)\s+'([^']*)'", raw)
        if m:
            h["cookie"] = m.group(1)
    else:
        for line in raw.splitlines():
            if ":" in line and not line.startswith(":"):
                k, v = line.split(":", 1)
                h[k.strip().lower()] = v.strip()
    return h


def main():
    raw = os.environ.get("YTM_HEADERS", "")
    if not raw.strip():
        print("YouTube Music: kein Login (Secret YTM_HEADERS fehlt) – übersprungen")
        return
    h = headers_from(raw)
    if "cookie" not in h:
        print("YouTube Music: im Secret fehlt das Cookie"); return
    from ytmusicapi import YTMusic, setup
    keep = ("cookie", "x-goog-authuser", "user-agent", "accept-language", "x-goog-visitor-id", "origin")
    path = "/tmp/ytm_browser.json"
    setup(filepath=path, headers_raw="\n".join(f"{k}: {h[k]}" for k in keep if k in h))
    yt = YTMusic(path)
    hist = yt.get_history()
    print("YouTube Music: Verlauf", len(hist), "Einträge")

    today = datetime.date.today()
    day_of = {"today": today, "yesterday": today - datetime.timedelta(days=1)}
    data = json.load(open(OUT)) if os.path.exists(OUT) else {"days": {}}
    names = {norm(a): a for a in ARTISTS}
    added = 0
    for it in hist:
        d = day_of.get((it.get("played") or "").strip().lower())
        if d is None:
            continue                      # nur Heute/Gestern – ältere sind schon gezählt
        who = None
        for ar in it.get("artists") or []:
            if norm(ar.get("name")) in names:
                who = names[norm(ar.get("name"))]; break
        if not who or not it.get("videoId"):
            continue
        lst = data["days"].setdefault(d.isoformat(), [])
        if any(x[0] == it["videoId"] for x in lst):
            continue
        lst.append([it["videoId"], who, it.get("title") or ""])
        added += 1
    # nur ~400 Tage behalten
    cut = (today - datetime.timedelta(days=400)).isoformat()
    data["days"] = {k: v for k, v in sorted(data["days"].items()) if k >= cut}
    json.dump(data, open(OUT, "w"), ensure_ascii=False, separators=(",", ":"))
    print("YouTube Music:", added, "neue Songs deiner Rapper")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:   # Login abgelaufen o. ä. – nicht den ganzen Lauf abbrechen
        print("YouTube Music: Fehler –", type(e).__name__, str(e)[:200])
