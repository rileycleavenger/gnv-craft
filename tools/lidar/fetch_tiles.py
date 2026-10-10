#!/usr/bin/env python3
"""Fetch USGS 3DEP LiDAR for every V1 tile and reduce each to 1 m rasters (points are not kept).

  tools/.venv/bin/python tools/lidar/fetch_tiles.py [max_parallel_tiles]

Writes tools/lidar/cache/lidar/t_<tx>_<tz>.npz with float32 1024x1024 arrays:
  ground (mean of class 2), bld (max of class 6), veg (max of class 1/3/4/5), plus point counts gn/bn.
"""
import io, json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import laspy, numpy as np
from pyproj import Transformer
sys.path.insert(0, str(Path(__file__).resolve().parent))
from area import tiles, TILE, E0, N0

EPT = "https://s3-us-west-2.amazonaws.com/usgs-lidar-public/FL_Peninsular_FDEM_Alachua_2018/"
OUT = Path(__file__).resolve().parent / "cache" / "lidar"
MERC_TO_UTM = Transformer.from_crs("EPSG:3857", "+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", always_xy=True)
UTM_TO_MERC = Transformer.from_crs("+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", "EPSG:3857", always_xy=True)


def get(url, tries=6):
    for t in range(tries):
        try:
            return urllib.request.urlopen(url, timeout=90).read()
        except Exception:
            if t == tries - 1:
                raise


META = json.loads(get(EPT + "ept.json"))
B = META["bounds"]
HIER = {}


def area_merc_bounds():
    from area import area_polygon
    x0, z0, x1, z1 = area_polygon().buffer(1100).bounds
    cs = [UTM_TO_MERC.transform(E0 + x, N0 - z) for x in (x0, x1) for z in (z0, z1)]
    return min(c[0] for c in cs), min(c[1] for c in cs), max(c[0] for c in cs), max(c[1] for c in cs)


def hierarchy():
    """EPT hierarchy pruned to the V1 area, walked once (sub-hierarchy files fetched in parallel) and shared by all tiles."""
    if HIER:
        return HIER
    ax0, ay0, ax1, ay1 = area_merc_bounds()

    def inside(key):
        b = node_bounds(key)
        return b[0] <= ax1 and b[2] >= ax0 and b[1] <= ay1 and b[3] >= ay0

    pending = ["0-0-0-0"]
    with ThreadPoolExecutor(16) as pool:
        while pending:
            batch, pending = pending, []
            for hk, h in zip(batch, pool.map(lambda k: json.loads(get(EPT + f"ept-hierarchy/{k}.json")), batch)):
                for key, count in h.items():
                    if not inside(key):
                        continue
                    if count == -1 and key != hk:
                        pending.append(key)
                    elif count > 0:
                        HIER[key] = count
    return HIER


def node_bounds(key):
    d, X, Y, Z = map(int, key.split("-"))
    size = (B[3] - B[0]) / (1 << d)
    return B[0] + X * size, B[1] + Y * size, B[0] + (X + 1) * size, B[1] + (Y + 1) * size


def fetch_tile(tx, tz, x0, z0):
    out = OUT / f"t_{tx}_{tz}.npz"
    if out.exists():
        return f"cached {tx},{tz}"
    corners = [UTM_TO_MERC.transform(E0 + x, N0 - z) for x in (x0, x0 + TILE) for z in (z0, z0 + TILE)]
    mx0, mx1 = min(c[0] for c in corners), max(c[0] for c in corners)
    my0, my1 = min(c[1] for c in corners), max(c[1] for c in corners)
    nodes = [k for k in hierarchy() if (lambda b: b[0] <= mx1 and b[2] >= mx0 and b[1] <= my1 and b[3] >= my0)(node_bounds(k))]
    n = TILE * TILE
    gs, gn = np.zeros(n), np.zeros(n)
    bld = np.full(n, -1e9); veg = np.full(n, -1e9); bn = np.zeros(n)

    def load(key):
        las = laspy.read(io.BytesIO(get(EPT + f"ept-data/{key}.laz")))
        mx, my = np.asarray(las.x), np.asarray(las.y)
        keep = (mx >= mx0) & (mx <= mx1) & (my >= my0) & (my <= my1)
        if not keep.any():
            return None
        e, nn = MERC_TO_UTM.transform(mx[keep], my[keep])
        i = np.floor(e - E0 - x0).astype(np.int64)
        k = np.floor(N0 - nn - z0).astype(np.int64)
        ok = (i >= 0) & (i < TILE) & (k >= 0) & (k < TILE)
        return (k[ok] * TILE + i[ok], np.asarray(las.z)[keep][ok], np.asarray(las.classification)[keep][ok])

    with ThreadPoolExecutor(16) as pool:
        for r in pool.map(load, nodes):
            if r is None:
                continue
            f, y, c = r
            g = c == 2
            gs += np.bincount(f[g], y[g], n); gn += np.bincount(f[g], None, n)
            b = c == 6
            np.maximum.at(bld, f[b], y[b]); bn += np.bincount(f[b], None, n)
            v = (c == 1) | (c == 3) | (c == 4) | (c == 5)
            np.maximum.at(veg, f[v], y[v])
    ground = np.where(gn > 0, gs / np.maximum(gn, 1), np.nan).astype(np.float32).reshape(TILE, TILE)
    bld = np.where(bld > -1e8, bld, np.nan).astype(np.float32).reshape(TILE, TILE)
    veg = np.where(veg > -1e8, veg, np.nan).astype(np.float32).reshape(TILE, TILE)
    OUT.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(out, ground=ground, bld=bld, veg=veg, gn=gn.reshape(TILE, TILE).astype(np.uint16), bn=bn.reshape(TILE, TILE).astype(np.uint16))
    return f"tile {tx},{tz}: {len(nodes)} nodes, ground cover {np.mean(gn > 0):.2f}, building cells {int((bn > 0).sum())}"


if __name__ == "__main__":
    par = int(sys.argv[1]) if len(sys.argv) > 1 else 2
    hierarchy()
    print("hierarchy nodes:", len(HIER), flush=True)
    todo = tiles()
    with ThreadPoolExecutor(par) as pool:
        for msg in pool.map(lambda t: fetch_tile(*t), todo):
            print(msg, flush=True)
    print("ALL_TILES_DONE", flush=True)
