#!/usr/bin/env python3
"""Side-by-side check image: NAIP aerial photo | baked map (surfaces, buildings shaded by height, trees). Crop in block coords."""
import gzip, json, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
R = ROOT / "src/main/resources/data/gnvcraft/gnv_map/raster"
CACHE = Path(__file__).resolve().parent / "cache"
COLORS = {0: (96, 140, 60), 1: (70, 70, 74), 2: (190, 190, 186), 3: (90, 90, 94), 4: (40, 90, 200), 5: (220, 200, 140), 6: (250, 250, 250),
          7: (240, 200, 30), 8: (250, 250, 250), 9: (60, 160, 60), 10: (160, 70, 50), 11: (150, 120, 80), 12: (130, 130, 130), 13: (250, 250, 250),
          14: (180, 60, 50), 15: (150, 150, 150)}


def load():
    meta = json.loads((R / "buildings.json").read_text())
    x0, z0, size, reg = meta["x0"], meta["z0"], meta["size"], meta["region"]
    n = reg * reg
    L = {k: np.zeros((size, size), np.int32) for k in ("g", "s", "b", "r", "lt", "lb", "f")}
    for rk in range(size // reg):
        for ri in range(size // reg):
            rx, rz = (x0 + ri * reg) // reg, (z0 + rk * reg) // reg
            raw = gzip.open(R / f"r.{rx}.{rz}.bin.gz").read()
            off = 0
            def take(dtype, count):
                nonlocal off
                a = np.frombuffer(raw, dtype, count, off); off += a.nbytes; return a.reshape(reg, reg)
            sl = (slice(rk * reg, (rk + 1) * reg), slice(ri * reg, (ri + 1) * reg))
            L["g"][sl] = take("<i2", n); L["s"][sl] = take("u1", n); L["b"][sl] = take("<u2", n); L["r"][sl] = take("<i2", n)
            L["lt"][sl] = take("<i2", n); L["lb"][sl] = take("<i2", n); L["f"][sl] = take("u1", n)
    return meta, L


def main(cx0, cz0, cx1, cz1, out):
    meta, L = load()
    x0, z0 = meta["x0"], meta["z0"]
    sl = (slice(cz0 - z0, cz1 - z0), slice(cx0 - x0, cx1 - x0))
    s = L["s"][sl]
    img = np.zeros(s.shape + (3,), np.uint8)
    for k, c in COLORS.items():
        img[s == k] = c
    b = L["b"][sl] > 0
    h = (L["r"][sl] - L["g"][sl]).astype(np.float32)
    shade = np.clip(110 + h * 3.5, 110, 255).astype(np.uint8)
    img[b] = np.stack([shade, shade * 0.85, shade * 0.7], -1).astype(np.uint8)[b]
    tree = (L["lt"][sl] > 0) & ~b
    img[tree] = (img[tree] * 0.35 + np.array([20, 110, 30]) * 0.65).astype(np.uint8)
    img[L["f"][sl] > 0] = (90, 60, 30)
    naip = np.load(CACHE / f"naip_{x0}_{z0}_{meta['size']}.npy")[sl]
    a = Image.fromarray(naip); m = Image.fromarray(img)
    W, H = a.size
    scale = max(1, 900 // W)
    a = a.resize((W * scale, H * scale), Image.NEAREST); m = m.resize((W * scale, H * scale), Image.NEAREST)
    canvas = Image.new("RGB", (a.width * 2 + 10, a.height), "white")
    canvas.paste(a, (0, 0)); canvas.paste(m, (a.width + 10, 0))
    d = ImageDraw.Draw(canvas)
    for t in meta["buildings"]:
        if t["name"] and cx0 <= (t["minX"] + t["maxX"]) // 2 < cx1 and cz0 <= (t["minZ"] + t["maxZ"]) // 2 < cz1:
            px = ((t["minX"] + t["maxX"]) // 2 - cx0) * scale + a.width + 10
            pz = ((t["minZ"] + t["maxZ"]) // 2 - cz0) * scale
            d.text((px - 20, pz), f"{t['i']} {t['name'][:18]}", fill=(255, 0, 0))
    canvas.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main(*map(int, sys.argv[1:5]), sys.argv[5])
