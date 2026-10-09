#!/usr/bin/env python3
"""NAIP aerial photo for every V1 tile (cache/naip_<x0>_<z0>_1024.npy)."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from area import tiles, TILE
from fetch_naip import main, CACHE

for tx, tz, x0, z0 in tiles():
    if (CACHE / f"naip_{x0}_{z0}_{TILE}.npy").exists():
        continue
    for attempt in range(3):
        try:
            main(x0, z0, TILE)
            break
        except Exception as ex:
            print("retry", tx, tz, ex, flush=True)
print("ALL_NAIP_DONE", flush=True)
