#!/usr/bin/env python3
"""Full-detail OSM for the LiDAR core (every highway incl. sidewalks/footways, buildings incl. multipolygons, land use,
crossings, mapped trees, amenities) -> tools/lidar/cache/osm_core.json. Splits into small boxes and retries mirrors."""
import json, sys, time, urllib.parse, urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "cache" / "osm_core.json"
S, W, N, E = 29.6420, -82.3510, 29.6620, -82.3270
SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter",
           "https://maps.mail.ru/osm/tools/overpass/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]


def q(s, w, n, e):
    bb = f"({s},{w},{n},{e})"
    return (f'[out:json][timeout:90];(way["highway"]{bb};way["building"]{bb};relation["building"]{bb};way["amenity"="parking"]{bb};'
            f'way["landuse"]{bb};way["leisure"]{bb};way["natural"]{bb};way["barrier"]{bb};node["highway"~"crossing|traffic_signals|street_lamp|bus_stop"]{bb};'
            f'node["natural"="tree"]{bb};node["amenity"]{bb};node["shop"]{bb};node["leisure"]{bb};);out geom;')


def fetch(box, depth=0):
    cache = OUT.parent / ("osm_core_%s_%s_%s_%s.json" % box)
    if cache.exists():
        return json.loads(cache.read_text())
    data = urllib.parse.urlencode({"data": q(*box)}).encode()
    for t in range(8):
        srv = SERVERS[t % len(SERVERS)]
        try:
            req = urllib.request.Request(srv, data=data, headers={"User-Agent": "gnv-craft-mod/1.0 (github.com/rileycleavenger/gnv-craft)"})
            d = json.load(urllib.request.urlopen(req, timeout=120))
            if d.get("remark") and "error" in d["remark"].lower():
                raise RuntimeError(d["remark"])
            cache.write_text(json.dumps(d["elements"]))
            print("ok", box, len(d["elements"]), flush=True)
            return d["elements"]
        except Exception as ex:
            print("retry", box, srv, str(ex)[:80], flush=True)
            time.sleep(4 + t * 3)
    if depth > 2:
        sys.exit("failed %s" % (box,))
    s, w, n, e = box
    ms, mw = round((s + n) / 2, 5), round((w + e) / 2, 5)
    out = []
    for b in ((s, w, ms, mw), (s, mw, ms, e), (ms, w, n, mw), (ms, mw, n, e)):
        out += fetch(b, depth + 1)
    return out


els, seen = [], set()
ds, dw = (N - S) / 3, (E - W) / 3
for i in range(3):
    for j in range(3):
        box = (round(S + i * ds, 5), round(W + j * dw, 5), round(S + (i + 1) * ds, 5), round(W + (j + 1) * dw, 5))
        for el in fetch(box):
            k = (el["type"], el["id"])
            if k not in seen:
                seen.add(k); els.append(el)
OUT.write_text(json.dumps({"elements": els}))
print("wrote", len(els), "elements")
