#!/usr/bin/env python3
"""Fetch Gainesville OSM data (Overpass) and bake it into the compact map file the mod loads.

Usage: python3 tools/osm/fetch_gnv.py   (writes tools/osm/raw.json cache + the resource map.json.gz)
Origin: 13th St & W University Ave. 1 block = 1 m. +X east, +Z south.
Data (c) OpenStreetMap contributors, ODbL.
"""
import gzip, json, math, os, sys, urllib.request, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
RAW = os.path.join(HERE, "raw.json")
OUT = os.path.join(ROOT, "src/main/resources/data/gnvcraft/gnv_map/map.json.gz")
ORIGIN = (29.6521, -82.3393)  # lat, lon of NW 13th St & W University Ave, read off the road data
# Core bbox: UF campus, Midtown, downtown. Airport corridor handled as a separate small bbox.
def _tiles():
    t = []
    lat = 29.630
    while lat < 29.668 - 1e-9:
        lon = -82.372
        while lon < -82.300 - 1e-9:
            t.append((round(lat, 4), round(lon, 4), round(lat + 0.0095, 4), round(lon + 0.018, 4)))
            lon += 0.018
        lat += 0.0095
    t.append((29.678, -82.285, 29.700, -82.260))
    return t
BBOXES = _tiles()
SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
           "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]

def query(b):
    s, w, n, e = b
    return f"""[out:json][timeout:180];
(
 way["building"]({s},{w},{n},{e});
 way["highway"]["highway"!~"footway|path|steps|cycleway|service|pedestrian|corridor"]({s},{w},{n},{e});
 way["highway"="service"]["service"!~"driveway|parking_aisle"]({s},{w},{n},{e});
 way["natural"="water"]({s},{w},{n},{e});
 way["leisure"~"park|pitch|stadium"]({s},{w},{n},{e});
 way["landuse"~"grass|recreation_ground"]({s},{w},{n},{e});
 way["amenity"="parking"]({s},{w},{n},{e});
 way["aeroway"]({s},{w},{n},{e});
 node["amenity"~"bar|pub|nightclub|fast_food|restaurant|cafe"]({s},{w},{n},{e});
 node["aeroway"~"terminal|aerodrome"]({s},{w},{n},{e});
 node["tourism"~"attraction"]({s},{w},{n},{e});
);
out geom;"""

def fetch_tile(b, depth=0):
    """Fetch one bbox, splitting it into four quarters when the Overpass servers time out on it."""
    import time
    data = urllib.parse.urlencode({"data": query(b)}).encode()
    tile_file = os.path.join(HERE, "tiles", "%s_%s_%s_%s.json" % b)
    os.makedirs(os.path.dirname(tile_file), exist_ok=True)
    if os.path.exists(tile_file):
        return json.load(open(tile_file))
    for attempt in range(4):
        srv = SERVERS[attempt % len(SERVERS)]
        try:
            req = urllib.request.Request(srv, data=data, headers={"User-Agent": "gnv-craft-mod/1.0 (github.com/rileycleavenger/gnv-craft)"})
            got = json.load(urllib.request.urlopen(req, timeout=120))["elements"]
            json.dump(got, open(tile_file, "w"))
            print("tile", b, len(got), file=sys.stderr)
            return got
        except Exception as ex:
            print("retry", b, srv, ex, file=sys.stderr)
            time.sleep(5 + attempt * 5)
    if depth >= 3:
        sys.exit("tile failed: %s" % (b,))
    s_, w, n, e = b
    ms, mw = round((s_ + n) / 2, 5), round((w + e) / 2, 5)
    print("splitting", b, file=sys.stderr)
    out = []
    for q in ((s_, w, ms, mw), (s_, mw, ms, e), (ms, w, n, mw), (ms, mw, n, e)):
        out += fetch_tile(q, depth + 1)
    json.dump(out, open(tile_file, "w"))
    return out

def fetch():
    if os.path.exists(RAW):
        return json.load(open(RAW))
    els, seen = [], set()
    for b in BBOXES:
        old = os.path.join(HERE, "tiles", "%s_%s.json" % (b[0], b[1]))
        new_name = os.path.join(HERE, "tiles", "%s_%s_%s_%s.json" % b)
        if os.path.exists(old) and not os.path.exists(new_name):
            os.rename(old, new_name)
        for e in fetch_tile(b):
            k = (e["type"], e["id"])
            if k not in seen:
                seen.add(k); els.append(e)
    json.dump({"elements": els}, open(RAW, "w"))
    return {"elements": els}

def project(lat, lon, o):
    x = (lon - o[1]) * 111320 * math.cos(math.radians(o[0]))
    z = -(lat - o[0]) * 110574
    return [round(x), round(z)]

def main():
    raw = fetch()["elements"]
    els = raw
    # refine origin: Chick-fil-A near 13th & University if present
    o = ORIGIN
    for e in els:
        if e["type"] == "node" and "chick" in e.get("tags", {}).get("name", "").lower() and abs(e["lat"] - o[0]) < 0.01 and abs(e["lon"] - o[1]) < 0.01:
            print("Chick-fil-A at", e["lat"], e["lon"], file=sys.stderr)
    out = {"origin": list(o), "roads": [], "areas": [], "buildings": [], "landmarks": []}
    road_w = {"motorway": 12, "trunk": 11, "primary": 11, "secondary": 9, "tertiary": 8, "residential": 7, "unclassified": 7, "service": 5, "living_street": 6}
    for e in els:
        t = e.get("tags", {})
        if e["type"] == "node":
            name = t.get("name")
            kind = t.get("amenity") or t.get("aeroway") or t.get("tourism")
            if name or kind:
                out["landmarks"].append({"id": e["id"], "name": name or kind, "kind": kind, "p": project(e["lat"], e["lon"], o)})
            continue
        geom = e.get("geometry")
        if not geom or len(geom) < 2:
            continue
        if t.get("aeroway") in ("aerodrome", "terminal") and t.get("name"):
            clat = sum(g["lat"] for g in geom) / len(geom)
            clon = sum(g["lon"] for g in geom) / len(geom)
            out["landmarks"].append({"id": e["id"], "name": t["name"], "kind": "airport_" + t["aeroway"], "p": project(clat, clon, o)})
        if t.get("aeroway") == "apron":
            clat = sum(g["lat"] for g in geom) / len(geom)
            clon = sum(g["lon"] for g in geom) / len(geom)
            out["landmarks"].append({"id": e["id"], "name": "GNV apron", "kind": "airport_apron", "p": project(clat, clon, o)})
        pts = [project(g["lat"], g["lon"], o) for g in geom]
        if "building" in t:
            levels = t.get("building:levels")
            try:
                lv = int(float(levels)) if levels else 0
            except ValueError:
                lv = 0
            kind = t.get("amenity") or t.get("shop") or t.get("tourism") or t.get("building")
            out["buildings"].append({"id": e["id"], "name": t.get("name"), "kind": kind, "levels": lv, "poly": pts})
        elif "highway" in t:
            out["roads"].append({"id": e["id"], "class": t["highway"], "w": road_w.get(t["highway"], 6), "name": t.get("name"), "pts": pts})
        else:
            kind = ("water" if t.get("natural") == "water" else "parking" if t.get("amenity") == "parking"
                    else "aero_" + t["aeroway"] if "aeroway" in t else "pitch" if t.get("leisure") in ("pitch", "stadium") else "grass")
            out["areas"].append({"id": e["id"], "kind": kind, "poly": pts, "name": t.get("name")})
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wt") as f:
        json.dump(out, f, separators=(",", ":"))
    print({k: len(v) for k, v in out.items() if isinstance(v, list)}, os.path.getsize(OUT) // 1024, "KB", file=sys.stderr)

main()
