#!/usr/bin/env python3
"""Cut the V1 tiles out of a Geofabrik OpenStreetMap extract (much faster and more reliable than Overpass).

  curl -L -o tools/lidar/cache/florida.osm.pbf https://download.geofabrik.de/north-america/us/florida-latest.osm.pbf
  tools/.venv/bin/python tools/lidar/extract_osm_pbf.py

Writes tools/lidar/cache/osm/t_<tx>_<tz>.json in the same element format as the Overpass fetcher (out geom).
"""
import json, sys
from pathlib import Path
import osmium
sys.path.insert(0, str(Path(__file__).resolve().parent))
from area import tiles, TILE
from fetch_osm_core import tile_box

CACHE = Path(__file__).resolve().parent / "cache"
PBF = CACHE / "florida.osm.pbf"
WAY_KEYS = ("highway", "building", "landuse", "leisure", "natural", "barrier", "aeroway", "water", "railway")
NODE_RE = {"highway": ("crossing", "traffic_signals", "street_lamp", "bus_stop"), "natural": ("tree",)}
NODE_KEYS = ("amenity", "shop", "leisure", "tourism", "aeroway")

boxes = [(tx, tz, tile_box(x0, z0, TILE)) for tx, tz, x0, z0 in tiles()]
S = min(b[2][0] for b in boxes); W_ = min(b[2][1] for b in boxes); N = max(b[2][2] for b in boxes); E = max(b[2][3] for b in boxes)


def tiles_for(lat0, lon0, lat1, lon1):
    return [(tx, tz) for tx, tz, (s, w, n, e) in boxes if lat0 <= n and lat1 >= s and lon0 <= e and lon1 >= w]


def wanted_way(tags):
    return any(k in tags for k in WAY_KEYS) or tags.get("amenity") == "parking"


rel_members = {}       # way id -> needed by a relation
relations = []

# pass 1: multipolygon relations we care about (buildings, land use, water, parks)
for r in osmium.FileProcessor(str(PBF), osmium.osm.RELATION):
    t = dict(r.tags)
    if t.get("type") == "multipolygon" and (wanted_way(t)):
        mem = [(m.ref, m.role) for m in r.members if m.type == "w"]
        relations.append((r.id, t, mem))
        for ref, _ in mem:
            rel_members[ref] = None
print("relations:", len(relations), flush=True)

out = {(tx, tz): [] for tx, tz, _ in boxes}
for obj in osmium.FileProcessor(str(PBF), osmium.osm.NODE | osmium.osm.WAY).with_locations():
    if obj.is_node():
        t = dict(obj.tags)
        if not t or not obj.location.valid():
            continue
        lat, lon = obj.location.lat, obj.location.lon
        if not (S <= lat <= N and W_ <= lon <= E):
            continue
        if any(t.get(k) in v for k, v in NODE_RE.items()) or any(k in t for k in NODE_KEYS):
            el = {"type": "node", "id": obj.id, "lat": lat, "lon": lon, "tags": t}
            for key in tiles_for(lat, lon, lat, lon):
                out[key].append(el)
        continue
    t = dict(obj.tags)
    need_rel = obj.id in rel_members
    if not (wanted_way(t) or need_rel):
        continue
    try:
        geom = [{"lat": n.location.lat, "lon": n.location.lon} for n in obj.nodes]
    except osmium.InvalidLocationError:
        continue
    lats = [g["lat"] for g in geom]; lons = [g["lon"] for g in geom]
    if max(lats) < S or min(lats) > N or max(lons) < W_ or min(lons) > E:
        continue
    if need_rel:
        rel_members[obj.id] = geom
    if wanted_way(t):
        el = {"type": "way", "id": obj.id, "tags": t, "geometry": geom}
        for key in tiles_for(min(lats), min(lons), max(lats), max(lons)):
            out[key].append(el)

for rid, t, mem in relations:
    members = [{"type": "way", "ref": ref, "role": role, "geometry": rel_members[ref]} for ref, role in mem if rel_members.get(ref)]
    if not members:
        continue
    lats = [g["lat"] for m in members for g in m["geometry"]]; lons = [g["lon"] for m in members for g in m["geometry"]]
    el = {"type": "relation", "id": rid, "tags": t, "members": members}
    for key in tiles_for(min(lats), min(lons), max(lats), max(lons)):
        out[key].append(el)

(CACHE / "osm").mkdir(exist_ok=True)
for (tx, tz), els in out.items():
    (CACHE / "osm" / f"t_{tx}_{tz}.json").write_text(json.dumps(els))
print("wrote", len(out), "tiles,", sum(len(v) for v in out.values()), "elements", flush=True)
