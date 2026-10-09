"""The V1 area polygon in block coordinates and the 1024-block tiles that cover it (tile grid aligned with the original core)."""
import json
from pathlib import Path
from pyproj import Transformer
from shapely.geometry import Polygon, box

HERE = Path(__file__).resolve().parent
TILE = 1024
GRID0 = -512                       # tile edges at -512 + 1024 * k, so the first core is tile (0, 0)
ORIGIN = (29.6521, -82.3393)
TO_UTM = Transformer.from_crs("EPSG:4326", "EPSG:32617", always_xy=True)
E0, N0 = TO_UTM.transform(ORIGIN[1], ORIGIN[0])


def area_polygon():
    pts = json.loads((HERE / "v1_area.json").read_text())["polygon"]
    xy = []
    for lon, lat in pts:
        e, n = TO_UTM.transform(lon, lat)
        xy.append((e - E0, N0 - n))
    return Polygon(xy)


def tiles():
    """[(tx, tz, x0, z0)] for every tile touching the V1 polygon."""
    poly = area_polygon()
    minx, minz, maxx, maxz = poly.bounds
    out = []
    for tx in range(int((minx - GRID0) // TILE), int((maxx - GRID0) // TILE) + 1):
        for tz in range(int((minz - GRID0) // TILE), int((maxz - GRID0) // TILE) + 1):
            x0, z0 = GRID0 + tx * TILE, GRID0 + tz * TILE
            if poly.intersects(box(x0, z0, x0 + TILE, z0 + TILE)):
                out.append((tx, tz, x0, z0))
    return out


if __name__ == "__main__":
    p = area_polygon()
    print("area %.1f km2, bounds %s" % (p.area / 1e6, [round(v) for v in p.bounds]))
    t = tiles()
    print(len(t), "tiles:", t)
