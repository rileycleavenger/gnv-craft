#!/usr/bin/env python3
"""Fetch USDA NAIP aerial imagery (public domain, 0.6 m) for the LiDAR core, resampled to the 1 m block grid.

Writes tools/lidar/cache/naip_<X0>_<Z0>_<SIZE>.npy (SIZE x SIZE x 3 uint8, row = z, col = x). Uses the free Microsoft
Planetary Computer STAC catalog.
"""
import sys
from pathlib import Path
import numpy as np
import planetary_computer, pystac_client, rasterio
from rasterio.warp import reproject, Resampling
from rasterio.transform import from_origin
from pyproj import Transformer

CACHE = Path(__file__).resolve().parent / "cache"
ORIGIN = (29.6521, -82.3393)
TO_UTM = Transformer.from_crs("EPSG:4326", "+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", always_xy=True)
TO_LL = Transformer.from_crs("+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", "EPSG:4326", always_xy=True)
E0, N0 = TO_UTM.transform(ORIGIN[1], ORIGIN[0])


def main(x0, z0, size):
    out = CACHE / f"naip_{x0}_{z0}_{size}.npy"
    lon0, lat0 = TO_LL.transform(E0 + x0, N0 - z0 - size)
    lon1, lat1 = TO_LL.transform(E0 + x0 + size, N0 - z0)
    cat = pystac_client.Client.open("https://planetarycomputer.microsoft.com/api/stac/v1", modifier=planetary_computer.sign_inplace)
    items = list(cat.search(collections=["naip"], bbox=[lon0, lat0, lon1, lat1]).items())
    items.sort(key=lambda it: it.datetime, reverse=True)
    print([(it.id, str(it.datetime)[:10]) for it in items[:6]])
    newest = items[0].datetime.year
    dst = np.zeros((3, size, size), np.uint8)
    transform = from_origin(E0 + x0, N0 - z0, 1.0, 1.0)
    for it in [i for i in items if i.datetime.year == newest]:
        with rasterio.open(it.assets["image"].href) as src:
            for band in range(3):
                tmp = np.zeros((size, size), np.uint8)
                reproject(rasterio.band(src, band + 1), tmp, dst_transform=transform, dst_crs="+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", resampling=Resampling.average)
                dst[band] = np.where(tmp > 0, tmp, dst[band])
    np.save(out, np.moveaxis(dst, 0, -1))
    print("wrote", out, "year", newest)


if __name__ == "__main__":
    main(*map(int, sys.argv[1:4]))
