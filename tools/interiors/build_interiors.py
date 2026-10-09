#!/usr/bin/env python3
"""Authors the researched buildings (The Standard, The Hub on 3rd Ave, 201 NW 10th St) as box-fill specs.

Output: src/main/resources/data/gnvcraft/gnv_interiors/<id>.json + index.json
Coordinates are relative to the anchor (NW corner); y = 0 is the ground surface block, y = 1 is the first air block.
These are best-effort reconstructions: the facts they are built from are listed in docs/research/*.md.
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent.parent / "src/main/resources/data/gnvcraft/gnv_interiors"
FH = 4  # floor height


class B:
    def __init__(self, id_, name, address, note, anchor, size, hide=()):
        self.meta = dict(id=id_, name=name, address=address, note=note, anchor=list(anchor), size=list(size), hide_osm=list(hide))
        self.pal, self.ops = [], []

    def idx(self, block):
        if block not in self.pal:
            self.pal.append(block)
        return self.pal.index(block)

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        self.ops.append([min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1), self.idx(block)])

    def set(self, x, y, z, block):
        self.fill(x, y, z, x, y, z, block)

    def save(self):
        OUT.mkdir(parents=True, exist_ok=True)
        d = dict(self.meta, palette=self.pal, ops=self.ops)
        (OUT / (self.meta["id"] + ".json")).write_text(json.dumps(d, separators=(",", ":")))
        print(self.meta["id"], len(self.ops), "ops")


def door(b, x, y, z, facing="north"):
    b.set(x, y, z, f"minecraft:oak_door[facing={facing},half=lower,hinge=left,open=false]")
    b.set(x, y + 1, z, f"minecraft:oak_door[facing={facing},half=upper,hinge=left,open=false]")


def bed(b, x, y, z, facing, color="red"):
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    b.set(x, y, z, f"minecraft:{color}_bed[part=foot,facing={facing}]")
    b.set(x + dx, y, z + dz, f"minecraft:{color}_bed[part=head,facing={facing}]")


STAIR = lambda mat, facing: f"minecraft:{mat}_stairs[facing={facing},half=bottom]"


def room_walls(b, x0, z0, x1, z1, y0, y1, block):
    b.fill(x0, y0, z0, x1, y1, z0, block)
    b.fill(x0, y0, z1, x1, y1, z1, block)
    b.fill(x0, y0, z0, x0, y1, z1, block)
    b.fill(x1, y0, z0, x1, y1, z1, block)


def complex_shell(b, W, D, floors, wall, trim, glass="minecraft:glass_pane"):
    top = FH * floors
    b.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:polished_andesite")                 # ground slab
    b.fill(0, 1, 0, W - 1, top, D - 1, "minecraft:air")
    for f in range(1, floors + 1):
        b.fill(1, FH * f, 1, W - 2, FH * f, D - 2, "minecraft:smooth_stone")        # floor slab above floor f
    b.fill(0, 1, 0, W - 1, top, 0, wall)
    b.fill(0, 1, D - 1, W - 1, top, D - 1, wall)
    b.fill(0, 1, 0, 0, top, D - 1, wall)
    b.fill(W - 1, 1, 0, W - 1, top, D - 1, wall)
    for f in range(floors):                                                          # floor-to-ceiling window bands
        y = FH * f + 1
        for x in range(2, W - 2, 4):
            for z in (0, D - 1):
                b.fill(x, y, z, x + 2, y + 2, z, glass)
        for z in range(3, D - 3, 4):
            for x in (0, W - 1):
                b.fill(x, y, z, x, y + 2, z + 1, glass)
        b.fill(0, FH * f + 4, 0, W - 1, FH * f + 4, 0, trim)
        b.fill(0, FH * f + 4, D - 1, W - 1, FH * f + 4, D - 1, trim)
    b.fill(0, top, 0, W - 1, top, D - 1, "minecraft:stone_bricks")                  # roof
    b.fill(0, top + 1, 0, W - 1, top + 1, D - 1, trim)
    b.fill(1, top + 1, 1, W - 2, top + 1, D - 2, "minecraft:air")


NU = 13  # unit depth; north walls z=0, north partition z=14, corridor z=15..17, south partition z=18, south wall z=32
def zmap(row, v):
    return 14 - v if row == "n" else 18 + v


def corridor(b, W, floors):
    for f in range(1, floors + 1):
        y = FH * (f - 1)
        for z in (14, 18):
            b.fill(1, y + 1, z, W - 2, y + 3, z, "minecraft:white_concrete")
        for x in range(8, W - 6, 8):
            b.set(x, y + 3, 16, "minecraft:sea_lantern")
        b.fill(1, y, 15, W - 2, y, 17, "minecraft:gray_carpet") if False else None
    # elevators and a ladder stair at the corridor ends; hole through every slab for the ladder
    for ex in (3, W - 5):
        for f in range(0, floors + 1):
            b.set(ex, FH * f, 16, "gnvcraft:elevator")
    for y in range(1, FH * floors + 2):
        b.set(W - 2, y, 16, "minecraft:ladder[facing=west]")
    for f in range(1, floors + 1):
        b.set(W - 2, FH * f, 16, "minecraft:ladder[facing=west]")
    b.set(W - 2, FH * floors + 1, 16, "minecraft:ladder[facing=west]")


def unit(b, f, row, x0, x1, bedrooms, baths, color):
    y = FH * (f - 1)
    w = x1 - x0
    # partition walls between neighbours
    b.fill(x0, y + 1, zmap(row, 0), x0, y + 3, zmap(row, NU), "minecraft:white_concrete")
    # entrance door from the corridor
    wallz = zmap(row, 0)
    door(b, x0 + 2, y + 1, wallz, "south" if row == "n" else "north")
    # kitchen along the corridor wall, living area, couch and TV
    kz = zmap(row, 1)
    b.fill(x0 + 3, y + 1, kz, min(x0 + 7, x1 - 1), y + 1, kz, "minecraft:smooth_quartz")
    b.set(x0 + 4, y + 2, kz, "minecraft:furnace")
    b.set(x0 + 6, y + 2, kz, "minecraft:cauldron")
    face = "north" if row == "s" else "south"
    for cx in range(x0 + 2, min(x0 + 6, x1 - 1)):
        b.set(cx, y + 1, zmap(row, 4), STAIR("quartz", face))
    b.set(x0 + 3, y + 3, zmap(row, 3), "minecraft:sea_lantern")
    # bedroom partition with a door per bedroom and a bed at the window
    pv = 7
    b.fill(x0 + 1, y + 1, zmap(row, pv), x1 - 1, y + 3, zmap(row, pv), "minecraft:oak_planks")
    n = max(1, bedrooms)
    cell = max(3, (w - 1) // n)
    for i in range(n):
        bx0 = x0 + 1 + i * cell
        bx1 = min(x1 - 1, bx0 + cell - 1)
        if i > 0:
            b.fill(bx0 - 1 if bx0 - 1 > x0 else bx0, y + 1, zmap(row, pv + 1), bx0 - 1 if bx0 - 1 > x0 else bx0, y + 3, zmap(row, 13), "minecraft:oak_planks")
        mid = (bx0 + bx1) // 2
        door(b, mid, y + 1, zmap(row, pv), "south" if row == "n" else "north")
        bed(b, mid, y + 1, zmap(row, 11), "north" if row == "n" else "south", color)
        b.set(min(bx1, mid + 1), y + 1, zmap(row, 12), "minecraft:chest[facing=" + ("south" if row == "n" else "north") + "]")
        b.set(mid, y + 3, zmap(row, 9), "minecraft:glowstone")
    # bathroom(s) tucked into the living area corner
    for k in range(max(1, baths // 2 + baths % 2) if baths else 0):
        bx1 = x1 - 1 - k * 4
        bx0 = bx1 - 3
        if bx0 <= x0 + 7:
            break
        room_walls(b, bx0, zmap(row, 2) if row == "s" else zmap(row, 6), bx1, zmap(row, 6) if row == "s" else zmap(row, 2), y + 1, y + 3, "minecraft:smooth_quartz")
        b.fill(bx0 + 1, y + 1, min(zmap(row, 3), zmap(row, 5)), bx1 - 1, y + 3, max(zmap(row, 3), zmap(row, 5)), "minecraft:air")
        b.set(bx0 + 1, y + 1, zmap(row, 4), "minecraft:cauldron")
        b.set(bx1 - 1, y + 1, zmap(row, 5), "minecraft:quartz_slab")
        door(b, bx0 + 1, y + 1, zmap(row, 2) if row == "s" else zmap(row, 6), "south" if row == "n" else "north")


def tile_units(b, W, f, plans, colors):
    """plans: list of (name, width, bedrooms, baths); cycles along each row."""
    for row in ("n", "s"):
        x = 0
        i = (f * 3 + (0 if row == "n" else 2)) % len(plans)
        while x < W - 8:
            name, w, bd, ba = plans[i % len(plans)]
            x1 = min(x + w, W - 1)
            if W - 1 - x1 < 6:
                x1 = W - 1
            unit(b, f, row, x, x1, bd, ba, colors[(i + f) % len(colors)])
            x = x1
            i += 1


def room(b, x0, z0, x1, z1, y, wall, floor=None, doorx=None, doorz=None, lit=True):
    room_walls(b, x0, z0, x1, z1, y + 1, y + 3, wall)
    if floor:
        b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, floor)
    if doorz is not None:
        door(b, doorx, y + 1, doorz, "north" if doorz == z1 else "south")
    if lit:
        b.set((x0 + x1) // 2, y + 3, (z0 + z1) // 2, "minecraft:sea_lantern")


def pool(b, x0, z0, x1, z1, top, rim="minecraft:quartz_block"):
    room_walls(b, x0, z0, x1, z1, top + 1, top + 2, rim)
    b.fill(x0 + 1, top + 1, z0 + 1, x1 - 1, top + 1, z1 - 1, "minecraft:water")


def cabana(b, x, z, top):
    for dx in (0, 3):
        for dz in (0, 3):
            b.fill(x + dx, top + 1, z + dz, x + dx, top + 3, z + dz, "minecraft:birch_fence")
    b.fill(x, top + 4, z, x + 3, top + 4, z + 3, "minecraft:white_wool")
    b.fill(x + 1, top + 1, z + 1, x + 2, top + 1, z + 2, "minecraft:white_carpet")


# ---------------------------------------------------------------- The Standard
def the_standard():
    W, D, F = 64, 33, 10
    # NW corner of University Ave & SW 13th St side of the block; footprint chosen from the address (1360 W University Ave),
    # since OSM has no footprint for it. Replaces the small OSM retail building at that spot.
    b = B("the_standard", "The Standard at Gainesville", "1360 W University Ave, Gainesville, FL 32603",
          "10 stories, 430 units / 1,200 beds (2017). Footprint position is estimated from the address; units use the published plan names.",
          (-175, -66), (W, F * FH + 4, D), hide=[1010270696])
    complex_shell(b, W, D, F, "minecraft:white_concrete", "minecraft:light_gray_concrete")
    corridor(b, W, F)
    # published plans: (name, width, bedrooms, baths)
    plans = [("Soho", 7, 1, 1), ("Astoria", 8, 1, 1), ("Brookhaven", 10, 2, 2), ("Chelsea", 12, 3, 3), ("Deerwood", 14, 4, 3), ("Hounslow", 16, 5, 4), ("Greenwich", 18, 6, 6)]
    colors = ["red", "blue", "orange", "light_blue"]
    for f in range(2, F + 1):
        tile_units(b, W, f, plans, colors)
    # ground floor amenities (management center on the first floor)
    y = 0
    b.fill(1, 1, 1, W - 2, 3, D - 2, "minecraft:air")
    for yy in range(1, FH):
        b.set(W - 2, yy, 16, "minecraft:ladder[facing=west]")
    # lobby + management center by the main entrance (south wall facing University Ave)
    b.fill(26, 1, D - 1, 30, 2, D - 1, "minecraft:air")
    b.fill(33, 1, D - 1, 37, 2, D - 1, "minecraft:air")
    door(b, 28, 1, D - 1, "south")
    door(b, 35, 1, D - 1, "south")
    b.fill(24, 0, 20, 40, 0, D - 2, "minecraft:polished_diorite")
    b.fill(26, 1, 22, 38, 1, 22, "minecraft:smooth_quartz")                         # leasing desk
    b.fill(27, 1, 25, 29, 1, 25, STAIR("quartz", "north"))
    # fitness center + cardio studio (north row west)
    room(b, 1, 1, 22, 13, 0, "minecraft:white_concrete", "minecraft:gray_concrete", 12, 13)
    for x in range(3, 20, 3):
        b.set(x, 1, 3, "minecraft:iron_block"); b.set(x, 2, 3, "minecraft:anvil")
    for x in range(3, 20, 3):
        b.set(x, 1, 9, "minecraft:smooth_stone_slab")
    # computer lab + study lounges
    room(b, 42, 1, 62, 13, 0, "minecraft:white_concrete", "minecraft:light_gray_carpet", 50, 13)
    for x in range(44, 61, 3):
        b.set(x, 1, 3, "minecraft:lectern"); b.set(x, 1, 8, "minecraft:lectern")
    room(b, 1, 19, 22, 31, 0, "minecraft:white_concrete", "minecraft:oak_planks", 12, 19)
    for x in range(3, 20, 5):
        b.fill(x, 1, 24, x + 2, 1, 26, "minecraft:spruce_slab")
    # arcade / game room (south row east)
    room(b, 42, 19, 62, 31, 0, "minecraft:black_concrete", "minecraft:gray_carpet", 50, 19)
    for x in range(44, 60, 4):
        b.set(x, 1, 21, "minecraft:note_block"); b.set(x, 2, 21, "minecraft:jukebox") if x == 44 else None
    b.fill(48, 1, 25, 56, 1, 27, "minecraft:green_carpet")                           # pool table
    # rooftop: pool, cabanas, clubhouse
    top = FH * F
    pool(b, 6, 6, 32, 16, top)
    cabana(b, 36, 6, top); cabana(b, 41, 6, top); cabana(b, 46, 6, top)
    room_walls(b, 38, 20, 60, 28, top + 1, top + 3, "minecraft:glass")
    b.fill(39, top + 1, 21, 59, top + 3, 27, "minecraft:air")
    b.fill(38, top + 4, 20, 60, top + 4, 28, "minecraft:white_concrete")
    b.set(38, top + 1, 24, "minecraft:air"); b.set(38, top + 2, 24, "minecraft:air")
    b.fill(41, top + 1, 22, 55, top + 1, 22, STAIR("quartz", "south"))
    b.save()


# ---------------------------------------------------------------- The Hub on 3rd Ave
def the_hub():
    W, D, F = 72, 33, 7
    b = B("the_hub_3rd_ave", "Hub Gainesville (3rd Ave)", "1258 NW 3rd Ave, Gainesville, FL 32601",
          "7 stories, 201 units / 661 beds (2020), 12,483 sq ft of ground-floor retail. Footprint position is estimated from the address (OSM has none).",
          (22, -262), (W, F * FH + 4, D))
    complex_shell(b, W, D, F, "minecraft:gray_concrete", "minecraft:black_concrete")
    corridor(b, W, F)
    plans = [("Studio", 6, 1, 1), ("1x1", 7, 1, 1), ("2x2 VIP", 9, 2, 2), ("3x2", 11, 3, 2), ("4x4 Balcony", 14, 4, 4), ("4x2 Lite Mansion", 14, 4, 2), ("4x3", 14, 4, 3), ("5x4", 17, 5, 4)]
    colors = ["gray", "cyan", "light_blue", "white"]
    for f in range(2, F + 1):
        tile_units(b, W, f, plans, colors)
    b.fill(1, 1, 1, W - 2, 3, D - 2, "minecraft:air")
    for yy in range(1, FH):
        b.set(W - 2, yy, 16, "minecraft:ladder[facing=west]")
    # retail along the street side (south): four storefronts with counters and shelves
    xs = [1, 18, 36, 54, 70]
    for i in range(4):
        x0, x1 = xs[i], xs[i + 1]
        room_walls(b, x0, 19, x1, 31, 1, 3, "minecraft:smooth_stone")
        b.fill(x0 + 1, 1, 20, x1 - 1, 1, 30, "minecraft:air")
        b.fill(x0 + 1, 0, 20, x1 - 1, 0, 30, "minecraft:birch_planks")
        b.fill(x0 + 2, 1, 22, x1 - 2, 1, 22, "minecraft:smooth_quartz")
        for sx in range(x0 + 3, x1 - 2, 3):
            b.set(sx, 1, 28, "minecraft:bookshelf"); b.set(sx, 2, 28, "minecraft:bookshelf")
        b.fill((x0 + x1) // 2 - 1, 1, D - 1, (x0 + x1) // 2 + 1, 2, D - 1, "minecraft:air")
        door(b, (x0 + x1) // 2, 1, D - 1, "south")
        b.set((x0 + x1) // 2, 3, 25, "minecraft:sea_lantern")
    # lobby / leasing / mail (north row center), fitness + yoga + sauna + steam (north row west), business center, study lounge
    room(b, 24, 1, 44, 13, 0, "minecraft:white_concrete", "minecraft:polished_diorite", 34, 13)
    b.fill(26, 1, 6, 42, 1, 6, "minecraft:smooth_quartz")
    b.fill(33, 1, 0, 35, 2, 0, "minecraft:air"); door(b, 34, 1, 0, "north")
    room(b, 1, 1, 14, 13, 0, "minecraft:white_concrete", "minecraft:gray_concrete", 8, 13)
    for x in range(3, 13, 3):
        b.set(x, 1, 3, "minecraft:iron_block"); b.set(x, 2, 3, "minecraft:anvil")
    room(b, 15, 1, 22, 6, 0, "minecraft:glass", "minecraft:birch_planks", 18, 6)                  # yoga studio (mirrors)
    room(b, 15, 8, 18, 13, 0, "minecraft:spruce_planks", "minecraft:spruce_planks", 16, 13)       # sauna
    b.set(16, 1, 10, "minecraft:spruce_stairs[facing=east,half=bottom]")
    room(b, 19, 8, 22, 13, 0, "minecraft:white_concrete", "minecraft:light_blue_concrete", 20, 13)  # steam room
    room(b, 46, 1, 58, 13, 0, "minecraft:white_concrete", "minecraft:light_gray_carpet", 52, 13)   # business center
    for x in range(48, 57, 3):
        b.set(x, 1, 3, "minecraft:lectern")
    room(b, 59, 1, 70, 13, 0, "minecraft:white_concrete", "minecraft:oak_planks", 64, 13)         # study lounge
    b.fill(61, 1, 6, 68, 1, 8, "minecraft:spruce_slab")
    # rooftop sundeck: pool and hot tub, grills, clubhouse
    top = FH * F
    pool(b, 8, 6, 36, 16, top)
    pool(b, 40, 6, 46, 12, top)
    for gx in (50, 54, 58):
        b.set(gx, top + 1, 7, "minecraft:smoker[facing=south]")
    room_walls(b, 44, 20, 66, 28, top + 1, top + 3, "minecraft:glass")
    b.fill(45, top + 1, 21, 65, top + 3, 27, "minecraft:air")
    b.fill(44, top + 4, 20, 66, top + 4, 28, "minecraft:gray_concrete")
    b.save()


# ---------------------------------------------------------------- 201 NW 10th St
def house():
    """201 NW 10th St, from the 37 listing photos (reference/201-nw-10th, git-ignored) on the footprint the LiDAR and 2023 aerial
    photo show (x 428..443, z -152..-138, front facing NW 10th St to the west, red-brick driveway along the south side).

    Exterior: pale-yellow textured stucco, white window trim, kelly-green front door with a pale-green surround and steps, a carport
    in the front-south corner with a second green door, dark-gray shingle gable roof with light siding in the front gable.
    Inside: enclosed tile-floored sunroom across the front (white stucco walls, red-painted inside of the front door, washer/dryer at
    the north end) with a granite-topped pass-through into the hardwood living room; French doors to a front bedroom; white-tile
    kitchen with oak cabinets and black granite; four hardwood bedrooms and three baths. Room sizes are inferred from the photos.
    """
    U, V = 15, 16        # u: north -> south along the street, v: west (front) -> east (back)
    YX, YZ = 7, 0        # front yard to the street, then the house
    b = B("house_201_nw_10th", "201 NW 10th St", "201 NW 10th St, Gainesville, FL 32601",
          "Rebuilt from the listing photos on the real footprint; room sizes inferred. 4 bed / 3 bath.",
          (428 - YX, -152), (V + YX + 3, 16, U + 4))
    def X(v): return YX + v
    def Z(u): return YZ + u
    def fl(u0, v0, u1, v1, y0, y1, blk): b.fill(X(min(v0, v1)), y0, Z(min(u0, u1)), X(max(v0, v1)), y1, Z(max(u0, u1)), blk)
    def st(u, v, y, blk): b.set(X(v), y, Z(u), blk)
    H = 4
    STUCCO, TRIM, ROOF, GABLE = "minecraft:end_stone", "minecraft:white_concrete", "minecraft:deepslate_tiles", "minecraft:light_gray_concrete"
    HARD, TILE, DARK, WHITE, CALC = "minecraft:jungle_planks", "minecraft:smooth_quartz", "minecraft:dark_oak_planks", "minecraft:white_concrete", "minecraft:calcite"
    def door(u, v, facing, kind="minecraft:birch_door", hinge="left"):
        st(u, v, 1, f"{kind}[facing={facing},half=lower,hinge={hinge},open=false]")
        st(u, v, 2, f"{kind}[facing={facing},half=upper,hinge={hinge},open=false]")
        for y in range(3, H + 1):
            st(u, v, y, WHITE)
    def window(u, v, y0=2, y1=3):
        for y in range(y0, y1 + 1):
            st(u, v, y, "minecraft:glass_pane")
    # yard: sandy patchy lawn, brick driveway along the south side from the street to the back, sidewalk at the street
    b.fill(0, 0, 0, V + YX + 2, 0, U + 3, "minecraft:grass_block")
    for (x, z) in ((1, 2), (3, 5), (2, 9), (5, 11), (4, 3), (6, 7)):
        b.fill(x, 0, z, x + 1, 0, z + 1, "minecraft:coarse_dirt")
    b.fill(0, 0, Z(U), V + YX + 2, 0, Z(U) + 2, "minecraft:bricks")
    b.fill(0, 0, 0, 0, 0, U + 3, "minecraft:light_gray_concrete")
    # shell
    fl(0, 0, U - 1, V - 1, 0, 0, "minecraft:polished_andesite")
    fl(0, 0, U - 1, V - 1, 1, H, STUCCO)
    fl(1, 1, U - 2, V - 2, 1, H, "minecraft:air")
    fl(0, 0, U - 1, V - 1, H + 1, H + 1, WHITE)                        # ceiling
    # gable roof, ridge front-to-back (gable faces the street), slight overhang
    for i in range(0, 9):
        u0, u1 = -1 + i, U - i
        if u0 > u1:
            break
        y = H + 2 + i // 2 if False else H + 1 + (i + 1) // 2
        fl(u0, -1, u0, V, y, y, ROOF); fl(u1, -1, u1, V, y, y, ROOF)
    for i in range(0, 9):
        u0, u1 = i, U - 1 - i
        if u0 <= u1:
            y = H + 1 + (i + 1) // 2
            fl(u0 + 1, 0, u1 - 1, 0, y, y, GABLE); fl(u0 + 1, V - 1, u1 - 1, V - 1, y, y, GABLE)
    # interior walls (own cells): u = 4, 10 ; v = 3, 9, 13 ; south strip v = 5, 12
    fl(4, 3, 4, V - 2, 1, H, WHITE)
    fl(10, 3, 10, V - 2, 1, H, WHITE)
    fl(1, 3, 9, 3, 1, H, CALC)                                         # sunroom back wall (stucco)
    fl(1, 9, 9, 9, 1, H, WHITE); fl(1, 13, 9, 13, 1, H, WHITE)
    fl(11, 5, 13, 5, 1, H, STUCCO); fl(11, 9, 13, 9, 1, H, WHITE); fl(11, 12, 13, 12, 1, H, WHITE)
    # carport: front-south corner, open to the street, brick floor, green side door into the sunroom
    fl(11, 0, 13, 4, 1, H, "minecraft:air"); fl(11, 0, 13, 4, 0, 0, "minecraft:bricks")
    fl(10, 0, 10, 2, 1, H, STUCCO)
    door(10, 1, "north", "minecraft:warped_door")
    st(10, 2, 2, "minecraft:glass_pane")
    st(14, 0, 1, STUCCO); fl(14, 0, 14, 0, 1, H, STUCCO)               # corner pillar
    # rooms: floors
    fl(1, 1, 9, 2, 0, 0, DARK)                                         # sunroom: dark wood-look tile
    fl(1, 1, 9, 2, 1, H, "minecraft:air")
    fl(1, 4, 3, 8, 0, 0, HARD)                                         # bedroom 1 (front, via French doors)
    fl(5, 4, 9, 8, 0, 0, HARD)                                         # living room
    fl(5, 10, 9, 12, 0, 0, TILE)                                       # kitchen
    fl(1, 10, 3, 12, 0, 0, HARD); fl(1, 14, 3, 14, 0, 0, TILE)         # bedroom 2 + its bath (tub)
    fl(5, 14, 7, 14, 0, 0, TILE); fl(8, 14, 9, 14, 0, 0, TILE)         # hall bath (shower) + back hall
    fl(11, 6, 13, 8, 0, 0, HARD)                                       # bedroom 3
    fl(11, 10, 13, 11, 0, 0, TILE)                                     # bath 3
    fl(11, 13, 13, 14, 0, 0, HARD)                                     # bedroom 4
    # front facade: window, front door, window (left half); door opens into the sunroom
    door(5, 0, "west", "minecraft:warped_door")
    for u in (4, 6):
        st(u, 0, 1, "minecraft:lime_concrete_powder"); st(u, 0, 2, "minecraft:lime_concrete_powder")
    for u in (2, 8):
        window(u, 0); st(u, 0, 1, TRIM)
    st(5, -1, 0, "minecraft:moss_block"); st(4, -1, 0, "minecraft:moss_block"); st(6, -1, 0, "minecraft:moss_block")   # green steps
    # side and back windows
    for v in (6, 11, 14):
        window(0, v); window(U - 1, v)
    for u in (2, 7, 12):
        window(u, V - 1)
    # openings between rooms
    fl(6, 3, 8, 3, 1, 2, "minecraft:air"); fl(6, 3, 8, 3, 1, 1, "minecraft:polished_blackstone_slab[type=top]")   # granite pass-through
    door(4, 6, "north", hinge="left"); door(4, 7, "north", hinge="right")                                          # French doors
    door(9, 7, "south") if False else fl(7, 9, 7, 9, 1, 2, "minecraft:air")                                         # living -> kitchen
    door(4, 11, "north")                                                                                             # kitchen -> bedroom 2
    door(13, 6, "east") if False else None
    door(6, 13, "east"); door(8, 13, "east")                                                                         # kitchen -> bath / back hall
    door(10, 7, "south"); door(10, 11, "south"); door(10, 14, "south")                                               # bedroom 3, bath 3, bedroom 4
    fl(1, 13, 3, 13, 1, H, WHITE); door(3, 13, "east")                                                               # bedroom 2 ensuite
    door(9, V - 1, "east")                                                                                           # back door
    # furniture
    st(1, 1, 1, "minecraft:white_concrete"); st(1, 2, 1, "minecraft:white_concrete")                                 # washer / dryer
    fl(5, 10, 9, 10, 1, 1, "minecraft:oak_planks"); fl(5, 10, 9, 10, 2, 2, "minecraft:polished_blackstone_slab[type=bottom]")  # counter
    st(5, 12, 1, "minecraft:iron_block"); st(5, 12, 2, "minecraft:iron_block")                                       # fridge
    st(9, 12, 1, "minecraft:furnace[facing=west]")                                                                   # range
    for (u, v, fac) in ((2, 7, "east"), (2, 11, "east"), (12, 7, "east"), (12, 14, "west")):
        dx = 1 if fac == "east" else -1
        b.set(X(v), 1, Z(u), f"minecraft:white_bed[part=foot,facing={fac}]"); b.set(X(v) + dx, 1, Z(u), f"minecraft:white_bed[part=head,facing={fac}]")
    st(1, 4, 1, "minecraft:barrel[facing=up]"); st(3, 12, 1, "minecraft:barrel[facing=up]"); st(13, 6, 1, "minecraft:barrel[facing=up]")  # closets
    for (u, v) in ((1, 14), (6, 14), (11, 10)):
        st(u, v, 1, "minecraft:cauldron")
    st(2, 14, 1, "minecraft:quartz_slab"); st(5, 14, 1, "minecraft:quartz_slab"); st(12, 11, 1, "minecraft:quartz_slab")
    for (u, v) in ((2, 6), (7, 6), (7, 11), (2, 11), (12, 7), (12, 13), (5, 1), (6, 14), (12, 10), (2, 14)):
        st(u, v, H, "minecraft:lantern[hanging=true]")
    # a live oak in the front yard
    b.fill(2, 1, 12, 2, 6, 12, "minecraft:oak_log"); b.fill(0, 5, 10, 4, 7, 14, "minecraft:oak_leaves[persistent=true]")
    b.save()


# ---------------------------------------------------------------- Hub Gainesville (3rd Ave)
def hub():
    """Hub Gainesville, 1258 NW 3rd Ave (NE corner of NW 13th St & NW 3rd Ave), from the user's photos on the footprint the 2023
    aerial photo shows (x 25..129, z -265..-209). 8 floors: arched ground-floor storefronts, a dark-gray metal corner tower at
    13th & 3rd, and these facade segments.
      South (NW 3rd Ave), west -> east: orange brick with a dark metal top, brown brick with pilasters and the "hub" entrance,
      slate-blue metal, white over red brick, white over gray.
      West (NW 13th St), south -> north: orange brick with a dark metal top, then white over red brick.
    Black steel balconies in vertical stacks, each with a door from the unit behind it. On the roof: two courtyards, the pool deck
    with pool, hot tub, turf, umbrellas and loungers, and the clubhouse with its orange-and-teal mural.
    """
    W, D = 105, 57                 # u: west -> east (x), v: north -> south (z)
    GF, FH, FLOORS = 5, 3, 8       # ground floor 5 tall, upper floors 3 tall
    TOP = GF + FH * (FLOORS - 1)   # roof slab Y (26)
    b = B("hub_gainesville", "Hub Gainesville (3rd Ave)", "1258 NW 3rd Ave, Gainesville, FL 32601",
          "From photos: 8 floors, segmented brick/metal facades, balconies, rooftop pool deck and courtyards on the real footprint.",
          (24, -266), (W + 2, TOP + 8, D + 4))
    ox, oz = 1, 1                  # building starts 1 block in (room for the corner tower and balconies to stick out)
    def fl(u0, y0, v0, u1, y1, v1, blk): b.fill(ox + min(u0, u1), y0, oz + min(v0, v1), ox + max(u0, u1), y1, oz + max(v0, v1), blk)
    def st(u, y, v, blk): b.set(ox + u, y, oz + v, blk)
    def floor_y(f): return 0 if f == 1 else GF + FH * (f - 2)          # slab under floor f
    COURTS = [(22, 17, 35, 38), (55, 18, 66, 39)]
    ORANGE, DARK, BROWN, PIL, SLATE, WHITE, RED, GRAY = ("minecraft:terracotta", "minecraft:gray_concrete", "minecraft:brown_terracotta",
        "minecraft:mud_bricks", "minecraft:light_blue_terracotta", "minecraft:white_concrete", "minecraft:bricks", "minecraft:light_gray_concrete")
    # ---- massing: clear, slabs, roof
    fl(0, 1, 0, W - 1, TOP + 6, D - 1, "minecraft:air")
    fl(0, 0, 0, W - 1, 0, D - 1, "minecraft:polished_andesite")
    for f in range(2, FLOORS + 1):
        fl(0, floor_y(f), 0, W - 1, floor_y(f), D - 1, "minecraft:smooth_stone")
    fl(0, TOP, 0, W - 1, TOP, D - 1, "minecraft:white_concrete")
    for (u0, v0, u1, v1) in COURTS:                      # courtyards open from the podium (floor 2) up
        fl(u0, GF + 1, v0, u1, TOP, v1, "minecraft:air")
        fl(u0, GF, v0, u1, GF, v1, "minecraft:moss_block")
        for (uu, vv) in ((u0 + 3, v0 + 4), (u1 - 3, v1 - 4)):
            fl(uu, GF + 1, vv, uu, GF + 3, vv, "minecraft:oak_log"); fl(uu - 1, GF + 4, vv - 1, uu + 1, GF + 5, vv + 1, "minecraft:oak_leaves[persistent=true]")
    # ---- facade material by position (u along south/north, v along west/east) and floor
    def south_mat(u, f):
        if u <= 20:  return DARK if f >= 7 else ORANGE
        if u <= 38:  return PIL if u % 4 == 0 else BROWN
        if u <= 54:  return SLATE
        if u <= 74:  return WHITE if f >= 5 else RED
        return WHITE if f >= 3 else GRAY
    def west_mat(v, f):
        if v <= 20:  return WHITE if f >= 3 else RED
        return DARK if f >= 7 else ORANGE
    def wall_run(side, n, mat_fn, balconies):
        """side: 'S','N','W','E'; n: length; draws every floor of one facade with windows and balcony doors."""
        def put(i, y, blk):
            if side == "S": st(i, y, D - 1, blk)
            elif side == "N": st(i, y, 0, blk)
            elif side == "W": st(0, y, i, blk)
            else: st(W - 1, y, i, blk)
        for i in range(n):
            # ground floor: arched storefronts (3 glass, 1 pier), arch course at the top
            for y in range(1, GF + 1):
                pier = i % 4 == 0
                if y <= 3 and not pier:
                    put(i, y, "minecraft:glass_pane")
                elif y == 4 and not pier and i % 4 == 2:
                    put(i, y, "minecraft:glass_pane")
                else:
                    put(i, y, mat_fn(i, 1))
            for f in range(2, FLOORS + 1):
                y0 = floor_y(f)
                for dy in range(0, FH + (1 if f == FLOORS else 0)):
                    y = y0 + dy
                    win = dy in (1, 2) and i % 3 != 0
                    put(i, y, "minecraft:glass_pane" if win else mat_fn(i, f))
        for (i0, f0) in balconies:                       # balcony stacks: door in the wall, steel deck and railing outside
            for f in range(f0, FLOORS + 1):
                y0 = floor_y(f)
                for i in range(i0, i0 + 3):
                    if i == i0 + 1:
                        put(i, y0 + 1, "minecraft:spruce_door[facing=%s,half=lower,hinge=left,open=false]" % {"S": "south", "N": "north", "W": "west", "E": "east"}[side])
                        put(i, y0 + 2, "minecraft:spruce_door[facing=%s,half=upper,hinge=left,open=false]" % {"S": "south", "N": "north", "W": "west", "E": "east"}[side])
                    if side == "S":
                        st(i, y0, D, "minecraft:polished_blackstone_slab[type=top]"); st(i, y0 + 1, D, "minecraft:iron_bars")
                    elif side == "W":
                        b.set(ox - 1, y0, oz + i, "minecraft:polished_blackstone_slab[type=top]"); b.set(ox - 1, y0 + 1, oz + i, "minecraft:iron_bars")
    wall_run("S", W, south_mat, [(45, 3), (62, 5), (8, 6)])
    wall_run("W", D, west_mat, [(8, 3), (30, 6), (42, 6)])
    wall_run("N", W, lambda i, f: WHITE if f >= 3 else GRAY, [])
    wall_run("E", D, lambda i, f: WHITE if f >= 3 else GRAY, [])
    # corner tower at 13th & 3rd: dark metal bay from floor 2 to above the roof, canted top
    for f_y in range(GF, TOP + 4):
        for k in range(0, 5):
            b.set(ox + k, f_y, oz + D, DARK); b.set(ox - 1, f_y, oz + D - 1 - k, DARK)
        for k in (1, 3):
            if (f_y - GF) % FH in (1, 2) and f_y < TOP:
                b.set(ox + k, f_y, oz + D, "minecraft:glass_pane"); b.set(ox - 1, f_y, oz + D - 1 - k, "minecraft:glass_pane")
    st(2, TOP + 4, D - 2, "minecraft:lime_concrete"); st(2, TOP + 3, D - 2, "minecraft:white_concrete")   # green "hub" logo box
    # "hub" entrance on 3rd Ave with a steel canopy
    fl(28, 1, D - 1, 33, 3, D - 1, "minecraft:air")
    b.set(ox + 30, 1, oz + D - 1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false]")
    b.set(ox + 30, 2, oz + D - 1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]")
    b.set(ox + 31, 1, oz + D - 1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=right,open=false]")
    b.set(ox + 31, 2, oz + D - 1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=right,open=false]")
    b.fill(ox + 26, 4, oz + D, ox + 35, 4, oz + D + 1, "minecraft:black_concrete")
    b.fill(ox + 31, 5, oz + D, ox + 33, 5, oz + D, "minecraft:lime_concrete"); b.set(ox + 34, 5, oz + D, "minecraft:white_concrete")
    # black canopies over storefront doors on 13th St and 3rd Ave
    for i in range(6, W - 6, 12):
        b.fill(ox + i, 4, oz + D, ox + i + 2, 4, oz + D, "minecraft:black_concrete")
    for i in range(6, D - 6, 12):
        b.fill(ox - 1, 4, oz + i, ox - 1, 4, oz + i + 2, "minecraft:black_concrete")
    # ---- ground floor: lobby + leasing behind the entrance, retail along 13th and 3rd, garage at the east end
    fl(24, 1, D - 12, 38, 1, D - 12, "minecraft:smooth_quartz")                  # leasing desk
    fl(23, 1, D - 16, 23, GF - 1, D - 2, "minecraft:white_concrete"); fl(39, 1, D - 16, 39, GF - 1, D - 2, "minecraft:white_concrete")
    fl(80, 1, 1, W - 2, GF - 1, D - 2, "minecraft:air"); fl(80, 0, 1, W - 2, 0, D - 2, "minecraft:gray_concrete")
    for u in range(82, W - 2, 3):
        b.fill(ox + u, 0, oz + 3, ox + u, 0, oz + 8, "minecraft:white_concrete")   # parking stripes
    # ---- residential floors: corridors around the courtyards, units with doors, beds, kitchens; elevators and stairs
    CORR = [(1, 8, W - 2, 9), (1, D - 10, W - 2, D - 9), (12, 8, 13, D - 9), (44, 8, 45, D - 9), (90, 8, 91, D - 9)]
    for f in range(2, FLOORS + 1):
        y0 = floor_y(f)
        for (u0, v0, u1, v1) in CORR:
            fl(u0, y0 + 1, v0 - 1, u1, y0 + FH - 1, v0 - 1, "minecraft:white_concrete") if v0 == v1 - 1 and u1 - u0 > 4 else None
            fl(u0, y0 + 1, v1 + 1, u1, y0 + FH - 1, v1 + 1, "minecraft:white_concrete") if v0 == v1 - 1 and u1 - u0 > 4 else None
        for (u0, v0, u1, v1) in CORR:                     # carve corridors after walls (crossings stay open)
            fl(u0, y0 + 1, v0, u1, y0 + FH - 1, v1, "minecraft:air")
            for uu in range(u0 + 3, u1, 9):
                st(uu, y0 + FH - 1 + (1 if f == FLOORS else 0), (v0 + v1) // 2, "minecraft:sea_lantern") if f != FLOORS else None
        for u in range(3, W - 2, 7):                     # unit partitions + doors off the north and south corridors
            for (va, vb, dv) in ((1, 6, 7), (D - 8, D - 2, D - 8)):
                if any(c[0] <= u <= c[2] for c in CORR[2:]):
                    continue
                fl(u, y0 + 1, va, u, y0 + FH - 1, vb, "minecraft:white_concrete")
                dz = dv if dv == 7 else D - 8
                st(u + 3, y0 + 1, dz, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]")
                st(u + 3, y0 + 2, dz, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]")
                bv = 2 if dv == 7 else D - 3
                st(u + 1, y0 + 1, bv, "minecraft:light_blue_bed[part=foot,facing=east]"); st(u + 2, y0 + 1, bv, "minecraft:light_blue_bed[part=head,facing=east]")
                st(u + 5, y0 + 1, bv, "minecraft:smooth_quartz")
        for (eu, ev) in ((44, 30), (12, 30), (90, 30)):
            st(eu, y0, ev, "gnvcraft:elevator")
        fl(91, y0, D - 12, 91, y0 + FH, D - 12, "minecraft:scaffolding[distance=0,bottom=false]")
    for (eu, ev) in ((44, 30), (12, 30), (90, 30)):
        st(eu, 0, ev, "gnvcraft:elevator"); st(eu, TOP, ev, "gnvcraft:elevator")
    fl(91, 1, D - 12, 91, TOP, D - 12, "minecraft:scaffolding[distance=0,bottom=false]")
    # ---- roof: membrane, HVAC, parapet, pool deck, clubhouse with mural
    fl(0, TOP + 1, 0, W - 1, TOP + 1, 0, "minecraft:white_concrete"); fl(0, TOP + 1, D - 1, W - 1, TOP + 1, D - 1, "minecraft:white_concrete")
    fl(0, TOP + 1, 0, 0, TOP + 1, D - 1, "minecraft:white_concrete"); fl(W - 1, TOP + 1, 0, W - 1, TOP + 1, D - 1, "minecraft:white_concrete")
    for (u, v) in ((8, 4), (16, 4), (70, 4), (78, 4), (98, 12), (98, 24), (8, 28), (70, 28)):
        fl(u, TOP + 1, v, u + 2, TOP + 2, v + 1, "minecraft:iron_block")
    fl(30, TOP, 42, 82, TOP, D - 2, "minecraft:smooth_sandstone")                # deck
    fl(65, TOP - 1, 50, 73, TOP, 54, "minecraft:water"); fl(64, TOP - 2, 49, 74, TOP - 2, 55, "minecraft:light_blue_concrete")
    fl(60, TOP, 51, 62, TOP, 53, "minecraft:water")                              # hot tub
    fl(30, TOP, 46, 44, TOP, D - 2, "minecraft:moss_block")                      # turf with umbrella tables
    for (u, v) in ((33, 48), (38, 51), (42, 47), (35, 53)):
        fl(u, TOP + 1, v, u, TOP + 2, v, "minecraft:birch_fence"); fl(u - 1, TOP + 3, v - 1, u + 1, TOP + 3, v + 1, "minecraft:white_carpet")
        st(u, TOP + 3, v, "minecraft:blue_wool")
    for u in range(66, 74, 2):
        st(u, TOP + 1, 48, "minecraft:quartz_slab")                              # loungers along the pool
    for u in range(76, 81):
        st(u, TOP + 1, 52, "minecraft:light_blue_carpet")
    fl(46, TOP + 1, 40, 62, TOP + 5, 45, "minecraft:white_concrete")             # clubhouse
    fl(47, TOP + 1, 45, 61, TOP + 3, 45, "minecraft:glass")
    mural = [(48, 4, "minecraft:orange_concrete"), (49, 4, "minecraft:orange_concrete"), (52, 3, "minecraft:cyan_terracotta"),
             (53, 4, "minecraft:lime_concrete"), (54, 3, "minecraft:cyan_terracotta"), (57, 4, "minecraft:orange_concrete"),
             (59, 3, "minecraft:lime_concrete"), (60, 4, "minecraft:cyan_terracotta"), (61, 5, "minecraft:orange_concrete")]
    for (u, dy, blk) in mural:
        st(u, TOP + dy, 40, blk); st(u, TOP + dy, 45, blk) if dy >= 4 else None
    b.save()


# ---------------------------------------------------------------- 13th St & University Ave, NW corner (overlay)
def corner_13th_university():
    """The NW corner plaza in front of The Standard / Chick-fil-A (from the corner photo): brick-banded pavers, two palms,
    round brick bollards with concrete caps along the curb, and the Chick-fil-A storefront sign. Stamped over the generated city."""
    b = B("corner_13th_university", "13th St & University Ave (NW corner)", "NW 13th St & W University Ave",
          "Overlay from the corner photo: pavers, palms, brick bollards, Chick-fil-A sign.", (-26, -15), (26, 14, 13))
    b.meta["overlay"] = True
    for x in range(0, 24):
        for z in range(2, 11):
            b.set(x, 0, z, "minecraft:bricks" if (x + z) % 6 == 0 or z == 2 else "minecraft:smooth_sandstone")
    for (x, z) in ((10, 5), (19, 5)):
        b.fill(x, 1, z, x, 7, z, "minecraft:jungle_log")
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2), (3, 0), (-3, 0), (0, 3), (0, -3)):
            b.set(x + dx, 8 if abs(dx) + abs(dz) < 3 else 7, z + dz, "minecraft:jungle_leaves[persistent=true]")
        b.set(x, 8, z, "minecraft:jungle_leaves[persistent=true]")
    for (x, z) in ((23, 10), (21, 10), (23, 8), (17, 10), (13, 10)):
        b.set(x, 1, z, "minecraft:bricks"); b.set(x, 2, z, "minecraft:smooth_stone_slab[type=bottom]")
    b.fill(18, 3, 1, 23, 3, 1, "minecraft:red_concrete"); b.fill(19, 4, 1, 22, 4, 1, "minecraft:white_concrete")   # Chick-fil-A sign
    b.save()


if __name__ == "__main__":
    # The Standard and the Hub are now built on their real LiDAR / Overture footprints by the generator (see bake_core overrides).
    for old in ("the_standard", "the_hub_3rd_ave"):
        (OUT / (old + ".json")).unlink(missing_ok=True)
    house(); hub(); corner_13th_university()
    (OUT / "index.json").write_text(json.dumps({"specs": ["house_201_nw_10th", "hub_gainesville", "corner_13th_university"]}))
