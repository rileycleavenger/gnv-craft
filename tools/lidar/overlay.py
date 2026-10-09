#!/usr/bin/env python3
"""NAIP photo with baked building outlines (red) and roads (yellow) drawn over it, to check alignment. Crop in block coords."""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage
sys.path.insert(0, __import__("os").path.dirname(__file__))
from preview import load, CACHE


def main(cx0, cz0, cx1, cz1, out):
    meta, L = load()
    x0, z0 = meta["x0"], meta["z0"]
    sl = (slice(cz0 - z0, cz1 - z0), slice(cx0 - x0, cx1 - x0))
    naip = np.load(CACHE / f"naip_{x0}_{z0}_{meta['size']}.npy")[sl].copy()
    b = L["b"][sl] > 0
    edge = b & ~ndimage.binary_erosion(b)
    naip[edge] = (255, 0, 0)
    road = np.isin(L["s"][sl], [1, 6, 7, 13])
    redge = road & ~ndimage.binary_erosion(road)
    naip[redge] = (255, 230, 0)
    im = Image.fromarray(naip)
    sc = max(1, 1000 // im.width)
    im.resize((im.width * sc, im.height * sc), Image.NEAREST).save(out)
    print("wrote", out)


if __name__ == "__main__":
    main(*map(int, sys.argv[1:5]), sys.argv[5])
