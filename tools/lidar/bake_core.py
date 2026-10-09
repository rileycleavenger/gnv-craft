#!/usr/bin/env python3
"""Bake the LiDAR core of Gainesville into the region files the mod generates from.

  tools/.venv/bin/python tools/lidar/fetch_ept.py -512 -512 512 512
  tools/.venv/bin/python tools/lidar/fetch_osm_core.py
  tools/.venv/bin/python tools/lidar/bake_core.py

Inputs (tools/lidar/cache): LiDAR points (USGS 3DEP 2018, public domain), full-detail OSM (ODbL) and Overture buildings.
Outputs (src/main/resources/data/gnvcraft/gnv_map/raster):
  r.<rx>.<rz>.bin.gz   256x256 cells (1 cell = 1 block), little endian layers in this order:
      ground  int16  Y of the top ground block
      surface uint8  surface code (see SURFACE below)
      bldg    uint16 building index + 1 (0 = none)
      roof    int16  Y of the top block of the building at this cell
      leafTop int16  Y of the highest leaf block (0 = no canopy)
      leafBot int16  Y of the lowest leaf block
      flags   uint8  bit0 = tree trunk here, bit1 = pool water
      color   uint8  roof palette index + 1 from the NAIP aerial photo (0 = none)
  buildings.json       per building: name, kind, levels, base Y, floor height, bounds, door, stair, elevator
Block coords: UTM 17N metres from 13th St & University Ave, +X east, +Z south (same as tools/osm/fetch_gnv.py).
"""
import gzip, json, math, struct
from pathlib import Path

import numpy as np
from pyproj import Transformer
from scipy import ndimage
from shapely.geometry import shape, Polygon, LineString, Point
from shapely import prepared

ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "src/main/resources/data/gnvcraft/gnv_map/raster"
X0, Z0, SIZE = -512, -512, 1024
REGION = 256
SURFACE_Y = 63          # outside the LiDAR core the world is flat at this Y
BASE_M = 50.0           # NAVD88 metres that map to SURFACE_Y; Gainesville's core sits around 45-55 m
BLEND = 48              # blocks over which the core's terrain eases back to SURFACE_Y at its edge
ORIGIN = (29.6521, -82.3393)
TO_UTM = Transformer.from_crs("EPSG:4326", "EPSG:32617", always_xy=True)
E0, N0 = TO_UTM.transform(ORIGIN[1], ORIGIN[0])

# roof blocks and their average colours (NAIP sees roofs from above; nearest colour wins)
ROOF_PALETTE = [("minecraft:white_concrete", (225, 226, 226)), ("minecraft:smooth_stone", (180, 180, 178)), ("minecraft:light_gray_concrete", (150, 150, 146)),
                ("minecraft:gray_concrete", (100, 102, 104)), ("minecraft:deepslate_tiles", (66, 66, 70)), ("minecraft:black_concrete", (36, 38, 42)),
                ("minecraft:terracotta", (165, 100, 75)), ("minecraft:red_terracotta", (140, 62, 48)), ("minecraft:brown_terracotta", (95, 70, 58)),
                ("minecraft:smooth_sandstone", (205, 195, 160))]

SURFACE = dict(grass=0, asphalt=1, concrete=2, parking=3, water=4, sand=5, lane_white=6, center_yellow=7, crosswalk=8,
               turf=9, brick=10, path_dirt=11, floor=12, edge_white=13, track=14, gravel=15, stop_white=16)


def proj(lon, lat):
    e, n = TO_UTM.transform(lon, lat)
    return e - E0, N0 - n


def proj_xy(lon, lat, z=None):
    """Vectorised lon/lat -> block coords, for shapely.ops.transform."""
    e, n = TO_UTM.transform(np.asarray(lon), np.asarray(lat))
    return e - E0, N0 - n


def grid_index(x, z):
    return (np.floor(x).astype(np.int64) - X0), (np.floor(z).astype(np.int64) - Z0)


# ----------------------------------------------------------------------------------------------- LiDAR rasters
def lidar_rasters():
    p = np.load(CACHE / f"points_{X0}_{Z0}_{X0 + SIZE}_{Z0 + SIZE}.npz")
    x, z, y, c = p["x"], p["z"], p["y"], p["c"]
    i, k = grid_index(x, z)
    ok = (i >= 0) & (i < SIZE) & (k >= 0) & (k < SIZE)
    i, k, y, c = i[ok], k[ok], y[ok], c[ok]
    flat = k * SIZE + i

    def stat(mask, how):
        out = np.full(SIZE * SIZE, np.nan, np.float32)
        if how == "mean":
            s = np.bincount(flat[mask], y[mask], SIZE * SIZE)
            n = np.bincount(flat[mask], None, SIZE * SIZE)
            out[n > 0] = (s[n > 0] / n[n > 0])
        else:
            order = np.argsort(y[mask])
            out[flat[mask][order]] = y[mask][order]          # last write wins -> max
        return out.reshape(SIZE, SIZE)

    ground = stat(c == 2, "mean")
    bld = stat(c == 6, "max")
    # this survey leaves vegetation unclassified (class 1); 3-5 are used if present. Class 7/18 are noise.
    veg = stat((c == 1) | (c == 3) | (c == 4) | (c == 5), "max")
    water = stat(c == 9, "mean")
    bridge = stat(c == 17, "max")
    print("points", len(y), "classes", dict(zip(*np.unique(c, return_counts=True))))
    # fill ground holes (under buildings, water) with the nearest measured ground
    holes = np.isnan(ground)
    _, (ii, kk) = ndimage.distance_transform_edt(holes, return_indices=True)
    ground = ground[ii, kk]
    ground = ndimage.median_filter(ground, size=3)
    return ground, bld, veg, water, bridge


# ----------------------------------------------------------------------------------------------- vector data
def load_osm():
    full = CACHE / "osm_core.json"
    if full.exists():
        return json.loads(full.read_text())["elements"]
    els, seen = [], set()          # partial fetch: merge whatever boxes are cached
    for f in sorted(CACHE.glob("osm_core_*.json")):
        for el in json.loads(f.read_text()):
            if (el["type"], el["id"]) not in seen:
                seen.add((el["type"], el["id"])); els.append(el)
    print("WARNING: using", len(els), "elements from partial OSM boxes")
    return els


def poly_of(el):
    if el["type"] == "way" and el.get("geometry") and len(el["geometry"]) >= 4:
        pts = [proj(g["lon"], g["lat"]) for g in el["geometry"]]
        try:
            p = Polygon(pts)
            return p if p.is_valid else p.buffer(0)
        except Exception:
            return None
    if el["type"] == "relation":
        outers = []
        for m in el.get("members", []):
            if m.get("role") == "outer" and m.get("geometry"):
                pts = [proj(g["lon"], g["lat"]) for g in m["geometry"]]
                if len(pts) >= 4:
                    outers.append(Polygon(pts).buffer(0))
        if outers:
            from shapely.ops import unary_union
            return unary_union(outers)
    return None


def rasterize_polys(items, value_of, out, priority=None):
    """Burn polygons into `out` (cell centres inside polygon). items: list of (polygon, payload)."""
    cx = np.arange(SIZE) + X0 + 0.5
    for poly, payload in items:
        if poly is None or poly.is_empty:
            continue
        minx, minz, maxx, maxz = poly.bounds
        i0, i1 = max(0, int(minx) - X0), min(SIZE, int(math.ceil(maxx)) - X0 + 1)
        k0, k1 = max(0, int(minz) - Z0), min(SIZE, int(math.ceil(maxz)) - Z0 + 1)
        if i0 >= i1 or k0 >= k1:
            continue
        from shapely import contains_xy
        xs, zs = np.meshgrid(np.arange(i0, i1) + X0 + 0.5, np.arange(k0, k1) + Z0 + 0.5)
        inside = contains_xy(poly, xs, zs)
        sub = out[k0:k1, i0:i1]
        sub[inside] = value_of(payload)


ROAD_LANES = dict(motorway=4, trunk=4, primary=4, secondary=2, tertiary=2, residential=2, unclassified=2, living_street=2,
                  service=1, primary_link=1, secondary_link=1, tertiary_link=1, busway=2)
FOOT = {"footway", "path", "pedestrian", "cycleway", "steps", "corridor", "track", "bridleway"}


def road_width(t):
    hw = t.get("highway")
    try:
        lanes = int(float(t.get("lanes", "0").split(";")[0]))
    except ValueError:
        lanes = 0
    if lanes <= 0:
        lanes = ROAD_LANES.get(hw, 2)
    w = lanes * 3.3
    if hw == "service":
        w = 4.5 if t.get("service") in ("parking_aisle", "driveway") else 5.5
    if t.get("turn:lanes") or t.get("lanes:both_ways"):
        w += 3.3
    try:
        if "width" in t:
            w = float(t["width"].split()[0])
    except ValueError:
        pass
    return w, lanes


def burn_roads(osm, surface):
    """Carriageways, lane markings, sidewalks/paths and crosswalks."""
    cxs = np.arange(SIZE) + X0 + 0.5
    roads = []
    for el in osm:
        t = el.get("tags", {})
        if el["type"] != "way" or "highway" not in t or not el.get("geometry"):
            continue
        if t.get("area") == "yes":
            continue
        pts = [proj(g["lon"], g["lat"]) for g in el["geometry"]]
        roads.append((t, pts, el["id"]))
    # paths first (narrow), then carriageways on top
    def segs(pts):
        for a, b in zip(pts, pts[1:]):
            yield np.array(a), np.array(b)

    def burn(pts, half, fn):
        for a, b in segs(pts):
            d = b - a
            L = float(np.hypot(*d))
            if L < 1e-6:
                continue
            u = d / L
            n = np.array([-u[1], u[0]])
            minx, maxx = min(a[0], b[0]) - half - 1, max(a[0], b[0]) + half + 1
            minz, maxz = min(a[1], b[1]) - half - 1, max(a[1], b[1]) + half + 1
            i0, i1 = max(0, int(math.floor(minx)) - X0), min(SIZE, int(math.ceil(maxx)) - X0)
            k0, k1 = max(0, int(math.floor(minz)) - Z0), min(SIZE, int(math.ceil(maxz)) - Z0)
            if i0 >= i1 or k0 >= k1:
                continue
            xs, zs = np.meshgrid(np.arange(i0, i1) + X0 + 0.5, np.arange(k0, k1) + Z0 + 0.5)
            rx, rz = xs - a[0], zs - a[1]
            along = rx * u[0] + rz * u[1]
            off = rx * n[0] + rz * n[1]
            # include round caps so joints have no gaps
            inside = ((along >= 0) & (along <= L) & (np.abs(off) <= half)) | (np.hypot(rx, rz) <= half) | (np.hypot(xs - b[0], zs - b[1]) <= half)
            fn(k0, k1, i0, i1, inside, along, off, L)

    for t, pts, _ in roads:
        hw = t["highway"]
        if hw not in FOOT:
            continue
        mat = SURFACE["concrete"]
        if hw in ("path", "track", "bridleway") and t.get("surface") in ("dirt", "ground", "unpaved", "grass", "sand", "compacted"):
            mat = SURFACE["path_dirt"]
        if t.get("surface") in ("paving_stones", "bricks", "brick", "sett"):
            mat = SURFACE["brick"]
        half = max(1.0, float(t.get("width", "2").split()[0]) / 2) if t.get("width", "").replace(".", "").isdigit() else (1.5 if hw in ("pedestrian",) else 1.0)

        def fn(k0, k1, i0, i1, inside, along, off, L, mat=mat):
            surface[k0:k1, i0:i1][inside] = mat
        burn(pts, half, fn)

    carriage = np.zeros((SIZE, SIZE), bool)
    for t, pts, _ in roads:
        hw = t["highway"]
        if hw in FOOT or hw in ("construction", "proposed", "elevator", "platform"):
            continue
        w, lanes = road_width(t)
        half = w / 2
        oneway = t.get("oneway") in ("yes", "1", "-1") or hw.endswith("_link")
        per_dir = lanes if oneway else max(1, lanes // 2)

        def fn(k0, k1, i0, i1, inside, along, off, L, half=half, oneway=oneway, per_dir=per_dir, lanes=lanes, hw=hw):
            sub = surface[k0:k1, i0:i1]
            sub[inside] = SURFACE["asphalt"]
            carriage[k0:k1, i0:i1] |= inside
            if hw in ("service",) or lanes < 2 or half < 3:
                return
            seg_in = inside & (along >= 0) & (along <= L)
            if not oneway:
                sub[seg_in & (np.abs(off) < 0.55)] = SURFACE["center_yellow"]
                lane_w = (half - 0.4) / per_dir
                for j in range(1, per_dir):
                    dash = (np.mod(along, 12.0) < 3.0)
                    for sgn in (-1, 1):
                        sub[seg_in & dash & (np.abs(off - sgn * j * lane_w) < 0.5)] = SURFACE["lane_white"]
            else:
                lane_w = (2 * half - 0.8) / per_dir
                for j in range(1, per_dir):
                    dash = (np.mod(along, 12.0) < 3.0)
                    sub[seg_in & dash & (np.abs(off - (-half + 0.4 + j * lane_w)) < 0.5)] = SURFACE["lane_white"]
            sub[seg_in & (np.abs(np.abs(off) - (half - 0.6)) < 0.5) & (half >= 5)] = SURFACE["edge_white"]
        burn(pts, half, fn)

    # sidewalk curbs along major carriageways that OSM maps only as a tag (sidewalk=both/left/right)
    for t, pts, _ in roads:
        sw = t.get("sidewalk", t.get("sidewalk:both", ""))
        if t["highway"] in FOOT or sw not in ("both", "left", "right", "yes"):
            continue
        w, _ = road_width(t)

        def fn(k0, k1, i0, i1, inside, along, off, L, w=w):
            band = inside & (np.abs(off) > w / 2) & ~carriage[k0:k1, i0:i1]
            surface[k0:k1, i0:i1][band] = SURFACE["concrete"]
        burn(pts, w / 2 + 2.0, fn)

    # crosswalks: zebra stripes across the carriageway at crossing nodes
    for el in osm:
        t = el.get("tags", {})
        if el["type"] != "node" or t.get("highway") != "crossing":
            continue
        px, pz = proj(el["lon"], el["lat"])
        best = None
        for rt, pts, _ in roads:
            if rt["highway"] in FOOT:
                continue
            for a, b in segs(pts):
                d = b - a
                L = float(np.hypot(*d))
                if L < 1e-6:
                    continue
                u = d / L
                tt = np.clip((px - a[0]) * u[0] + (pz - a[1]) * u[1], 0, L)
                q = a + u * tt
                dist = math.hypot(px - q[0], pz - q[1])
                if best is None or dist < best[0]:
                    best = (dist, u, road_width(rt)[0])
        if best is None or best[0] > 4:
            continue
        _, u, w = best
        n = np.array([-u[1], u[0]])
        i0, i1 = max(0, int(px - w) - X0), min(SIZE, int(px + w) - X0 + 1)
        k0, k1 = max(0, int(pz - w) - Z0), min(SIZE, int(pz + w) - Z0 + 1)
        if i0 >= i1 or k0 >= k1:
            continue
        xs, zs = np.meshgrid(np.arange(i0, i1) + X0 + 0.5, np.arange(k0, k1) + Z0 + 0.5)
        along = (xs - px) * u[0] + (zs - pz) * u[1]
        off = (xs - px) * n[0] + (zs - pz) * n[1]
        stripe = (np.abs(along) <= 1.5) & (np.mod(np.floor(off + 100), 2) == 0) & carriage[k0:k1, i0:i1]
        surface[k0:k1, i0:i1][stripe] = SURFACE["crosswalk"]
    return carriage


# ----------------------------------------------------------------------------------------------- buildings
def kind_of(t):
    for key in ("amenity", "shop", "tourism", "leisure", "office"):
        if t.get(key):
            return t[key]
    b = t.get("building", "yes")
    return b


def levels_of(t):
    for key in ("building:levels", "levels"):
        try:
            return int(float(str(t.get(key, "")).split(";")[0]))
        except ValueError:
            pass
    return 0


def build_buildings(osm, ground, bld, surface):
    # LiDAR building mask
    mask = ~np.isnan(bld)
    mask = ndimage.binary_closing(mask, structure=np.ones((3, 3)), iterations=1)
    mask = ndimage.binary_fill_holes(mask)
    mask = ndimage.binary_opening(mask, structure=np.ones((2, 2)))
    lab, n = ndimage.label(mask)
    sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
    keep_ids = np.where(sizes >= 12)[0] + 1
    keep = np.isin(lab, keep_ids)
    lab, n = ndimage.label(keep)
    # roof elevation: fill gaps inside the mask from the nearest LiDAR roof return, then smooth
    roof = bld.copy()
    holes = np.isnan(roof)
    _, (ii, kk) = ndimage.distance_transform_edt(holes, return_indices=True)
    roof = roof[ii, kk]
    roof = ndimage.median_filter(roof, size=3)

    # vector footprints (OSM incl. multipolygons + Overture) for names, kinds, and post-2019 buildings
    vec = []
    for el in osm:
        t = el.get("tags", {})
        if "building" in t and el["type"] in ("way", "relation"):
            p = poly_of(el)
            if p is not None and not p.is_empty:
                vec.append(dict(poly=p, name=t.get("name"), kind=kind_of(t), levels=levels_of(t), height=None, src="osm", id=el["id"],
                                addr=(t.get("addr:housenumber", "") + " " + t.get("addr:street", "")).strip()))
    ov_path = CACHE / "overture_buildings.geojson"
    if ov_path.exists():
        for f in json.loads(ov_path.read_text())["features"]:
            pr = f["properties"]
            if pr["sources"][0]["dataset"] == "OpenStreetMap":
                continue
            g = shape(f["geometry"])
            if g.geom_type == "MultiPolygon":
                g = max(g.geoms, key=lambda q: q.area)
            from shapely.ops import transform as stx
            p = stx(proj_xy, g)
            vec.append(dict(poly=p, name=(pr.get("names") or {}).get("primary"), kind=pr.get("class") or "yes",
                            levels=pr.get("num_floors") or 0, height=pr.get("height"), src="overture", id=f.get("id") or pr.get("id"), addr=""))
    vid = np.zeros((SIZE, SIZE), np.int32)
    rasterize_polys([(v["poly"], idx + 1) for idx, v in enumerate(vec)], lambda q: q, vid)

    out = []
    # (1) LiDAR buildings, named from the vector footprint they overlap most
    objs = ndimage.find_objects(lab)
    for bi, sl in enumerate(objs, start=1):
        if sl is None:
            continue
        cells = lab[sl] == bi
        ids = vid[sl][cells]
        ids = ids[ids > 0]
        meta = vec[np.bincount(ids).argmax() - 1] if len(ids) else None
        g = ground[sl][cells]
        base = float(np.percentile(g, 10))
        top = float(np.percentile(roof[sl][cells], 95))
        height = max(2.5, top - base)
        levels = meta["levels"] if meta and meta["levels"] else max(1, int(round(height / 3.7)))
        out.append(dict(cells=(sl, cells), base_m=base, height=height, levels=int(levels), name=meta["name"] if meta else None,
                        kind=meta["kind"] if meta else "yes", src="lidar", addr=meta["addr"] if meta else "", osm=meta["id"] if meta else None))
    # (2) footprints with a known height that the 2018 LiDAR doesn't have (built after the flight, e.g. Hub on 3rd Ave)
    for v in vec:
        hgt = v["height"] or (v["levels"] * 3.6 if v["levels"] else None)
        if not hgt or v["poly"].area < 150:
            continue
        tmp = np.zeros((SIZE, SIZE), bool)
        rasterize_polys([(v["poly"], 1)], lambda q: True, tmp)
        if tmp.sum() < 50:
            continue
        cover = (lab[tmp] > 0).mean()
        if cover > 0.3:
            continue
        rows, cols = np.where(tmp)
        sl = (slice(rows.min(), rows.max() + 1), slice(cols.min(), cols.max() + 1))
        cells = tmp[sl]
        base = float(np.percentile(ground[sl][cells], 10))
        lab[sl][cells] = len(out) + 1   # claim the cells
        roof[sl][cells] = base + hgt
        out.append(dict(cells=(sl, cells), base_m=base, height=float(hgt), levels=int(v["levels"] or max(1, round(hgt / 3.6))),
                        name=v["name"], kind=v["kind"], src=v["src"], addr=v["addr"], osm=v["id"]))
    return out, roof


def place_features(b, surface, bidx):
    """Pick a door on an edge next to sidewalk/road, plus stair and elevator cells inside."""
    sl, cells = b["cells"]
    k0, i0 = sl[0].start, sl[1].start
    H, W = cells.shape
    pad = np.pad(cells, 1)
    edge = cells & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    best = None
    cz, cx = np.argwhere(cells).mean(axis=0)
    for (r, c) in np.argwhere(edge):
        for dr, dc, facing in ((-1, 0, "north"), (1, 0, "south"), (0, -1, "west"), (0, 1, "east")):
            rr, cc = r + dr, c + dc
            if 0 <= rr < H and 0 <= cc < W and cells[rr, cc]:
                continue
            gk, gi = k0 + rr, i0 + cc
            if not (0 <= gk < SIZE and 0 <= gi < SIZE):
                continue
            s = surface[gk, gi]
            score = {SURFACE["concrete"]: 0, SURFACE["brick"]: 0, SURFACE["parking"]: 2, SURFACE["asphalt"]: 3}.get(int(s), 5)
            score += 0.01 * math.hypot(r - cz, c - cx)
            if best is None or score < best[0]:
                best = (score, int(i0 + c + X0), int(k0 + r + Z0), facing)
    interior = ndimage.binary_erosion(cells, iterations=2)
    pts = np.argwhere(interior)
    stair = elev = None
    if len(pts):
        # stair at the interior cell nearest the door, elevator 3 cells further in
        dz, dx = (best[2] - Z0 - k0, best[1] - X0 - i0) if best else (0, 0)
        d = np.hypot(pts[:, 0] - dz, pts[:, 1] - dx)
        order = np.argsort(d)
        sr, sc = pts[order[min(len(order) - 1, 2)]]
        stair = (int(i0 + sc + X0), int(k0 + sr + Z0))
        far = pts[np.hypot(pts[:, 0] - sr, pts[:, 1] - sc) >= 3]
        if len(far):
            er, ec = far[np.argmin(np.hypot(far[:, 0] - sr, far[:, 1] - sc))]
            elev = (int(i0 + ec + X0), int(k0 + er + Z0))
    return best, stair, elev


# ----------------------------------------------------------------------------------------------- trees
def trees(ground, veg, building_mask, surface):
    chm = veg - ground
    chm[np.isnan(chm)] = 0
    chm[building_mask] = 0
    chm = ndimage.median_filter(chm, size=3)
    canopy = chm >= 3.0
    canopy = ndimage.binary_opening(canopy, structure=np.ones((2, 2)))
    leaf_top = np.where(canopy, chm, 0)
    # rounded crowns: thin at the canopy's edge, thick inside; live oaks spread low (crown base ~45% of height)
    edge = ndimage.distance_transform_edt(canopy)
    thick = np.minimum(chm * 0.55, 1.0 + edge * 1.1)
    leaf_top = np.where(canopy, chm - np.clip(2.2 - edge, 0, 2), 0)
    leaf_bot = np.where(canopy, np.maximum(2.0, leaf_top - thick), 0)
    maxf = ndimage.maximum_filter(chm, size=9)
    tops = (chm == maxf) & (chm >= 5.0) & canopy
    trunk = tops & ~building_mask & ~np.isin(surface, [SURFACE["asphalt"], SURFACE["center_yellow"], SURFACE["lane_white"], SURFACE["edge_white"], SURFACE["crosswalk"]])
    print("trees:", int(trunk.sum()), "canopy cells:", int(canopy.sum()))
    return leaf_top, leaf_bot, trunk


# ----------------------------------------------------------------------------------------------- main
def main():
    ground_m, bld, veg, water, bridge = lidar_rasters()
    print("ground NAVD88 m: min %.1f median %.1f max %.1f" % (np.nanmin(ground_m), np.nanmedian(ground_m), np.nanmax(ground_m)))
    osm = load_osm()

    surface = np.zeros((SIZE, SIZE), np.uint8)
    land = []
    for el in osm:
        t = el.get("tags", {})
        if el["type"] not in ("way", "relation") or "building" in t:
            continue
        code = None
        if t.get("amenity") == "parking" or t.get("highway") in ("pedestrian",) and t.get("area") == "yes":
            code = SURFACE["parking"] if t.get("amenity") == "parking" else SURFACE["concrete"]
        elif t.get("leisure") in ("pitch",) or t.get("landuse") == "recreation_ground":
            code = SURFACE["turf"] if t.get("surface") not in ("sand", "clay") else SURFACE["sand"]
        elif t.get("leisure") == "track":
            code = SURFACE["track"]
        elif t.get("natural") == "water" or t.get("landuse") in ("reservoir", "basin"):
            code = SURFACE["water"]
        elif t.get("natural") in ("sand", "beach"):
            code = SURFACE["sand"]
        elif t.get("landuse") in ("construction", "brownfield"):
            code = SURFACE["gravel"]
        if code is not None:
            p = poly_of(el)
            if p is not None:
                land.append((p.area, p, code))
    land.sort(key=lambda q: -q[0])  # big areas first, small ones on top
    rasterize_polys([(p, code) for _, p, code in land], lambda q: q, surface)
    surface[(~np.isnan(water)) & (surface == SURFACE["grass"])] = SURFACE["water"]
    burn_roads(osm, surface)

    buildings, roof_m = build_buildings(osm, ground_m, bld, surface)
    bidx = np.zeros((SIZE, SIZE), np.uint16)
    for idx, b in enumerate(buildings, start=1):
        sl, cells = b["cells"]
        bidx[sl][cells] = idx

    # metres -> block Y, easing back to the flat world at the core's edge
    ii, kk = np.meshgrid(np.arange(SIZE), np.arange(SIZE))
    edge_dist = np.minimum.reduce([ii, kk, SIZE - 1 - ii, SIZE - 1 - kk]).astype(np.float32)
    blend = np.clip(edge_dist / BLEND, 0, 1)
    gy = np.round(SURFACE_Y + (ground_m - BASE_M) * blend).astype(np.int16)
    # flatten the ground under each building to its base, so floors are level
    table = []
    roof_y = np.zeros((SIZE, SIZE), np.int16)
    for idx, b in enumerate(buildings, start=1):
        sl, cells = b["cells"]
        k0, i0 = sl[0].start, sl[1].start
        bb = float(np.mean(blend[sl][cells]))
        base_y = int(round(SURFACE_Y + (b["base_m"] - BASE_M) * bb))
        gy[sl][cells] = base_y
        ry = np.round(base_y + (roof_m[sl][cells] - b["base_m"])).astype(np.int16)
        ry = np.clip(ry, base_y + 3, base_y + 200)
        roof_y[sl][cells] = ry
        floor_h = max(3, min(6, int(round(b["height"] / max(1, b["levels"])))))
        door, stair, elev = place_features(b, surface, bidx)
        rows, cols = np.where(cells)
        table.append(dict(i=idx, name=b["name"], kind=b["kind"], levels=b["levels"], baseY=base_y, floorH=floor_h,
                          topY=int(ry.max()), minX=int(i0 + cols.min() + X0), minZ=int(k0 + rows.min() + Z0),
                          maxX=int(i0 + cols.max() + X0), maxZ=int(k0 + rows.max() + Z0), src=b["src"], osm=b["osm"], addr=b["addr"],
                          door=dict(x=door[1], z=door[2], facing=door[3]) if door else None, stair=list(stair) if stair else None, elevator=list(elev) if elev else None))
    surface[bidx > 0] = SURFACE["floor"]

    # name/type corrections
    ov = json.loads((Path(__file__).resolve().parent / "overrides.json").read_text())["buildings"]
    for o in ov:
        i, k = o["point"][0] - X0, o["point"][1] - Z0
        bi = int(bidx[k, i])
        if bi:
            t = table[bi - 1]
            t.update(name=o["name"], kind=o["kind"], levels=o["levels"], addr=o["addr"])
            if "facade" in o:
                t["facade"] = o["facade"]
            t["floorH"] = max(3, min(6, round((t["topY"] - t["baseY"]) / max(1, o["levels"]))))
        else:
            print("override point not in a building:", o)

    # NAIP aerial photo (public domain) -> roof colour per cell, and pools
    color = np.zeros((SIZE, SIZE), np.uint8)
    pool = np.zeros((SIZE, SIZE), bool)
    naip_path = CACHE / f"naip_{X0}_{Z0}_{SIZE}.npy"
    if naip_path.exists():
        naip = np.load(naip_path).astype(np.int32)
        r, g, bl = naip[..., 0], naip[..., 1], naip[..., 2]
        pool = (bl > 150) & (bl > r + 45) & (g > r + 25) & (bl >= g)
        pool = ndimage.binary_opening(pool, structure=np.ones((2, 2)))
        pal = np.array([c for _, c in ROOF_PALETTE], np.int32)
        sm = np.stack([ndimage.uniform_filter(naip[..., ch].astype(np.float32), 3) for ch in range(3)], -1)
        d = ((sm[:, :, None, :] - pal[None, None, :, :]) ** 2).sum(-1)
        color = (np.argmin(d, axis=-1) + 1).astype(np.uint8)
        color[bidx == 0] = 0
        # one roof material per roof plane: majority colour over each building's cells within 5x5 windows
        counts = np.stack([ndimage.uniform_filter((color == c + 1).astype(np.float32), 5) for c in range(len(ROOF_PALETTE))], 0)
        color = np.where(bidx > 0, np.argmax(counts, 0) + 1, 0).astype(np.uint8)
        print("pool cells:", int(pool.sum()))

    leaf_top_m, leaf_bot_m, trunk = trees(ground_m, veg, bidx > 0, surface)
    lt = np.where(leaf_top_m > 0, gy + np.round(leaf_top_m), 0).astype(np.int16)
    lb = np.where(leaf_top_m > 0, gy + np.round(leaf_bot_m), 0).astype(np.int16)
    flags = trunk.astype(np.uint8) | (pool.astype(np.uint8) << 1)

    OUT.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("r.*.bin.gz"):
        f.unlink()
    for rk in range(SIZE // REGION):
        for ri in range(SIZE // REGION):
            sl = (slice(rk * REGION, (rk + 1) * REGION), slice(ri * REGION, (ri + 1) * REGION))
            rx, rz = (X0 + ri * REGION) // REGION, (Z0 + rk * REGION) // REGION
            blob = b"".join([gy[sl].astype("<i2").tobytes(), surface[sl].tobytes(), bidx[sl].astype("<u2").tobytes(),
                             roof_y[sl].astype("<i2").tobytes(), lt[sl].astype("<i2").tobytes(), lb[sl].astype("<i2").tobytes(),
                             flags[sl].tobytes(), color[sl].tobytes()])
            with gzip.open(OUT / f"r.{rx}.{rz}.bin.gz", "wb", compresslevel=9) as fh:
                fh.write(blob)
    (OUT / "buildings.json").write_text(json.dumps({"x0": X0, "z0": Z0, "size": SIZE, "region": REGION, "buildings": table,
                                                    "roofPalette": [b for b, _ in ROOF_PALETTE]}, separators=(",", ":")))
    total = sum(f.stat().st_size for f in OUT.iterdir())
    print(len(table), "buildings,", "%.1f MB" % (total / 1e6))
    named = [t for t in table if t["name"]]
    print("named:", len(named), [t["name"] for t in named[:25]])


if __name__ == "__main__":
    main()
