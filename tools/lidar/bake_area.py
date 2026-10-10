#!/usr/bin/env python3
"""Bake the whole V1 area (tools/lidar/v1_area.json) tile by tile into the mod's raster regions.

  tools/.venv/bin/python tools/lidar/fetch_tiles.py 3          # LiDAR rasters per tile
  tools/.venv/bin/python tools/lidar/fetch_osm_core.py area    # full-detail OSM per tile
  tools/.venv/bin/python tools/lidar/fetch_naip_area.py        # aerial photo per tile
  overturemaps download ... -o tools/lidar/cache/overture_area.geojson
  tools/.venv/bin/python tools/lidar/bake_area.py

Each 1024-block tile is processed with a MARGIN of its neighbours so buildings, trees and roads that cross tile edges come out
whole. Buildings are identified across tiles by their top-left cell, so a building on a tile seam gets one id. Terrain eases
back to the flat world only where there is no neighbouring tile. Region files are only written where they touch the V1 polygon.
Output format: see bake_core.py.
"""
import gzip, json, math, sys
from pathlib import Path

import numpy as np
from scipy import ndimage
from shapely.geometry import box

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bake_core as bc
from area import tiles, area_polygon, TILE, GRID0

CACHE = Path(__file__).resolve().parent / "cache"
TMP = CACHE / "bake_tmp"
MARGIN = 256
W = TILE + 2 * MARGIN


def window_arrays(tx, tz, have):
    """LiDAR ground/bld/veg (and NAIP) for the tile plus MARGIN from its neighbours; NaN where no data."""
    ground = np.full((W, W), np.nan, np.float32); bld = ground.copy(); veg = ground.copy()
    naip = np.zeros((W, W, 3), np.uint8)
    covered = np.zeros((W, W), bool)
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            nt = (tx + dx, tz + dz)
            if nt not in have:
                continue
            d = np.load(CACHE / "lidar" / f"t_{nt[0]}_{nt[1]}.npz")
            # window coords of this neighbour tile
            wx0 = MARGIN + dx * TILE
            wz0 = MARGIN + dz * TILE
            sx0, sz0 = max(0, -wx0), max(0, -wz0)
            sx1, sz1 = min(TILE, W - wx0), min(TILE, W - wz0)
            if sx0 >= sx1 or sz0 >= sz1:
                continue
            dst = (slice(wz0 + sz0, wz0 + sz1), slice(wx0 + sx0, wx0 + sx1))
            src = (slice(sz0, sz1), slice(sx0, sx1))
            ground[dst] = d["ground"][src]; bld[dst] = d["bld"][src]; veg[dst] = d["veg"][src]
            covered[dst] = True
            x0, z0 = GRID0 + nt[0] * TILE, GRID0 + nt[1] * TILE
            npath = CACHE / f"naip_{x0}_{z0}_{TILE}.npy"
            if npath.exists():
                naip[dst] = np.load(npath)[src]
    return ground, bld, veg, naip, covered


def osm_for(tx, tz, have):
    els, seen = [], set()
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            p = CACHE / "osm" / f"t_{tx + dx}_{tz + dz}.json"
            if not p.exists():
                continue
            for el in json.loads(p.read_text()):
                k = (el["type"], el["id"])
                if k not in seen:
                    seen.add(k); els.append(el)
    return els


def surfaces(osm):
    surface = np.zeros((W, W), np.uint8)
    land = []
    for el in osm:
        t = el.get("tags", {})
        if el["type"] not in ("way", "relation") or "building" in t:
            continue
        code = None
        if t.get("amenity") == "parking":
            code = bc.SURFACE["parking"]
        elif t.get("highway") == "pedestrian" and t.get("area") == "yes":
            code = bc.SURFACE["concrete"]
        elif t.get("leisure") in ("pitch",) or t.get("landuse") == "recreation_ground":
            code = bc.SURFACE["turf"] if t.get("surface") not in ("sand", "clay") else bc.SURFACE["sand"]
        elif t.get("leisure") == "track":
            code = bc.SURFACE["track"]
        elif t.get("natural") == "water" or t.get("landuse") in ("reservoir", "basin") or t.get("water"):
            code = bc.SURFACE["water"]
        elif t.get("natural") in ("sand", "beach"):
            code = bc.SURFACE["sand"]
        elif t.get("landuse") in ("construction", "brownfield"):
            code = bc.SURFACE["gravel"]
        elif t.get("aeroway") in ("runway", "taxiway", "apron"):
            code = bc.SURFACE["asphalt"]
        if code is not None:
            p = bc.poly_of(el)
            if p is not None:
                land.append((p.area, p, code))
    land.sort(key=lambda q: -q[0])
    bc.rasterize_polys([(p, code) for _, p, code in land], lambda q: q, surface)
    bc.burn_roads(osm, surface)
    return surface


def bake_tile(tx, tz, have, poly):
    x0, z0 = GRID0 + tx * TILE, GRID0 + tz * TILE
    bc.X0, bc.Z0, bc.SIZE = x0 - MARGIN, z0 - MARGIN, W
    ground_m, bld, veg, naip, covered = window_arrays(tx, tz, have)
    holes = np.isnan(ground_m)
    if holes.all():
        return None
    _, (ii, kk) = ndimage.distance_transform_edt(holes, return_indices=True)
    ground_m = ndimage.median_filter(ground_m[ii, kk], size=3)
    osm = osm_for(tx, tz, have)
    surface = surfaces(osm)
    buildings, roof_m = bc.build_buildings(osm, ground_m, bld, surface)

    # ease back to the flat world only where the data stops
    blend = np.clip(ndimage.distance_transform_edt(covered) / bc.BLEND, 0, 1).astype(np.float32)
    gy = np.round(bc.SURFACE_Y + (ground_m - bc.BASE_M) * blend).astype(np.int16)

    # flat city: every road, sidewalk and lot sits on one level; buildings, trees and stands keep their measured heights above it
    gy = np.full((W, W), bc.SURFACE_Y, np.int16)

    core = (slice(MARGIN, MARGIN + TILE), slice(MARGIN, MARGIN + TILE))
    key = np.zeros((W, W), np.int64)            # building identity across tiles: global (z, x) of its first cell
    roof_y = np.zeros((W, W), np.int16)
    meta = {}
    bidx = np.zeros((W, W), np.int32)
    for idx, b in enumerate(buildings, start=1):
        sl, cells = b["cells"]
        bidx[sl][cells] = idx
    for idx, b in enumerate(buildings, start=1):
        sl, cells = b["cells"]
        if not (bidx[core] == idx).any():
            continue                             # doesn't reach this tile's core
        rows, cols = np.where(cells)
        k0, i0 = sl[0].start, sl[1].start
        r0 = rows.min()
        c0 = cols[rows == r0].min()
        gkey = int((z0 - MARGIN + k0 + r0 + 100000) * 1000000 + (x0 - MARGIN + i0 + c0 + 100000))
        base_y = bc.SURFACE_Y
        gy[sl][cells] = base_y
        ry = np.clip(np.round(base_y + (roof_m[sl][cells] - b["base_m"])), base_y + 3, base_y + 200).astype(np.int16)
        roof_y[sl][cells] = ry
        key[sl][cells] = gkey
        door, stair, elev = bc.place_features(b, surface, bidx)
        cz, cx = rows.mean() + k0, cols.mean() + i0
        owner = bool(MARGIN <= cz < MARGIN + TILE and MARGIN <= cx < MARGIN + TILE)
        meta[gkey] = dict(owner=owner, name=b["name"], kind=b["kind"], levels=b["levels"], baseY=base_y,
                          floorH=max(3, min(6, int(round(b["height"] / max(1, b["levels"]))))), topY=int(ry.max()),
                          minX=int(bc.X0 + i0 + cols.min()), minZ=int(bc.Z0 + k0 + rows.min()),
                          maxX=int(bc.X0 + i0 + cols.max()), maxZ=int(bc.Z0 + k0 + rows.max()), src=b["src"], osm=b["osm"], addr=b["addr"],
                          door=dict(x=door[1], z=door[2], facing=door[3]) if door else None,
                          stair=list(stair) if stair else None, elevator=list(elev) if elev else None)
    surface[key != 0] = bc.SURFACE["floor"]

    # aerial photo: roof colours and pools
    color = np.zeros((W, W), np.uint8)
    nai = naip.astype(np.int32)
    r, g, bl = nai[..., 0], nai[..., 1], nai[..., 2]
    pool = (bl > 150) & (bl > r + 45) & (g > r + 25) & (bl >= g)
    pool = ndimage.binary_opening(pool, structure=np.ones((2, 2)))
    pal = np.array([c for _, c in bc.ROOF_PALETTE], np.int32)
    sm = np.stack([ndimage.uniform_filter(nai[..., ch].astype(np.float32), 3) for ch in range(3)], -1)
    d = ((sm[:, :, None, :] - pal[None, None, :, :]) ** 2).sum(-1)
    color = (np.argmin(d, axis=-1) + 1).astype(np.uint8)
    counts = np.stack([ndimage.uniform_filter((color == c + 1).astype(np.float32), 5) for c in range(len(bc.ROOF_PALETTE))], 0)
    color = np.where(key != 0, np.argmax(counts, 0) + 1, 0).astype(np.uint8)
    color[naip.sum(-1) == 0] = 0

    leaf_top_m, leaf_bot_m, trunk = bc.trees(ground_m, veg, key != 0, surface)
    lt = np.where(leaf_top_m > 0, gy + np.round(leaf_top_m), 0).astype(np.int16)
    lb = np.where(leaf_top_m > 0, gy + np.round(leaf_bot_m), 0).astype(np.int16)
    flags = trunk.astype(np.uint8) | (pool.astype(np.uint8) << 1)
    TMP.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(TMP / f"t_{tx}_{tz}.npz", gy=gy[core], s=surface[core], key=key[core], roof=roof_y[core], lt=lt[core],
                        lb=lb[core], f=flags[core], c=color[core])
    return meta


def main():
    poly = area_polygon()
    todo = [t for t in tiles() if (CACHE / "lidar" / f"t_{t[0]}_{t[1]}.npz").exists()]
    have = {(t[0], t[1]) for t in todo}
    only = set(tuple(map(int, a.split(","))) for a in sys.argv[1:] if not a.startswith("--"))
    print(len(todo), "tiles with LiDAR", flush=True)
    allmeta = {}
    for tx, tz, x0, z0 in todo:
        reuse = "--reuse" in sys.argv or (only and (tx, tz) not in only)
        if reuse and (TMP / f"t_{tx}_{tz}.npz").exists() and (TMP / f"m_{tx}_{tz}.json").exists():
            m = {int(k): v for k, v in json.loads((TMP / f"m_{tx}_{tz}.json").read_text()).items()}
        else:
            m = bake_tile(tx, tz, have, poly) or {}
            (TMP / f"m_{tx}_{tz}.json").write_text(json.dumps(m, default=lambda o: o.item() if hasattr(o, "item") else str(o)))
            print("baked", tx, tz, len(m), "buildings", flush=True)
        for k, v in m.items():
            if k not in allmeta or (v["owner"] and not allmeta[k]["owner"]):
                allmeta[k] = v

    # global building table
    keys = sorted(allmeta)
    index = {k: i + 1 for i, k in enumerate(keys)}
    if len(keys) >= 65535:
        sys.exit("too many buildings for uint16 ids")
    table = []
    for k in keys:
        v = dict(allmeta[k]); v.pop("owner")
        v["i"] = index[k]
        table.append(v)

    # write regions (only those touching the V1 polygon)
    for f in bc.OUT.glob("r.*.bin.gz"):
        f.unlink()
    R = bc.REGION
    written = 0
    full = {}
    for tx, tz, x0, z0 in todo:
        d = np.load(TMP / f"t_{tx}_{tz}.npz")
        bidx = np.zeros(d["key"].shape, np.uint16)
        nz = d["key"] != 0
        if nz.any():
            uk, inv = np.unique(d["key"][nz], return_inverse=True)
            bidx[nz] = np.array([index.get(int(k), 0) for k in uk], np.uint16)[inv]
        full[(tx, tz)] = bidx
        for rk in range(TILE // R):
            for ri in range(TILE // R):
                rx0, rz0 = x0 + ri * R, z0 + rk * R
                if not poly.buffer(64).intersects(box(rx0, rz0, rx0 + R, rz0 + R)):
                    continue
                sl = (slice(rk * R, (rk + 1) * R), slice(ri * R, (ri + 1) * R))
                blob = b"".join([d["gy"][sl].astype("<i2").tobytes(), d["s"][sl].tobytes(), bidx[sl].astype("<u2").tobytes(),
                                 d["roof"][sl].astype("<i2").tobytes(), d["lt"][sl].astype("<i2").tobytes(), d["lb"][sl].astype("<i2").tobytes(),
                                 d["f"][sl].tobytes(), d["c"][sl].tobytes()])
                with gzip.open(bc.OUT / f"r.{rx0 // R}.{rz0 // R}.bin.gz", "wb", compresslevel=9) as fh:
                    fh.write(blob)
                written += 1

    # name / facade corrections
    ov = json.loads((Path(__file__).resolve().parent / "overrides.json").read_text())["buildings"]
    for o in ov:
        px, pz = o["point"]
        t = ((px - GRID0) // TILE, (pz - GRID0) // TILE)
        if t not in full:
            print("override outside baked tiles:", o["name"]); continue
        x0, z0 = GRID0 + t[0] * TILE, GRID0 + t[1] * TILE
        bi = int(full[t][pz - z0, px - x0])
        if not bi:
            print("override point not in a building:", o["name"]); continue
        e = table[bi - 1]
        e.update(name=o["name"], kind=o["kind"], levels=o["levels"], addr=o["addr"])
        if "facade" in o:
            e["facade"] = o["facade"]
        e["floorH"] = max(3, min(6, round((e["topY"] - e["baseY"]) / max(1, o["levels"]))))

    regions = sorted(f.name for f in bc.OUT.glob("r.*.bin.gz"))
    (bc.OUT / "buildings.json").write_text(json.dumps({"region": R, "regions": regions, "buildings": table,
                                                        "roofPalette": [b for b, _ in bc.ROOF_PALETTE]}, separators=(",", ":")))
    import post_fixes, bake_stadium
    post_fixes.main()
    bake_stadium.main()
    total = sum(f.stat().st_size for f in bc.OUT.iterdir())
    print(f"{len(table)} buildings, {written} regions, {total / 1e6:.1f} MB", flush=True)


if __name__ == "__main__":
    main()
