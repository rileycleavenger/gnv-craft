#!/usr/bin/env python3
"""Post-bake fixes from overrides.json "cuts": clear a strip of building cells (where the LiDAR clean-up merged two buildings
across a gap), split the buildings it crossed into their connected pieces, and re-apply the point overrides so each piece gets
its own name and facade. Called at the end of bake_area.py, before bake_stadium.py; safe to re-run."""
import json, sys
from pathlib import Path
import numpy as np
from scipy import ndimage

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bake_core as bc
from bake_stadium import Grid


def main():
    ov = json.loads((HERE / "overrides.json").read_text())
    meta = json.loads((bc.OUT / "buildings.json").read_text())
    table = meta["buildings"]
    for cut in ov.get("cuts", []):
        x0, z0, x1, z1 = cut["rect"]
        g = Grid(x0 - 160, z0 - 160, x1 + 160, z1 + 160)
        b = g.a["b"]
        sl = (slice(z0 - g.z0, z1 - g.z0 + 1), slice(x0 - g.x0, x1 - g.x0 + 1))
        hit = set(np.unique(b[sl]).tolist()) - {0}
        cleared = b[sl] != 0
        b[sl][cleared] = 0
        g.a["roof"][sl][cleared] = 0
        g.a["c"][sl][cleared] = 0
        g.a["s"][sl][cleared] = bc.SURFACE[cut.get("surface", "concrete")]
        for bi in hit:
            lab, n = ndimage.label(b == bi)
            if n <= 1:
                continue
            sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
            keep = int(np.argmax(sizes)) + 1
            for part in range(1, n + 1):
                if part == keep or sizes[part - 1] < 12:
                    if part != keep:
                        b[lab == part] = 0
                    continue
                new = dict(table[bi - 1]); new["i"] = len(table) + 1; new["name"] = None; new.pop("facade", None)
                rows, cols = np.where(lab == part)
                new.update(minX=int(g.x0 + cols.min()), maxX=int(g.x0 + cols.max()), minZ=int(g.z0 + rows.min()), maxZ=int(g.z0 + rows.max()))
                table.append(new)
                b[lab == part] = new["i"]
            rows, cols = np.where(b == bi)
            table[bi - 1].update(minX=int(g.x0 + cols.min()), maxX=int(g.x0 + cols.max()), minZ=int(g.z0 + rows.min()), maxZ=int(g.z0 + rows.max()))
        g.save()
        print("cut", cut["rect"], "split", sorted(hit))
    # re-apply point overrides (names / facades) now that pieces exist
    for o in ov["buildings"]:
        px, pz = o["point"]
        g = Grid(px, pz, px, pz)
        bi = int(g.a["b"][pz - g.z0, px - g.x0])
        if not bi:
            print("override point not in a building:", o["name"]); continue
        e = table[bi - 1]
        e.update(name=o["name"], kind=o["kind"], levels=o["levels"], addr=o["addr"])
        if "facade" in o:
            e["facade"] = o["facade"]
        e["floorH"] = max(3, min(6, round((e["topY"] - e["baseY"]) / max(1, o["levels"]))))
    if len(table) >= 65535:
        sys.exit("too many buildings")
    (bc.OUT / "buildings.json").write_text(json.dumps(meta, separators=(",", ":")))


if __name__ == "__main__":
    main()
