#!/usr/bin/env python3
"""Ben Hill Griffin Stadium, rebuilt after bake_area.py has written the regions.

The bowl is the LiDAR surface itself (every seat row at its measured height), coloured cell by cell from the 2023 NAIP aerial
photo (blue seats under the press box, aluminium bleachers, orange rim and hoods). The field is drawn to regulation inside the
OSM pitch: 100 yards plus two 10-yard end zones, yard lines every 5 yards, sidelines, GATORS / FLORIDA end-zone lettering
(oriented as in the aerial photo), the midfield logo taken from the aerial photo, goalposts, and the orange field wall.
Called at the end of bake_area.py; safe to re-run.
"""
import gzip, json, math, sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage
from shapely.geometry import Point
from shapely import contains_xy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bake_core as bc
from area import TILE, GRID0

CACHE = HERE / "cache"
R = 256
STADIUM_OSM = 45719890
PITCH_OSM = 119378491
NAME = "Ben Hill Griffin Stadium (The Swamp)"
YARD = 0.9144
# surface codes added for the field (Java: GnvRaster.S_*)
S_ORANGE, S_BLUE, S_GOAL_POST, S_GOAL_BAR, S_GOAL_UPRIGHT = 16, 17, 18, 19, 20
STADIUM_PALETTE = [("minecraft:light_gray_concrete", (170, 172, 176)), ("minecraft:white_concrete", (222, 224, 226)),
                   ("minecraft:blue_concrete", (58, 70, 140)), ("minecraft:light_blue_concrete", (110, 130, 175)),
                   ("minecraft:orange_concrete", (215, 100, 45)), ("minecraft:gray_concrete", (95, 98, 102)),
                   ("minecraft:bricks", (140, 80, 62))]


def osm_poly(osm_id):
    for f in (CACHE / "osm").glob("t_*.json"):
        for e in json.loads(f.read_text()):
            if e["id"] == osm_id:
                return bc.poly_of(e)
    raise SystemExit(f"OSM {osm_id} not found")


class Grid:
    """Read/write access to the baked region layers over a block rectangle."""
    LAYERS = [("gy", "<i2"), ("s", "u1"), ("b", "<u2"), ("roof", "<i2"), ("lt", "<i2"), ("lb", "<i2"), ("f", "u1"), ("c", "u1")]

    def __init__(self, x0, z0, x1, z1):
        self.x0, self.z0 = x0 // R * R, z0 // R * R
        self.x1, self.z1 = (x1 // R + 1) * R, (z1 // R + 1) * R
        W, H = self.x1 - self.x0, self.z1 - self.z0
        self.a = {k: np.zeros((H, W), np.dtype(t)) for k, t in self.LAYERS}
        n = R * R
        for rz in range(self.z0 // R, self.z1 // R):
            for rx in range(self.x0 // R, self.x1 // R):
                raw = gzip.open(bc.OUT / f"r.{rx}.{rz}.bin.gz").read()
                off = 0
                sl = (slice(rz * R - self.z0, rz * R - self.z0 + R), slice(rx * R - self.x0, rx * R - self.x0 + R))
                for k, t in self.LAYERS:
                    arr = np.frombuffer(raw, np.dtype(t), n, off).reshape(R, R)
                    off += arr.nbytes
                    self.a[k][sl] = arr

    def save(self):
        for rz in range(self.z0 // R, self.z1 // R):
            for rx in range(self.x0 // R, self.x1 // R):
                sl = (slice(rz * R - self.z0, rz * R - self.z0 + R), slice(rx * R - self.x0, rx * R - self.x0 + R))
                blob = b"".join(self.a[k][sl].astype(t).tobytes() for k, t in self.LAYERS)
                with gzip.open(bc.OUT / f"r.{rx}.{rz}.bin.gz", "wb", compresslevel=9) as fh:
                    fh.write(blob)


def lidar_dsm(g):
    """First-return surface (max of building and unclassified returns) over the grid, metres."""
    H, W = g.a["gy"].shape
    dsm = np.full((H, W), np.nan, np.float32)
    naip = np.zeros((H, W, 3), np.uint8)
    for tx in range((g.x0 - GRID0) // TILE, (g.x1 - 1 - GRID0) // TILE + 1):
        for tz in range((g.z0 - GRID0) // TILE, (g.z1 - 1 - GRID0) // TILE + 1):
            tx0, tz0 = GRID0 + tx * TILE, GRID0 + tz * TILE
            p = CACHE / "lidar" / f"t_{tx}_{tz}.npz"
            if not p.exists():
                continue
            d = np.load(p)
            surf = np.fmax(d["bld"], d["veg"])
            sx0, sz0 = max(g.x0, tx0), max(g.z0, tz0)
            sx1, sz1 = min(g.x1, tx0 + TILE), min(g.z1, tz0 + TILE)
            dst = (slice(sz0 - g.z0, sz1 - g.z0), slice(sx0 - g.x0, sx1 - g.x0))
            src = (slice(sz0 - tz0, sz1 - tz0), slice(sx0 - tx0, sx1 - tx0))
            dsm[dst] = surf[src]
            npth = CACHE / f"naip_{tx0}_{tz0}_{TILE}.npy"
            if npth.exists():
                naip[dst] = np.load(npth)[src]
    return dsm, naip


def text_mask(text, w, h):
    """Block-letter mask (h rows x w cols) of text filling a w x h box."""
    img = Image.new("L", (w * 8, h * 8), 0)
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Black.ttf", int(h * 8 * 0.95))
    except OSError:
        font = ImageFont.load_default()
    bb = d.textbbox((0, 0), text, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    scale = min(w * 8 * 0.92 / tw, h * 8 * 0.8 / th)
    font = ImageFont.truetype(font.path, int(h * 8 * 0.95 * scale)) if hasattr(font, "path") else font
    bb = d.textbbox((0, 0), text, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((w * 8 - tw) / 2 - bb[0], (h * 8 - th) / 2 - bb[1]), text, fill=255, font=font)
    return np.array(img.resize((w, h), Image.BOX)) > 100


def main():
    stadium = osm_poly(STADIUM_OSM)
    pitch = osm_poly(PITCH_OSM)
    x0, z0, x1, z1 = [int(v) for v in stadium.buffer(12).bounds]
    g = Grid(x0, z0, x1, z1)
    H, W = g.a["gy"].shape
    xs, zs = np.meshgrid(np.arange(W) + g.x0 + 0.5, np.arange(H) + g.z0 + 0.5)
    in_stadium = contains_xy(stadium, xs, zs)

    # field frame from the pitch polygon: long axis, centre
    rect = pitch.minimum_rotated_rectangle
    pts = np.array(rect.exterior.coords)[:4]
    edges = [pts[(i + 1) % 4] - pts[i] for i in range(4)]
    # the real field is 0.9 degrees off grid north; snapping it to the block grid keeps every line straight (ends move < 1 m)
    u = np.array([0.0, 1.0])                    # along the field, +u points south
    n = np.array([-1.0, 0.0])
    c = np.round(np.array(rect.centroid.coords[0])) + 0.5
    along = (xs - c[0]) * u[0] + (zs - c[1]) * u[1]
    across = (xs - c[0]) * n[0] + (zs - c[1]) * n[1]
    L, Wd = 120 * YARD, 160 / 3 * YARD          # 120 yards x 53 1/3 yards
    field = (np.abs(along) <= L / 2) & (np.abs(across) <= Wd / 2)
    apron = (np.abs(along) <= L / 2 + 6) & (np.abs(across) <= Wd / 2 + 7)
    wall = apron & ~((np.abs(along) <= L / 2 + 5) & (np.abs(across) <= Wd / 2 + 6))

    dsm, naip = lidar_dsm(g)
    gy = g.a["gy"].astype(np.int32)
    ground_field = int(np.median(gy[field]))
    seat_y = np.where(np.isnan(dsm), gy, np.round(bc.SURFACE_Y + (dsm - bc.BASE_M))).astype(np.int32)
    seat_y = ndimage.median_filter(seat_y, size=3)
    bowl = in_stadium & ~apron & (seat_y - gy >= 2)
    bowl = ndimage.binary_closing(bowl, structure=np.ones((3, 3)), iterations=2) & in_stadium & ~apron
    bowl = ndimage.binary_fill_holes(bowl | apron) & ~apron & in_stadium

    # building table: drop the old pieces of the stadium, add one stadium building
    meta = json.loads((bc.OUT / "buildings.json").read_text())
    table = meta["buildings"]
    old = set(np.unique(g.a["b"][in_stadium]).tolist()) - {0}
    pal = meta["roofPalette"]
    base_idx = {}
    for blk, _ in STADIUM_PALETTE:
        if blk not in pal:
            pal.append(blk)
        base_idx[blk] = pal.index(blk) + 1
    sid = next((t["i"] for t in table if t.get("name") == NAME), None)
    if sid is None:
        sid = len(table) + 1
        table.append({})
    for t in table:
        if t.get("i") in old and t.get("i") != sid:
            t["name"] = None
    b = g.a["b"]
    b[in_stadium & np.isin(b, list(old))] = 0
    cells = bowl | wall
    b[cells] = sid
    base = int(np.median(gy[bowl]))
    gy[cells] = base
    gy[apron & ~wall] = ground_field
    roof = np.where(wall, ground_field + 1, np.clip(seat_y, base + 3, base + 70))
    g.a["roof"][cells] = roof[cells]
    g.a["lt"][in_stadium] = 0
    g.a["lb"][in_stadium] = 0
    g.a["f"][in_stadium] = 0
    rows, cols = np.where(cells)
    table[sid - 1] = dict(i=sid, name=NAME, kind="stadium", levels=6, baseY=base, floorH=5, topY=int(roof[cells].max()),
                          minX=int(g.x0 + cols.min()), minZ=int(g.z0 + rows.min()), maxX=int(g.x0 + cols.max()), maxZ=int(g.z0 + rows.max()),
                          addr="157 Gale Lemerand Dr", src="lidar", osm=STADIUM_OSM, door=None, stair=None, elevator=None,
                          fieldX=float(c[0]), fieldZ=float(c[1]),
                          facade=dict(base="minecraft:bricks", baseFloors=3, upper="minecraft:light_gray_concrete", band="minecraft:orange_concrete", bandEvery=0))

    # seat colours by section, from the photos and the seating chart:
    #   west sideline (under the press box): blue seats with orange aisles; east sideline and both end zones: striped aluminium
    #   bleachers with orange vomitory hoods; the north end's middle tier (Touchdown Terrace) has the orange/red band; orange rim
    #   along every top edge; the Gator Vision boards at both ends are white.
    I = lambda blk: base_idx[blk]
    dx, dz = xs - c[0], zs - c[1]
    end = np.abs(dz) / (L / 2) > np.abs(dx) / (Wd / 2)
    west = ~end & (dx < 0)
    north = end & (dz < 0)
    h = roof - base
    hmax_n = np.percentile(h[bowl & north], 95) if (bowl & north).any() else 30
    lateral = np.where(end, xs, zs).astype(int)
    col = np.where(h % 2 == 0, I("minecraft:white_concrete"), I("minecraft:light_gray_concrete")).astype(np.uint8)
    col[west] = I("minecraft:blue_concrete")
    col[west & (np.mod(lateral, 13) == 0)] = I("minecraft:orange_concrete")                      # aisles
    band = north & (h > hmax_n * 0.42) & (h < hmax_n * 0.58)
    col[band] = I("minecraft:orange_concrete")
    hoods = ~west & (np.mod(lateral, 14) < 2) & (np.mod(h, 9) == 4)
    col[hoods] = I("minecraft:orange_concrete")
    boards = end & (h > 32)
    col[boards] = I("minecraft:white_concrete")
    col[wall] = I("minecraft:orange_concrete")
    rim = bowl & ~ndimage.binary_erosion(bowl, iterations=2) & (roof > base + 12)
    col[rim] = I("minecraft:orange_concrete")
    g.a["c"][cells] = col[cells]
    g.a["c"][in_stadium & ~cells] = 0

    # field
    s = g.a["s"]
    s[apron & ~wall] = bc.SURFACE["turf"]
    a_yd = (along + L / 2) / YARD                # 0 .. 120 yards from the north end line
    lines = field & (np.abs(across) <= Wd / 2 - 0.3)
    for yd in range(10, 111, 5):
        s[lines & (np.abs(a_yd - yd) < 0.55)] = bc.SURFACE["lane_white"]
    border = field & ((np.abs(across) > Wd / 2 - 1.0) | (a_yd < 0.9) | (a_yd > 119.1))
    s[border] = bc.SURFACE["lane_white"]
    # end zones: GATORS (north, letters' tops toward the north end line) and FLORIDA (south, tops toward the south end line)
    ez_w, ez_h = int(Wd) - 6, int(10 * YARD) - 3
    for word, north in (("GATORS", True), ("FLORIDA", False)):
        m = text_mask(word, ez_w, ez_h)
        outline = ndimage.binary_dilation(m, iterations=1) & ~m
        sel = field & ((a_yd > 0.9) & (a_yd < 10) if north else (a_yd > 110) & (a_yd < 119.1))
        rr = np.where(sel)
        for k, i in zip(*rr):
            ay = a_yd[k, i] - (0 if north else 110)
            row = int((ay - 1.5) / (10 - 3) * ez_h) if north else int((10 - ay - 1.5) / (10 - 3) * ez_h)
            colm = int((across[k, i] + ez_w / 2))
            if not north:
                colm = ez_w - 1 - colm
            if 0 <= row < ez_h and 0 <= colm < ez_w:
                if m[row, colm]:
                    s[k, i] = S_BLUE
                elif outline[row, colm]:
                    s[k, i] = S_ORANGE
    # yard numbers (10 20 30 40 50 40 30 20 10), 9 yards in from each sideline, tops toward the sideline
    for yd in range(20, 101, 10):
        num = str(min(yd - 10, 110 - yd))
        m = text_mask(num, 6, 4)                   # 6 along the field x 4 across
        for side in (-1, 1):
            cx_ = Wd / 2 - 9 * YARD
            sel = field & (np.abs(a_yd - yd) <= 3.2) & (np.abs(across - side * cx_) <= 2)
            for k, i in zip(*np.where(sel)):
                r_ = int(round((across[k, i] - side * cx_) * side + 1.5))      # 0..3, top of the digits toward the sideline
                c_ = int(round((a_yd[k, i] - yd) * YARD * side + 2.5))
                if 0 <= r_ < 4 and 0 <= c_ < 6 and m[3 - r_, c_]:
                    s[k, i] = bc.SURFACE["lane_white"]
    # midfield logo straight from the aerial photo (orange / blue / green / white)
    logo = field & (np.abs(a_yd - 60) < 7) & (np.abs(across) < 7)
    lc = naip.astype(np.int32)
    orange = logo & (lc[..., 0] > lc[..., 2] + 50) & (lc[..., 0] > 140)
    blue = logo & (lc[..., 2] > lc[..., 0] + 25) & (lc[..., 2] > 90)
    white = logo & (lc.sum(-1) > 560)
    s[orange] = S_ORANGE
    s[blue] = S_BLUE
    s[white & ~orange & ~blue] = bc.SURFACE["lane_white"]
    # goalposts: single post behind each end line, 18.5 ft crossbar, uprights
    for sign in (-1, 1):
        endline = sign * (L / 2)
        post = np.abs(along - (endline + sign * 1.2)) < 0.5
        s[post & (np.abs(across) < 0.5)] = S_GOAL_POST
        bar = (np.abs(along - endline) < 0.5) & (np.abs(across) <= 2.8)
        s[bar] = S_GOAL_BAR
        s[bar & (np.abs(np.abs(across) - 2.8) < 0.5)] = S_GOAL_UPRIGHT
    g.a["gy"] = gy.astype(np.int16)
    g.save()
    (bc.OUT / "buildings.json").write_text(json.dumps(meta, separators=(",", ":")))
    print(f"stadium #{sid}: {int(bowl.sum())} bowl cells, top Y {int(roof[cells].max())} (base {base}), field ground {ground_field}")


if __name__ == "__main__":
    main()
