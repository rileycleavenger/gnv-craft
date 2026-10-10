#!/usr/bin/env python3
"""Download the USGS 3DEP LiDAR points for a block-coordinate box from the public EPT index on AWS.

  tools/.venv/bin/python tools/lidar/fetch_ept.py X0 Z0 X1 Z1     (block coords, origin 13th St & University Ave)

Writes tools/lidar/cache/points_<X0>_<Z0>_<X1>_<Z1>.npz with x, z (block coords, metres), y (NAVD88 metres) and the
ASPRS class of every point. Source: USGS 3DEP FL_Peninsular_FDEM_Alachua_2018 (public domain).
"""
import io, json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import laspy, numpy as np
from pyproj import Transformer

EPT = "https://s3-us-west-2.amazonaws.com/usgs-lidar-public/FL_Peninsular_FDEM_Alachua_2018/"
ORIGIN = (29.6521, -82.3393)
CACHE = Path(__file__).resolve().parent / "cache"
TO_UTM = Transformer.from_crs("EPSG:4326", "+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", always_xy=True)
MERC_TO_UTM = Transformer.from_crs("EPSG:3857", "+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", always_xy=True)
UTM_TO_MERC = Transformer.from_crs("+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", "EPSG:3857", always_xy=True)
E0, N0 = TO_UTM.transform(ORIGIN[1], ORIGIN[0])


def get(url, tries=6):
    for t in range(tries):
        try:
            return urllib.request.urlopen(url, timeout=60).read()
        except Exception:
            if t == tries - 1:
                raise


def main(x0, z0, x1, z1):
    out = CACHE / f"points_{x0}_{z0}_{x1}_{z1}.npz"
    if out.exists():
        print("cached", out); return
    meta = json.loads(get(EPT + "ept.json"))
    b = meta["bounds"]
    corners = [MERC_TO_UTM and UTM_TO_MERC.transform(E0 + x, N0 - z) for x in (x0, x1) for z in (z0, z1)]
    mx0, mx1 = min(c[0] for c in corners), max(c[0] for c in corners)
    my0, my1 = min(c[1] for c in corners), max(c[1] for c in corners)

    def node_bounds(key):
        d, X, Y, Z = map(int, key.split("-"))
        size = (b[3] - b[0]) / (1 << d)
        return b[0] + X * size, b[1] + Y * size, b[0] + (X + 1) * size, b[1] + (Y + 1) * size

    def hits(key):
        nx0, ny0, nx1, ny1 = node_bounds(key)
        return nx0 <= mx1 and nx1 >= mx0 and ny0 <= my1 and ny1 >= my0

    # walk the hierarchy (sub-hierarchy files are marked with count -1)
    nodes, pending = [], ["0-0-0-0"]
    while pending:
        hk = pending.pop()
        h = json.loads(get(EPT + f"ept-hierarchy/{hk}.json"))
        for key, count in h.items():
            if not hits(key):
                continue
            if count == -1 and key != hk:
                pending.append(key)
            elif count > 0:
                nodes.append(key)
    print(len(nodes), "nodes to fetch")
    parts = []

    def load(key):
        data = get(EPT + f"ept-data/{key}.laz")
        las = laspy.read(io.BytesIO(data))
        mx, my = np.asarray(las.x), np.asarray(las.y)
        keep = (mx >= mx0) & (mx <= mx1) & (my >= my0) & (my <= my1)
        if not keep.any():
            return None
        e, n = MERC_TO_UTM.transform(mx[keep], my[keep])
        x = e - E0
        z = N0 - n
        inb = (x >= x0) & (x < x1) & (z >= z0) & (z < z1)
        return (x[inb].astype(np.float32), z[inb].astype(np.float32), np.asarray(las.z)[keep][inb].astype(np.float32),
                np.asarray(las.classification)[keep][inb].astype(np.uint8))

    done = 0
    with ThreadPoolExecutor(24) as pool:
        for r in pool.map(load, nodes):
            done += 1
            if r is not None:
                parts.append(r)
            if done % 100 == 0:
                print(done, "/", len(nodes), flush=True)
    x = np.concatenate([p[0] for p in parts]); z = np.concatenate([p[1] for p in parts])
    y = np.concatenate([p[2] for p in parts]); c = np.concatenate([p[3] for p in parts])
    CACHE.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(out, x=x, z=z, y=y, c=c)
    print(len(x), "points ->", out, "classes", np.unique(c, return_counts=True))


if __name__ == "__main__":
    main(*map(int, sys.argv[1:5]))
