#!/usr/bin/env python3
"""Generates HeySir's textures with Pillow.

    python3 tools/gen_textures.py

Outputs (under src/main/resources/assets/gnvcraft/):
  textures/entity/heysir.png        64x64 classic player skin
  textures/entity/heysir_bike.png   bike atlas; UV regions must match BikeModel.java
  textures/item/mcdonalds_giftcard.png
  textures/item/heysir_spawn_egg.png
  icon.png

The skin uses the standard player layout, so any classic (4px arm) skin can replace heysir.png.
"""

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gnvcraft"
VANILLA_EGG = Path("/tmp/mcres/assets/minecraft/textures/item/zombie_spawn_egg.png")

rng = random.Random(1234)

SKIN = (226, 186, 150)
SKIN_SHADE = (204, 161, 126)
STUBBLE = (188, 158, 134)
HAIR = (38, 29, 23)
HAIR_LIGHT = (62, 49, 39)
SHIRT = (196, 30, 36)
SHIRT_SHADE = (158, 22, 28)
SHIRT_LIGHT = (214, 52, 56)
JEANS = (96, 124, 160)
JEANS_FADED = (134, 160, 192)
JEANS_DARK = (70, 94, 128)
FRAY = (226, 228, 232)
SHOE = (202, 202, 196)
SHOE_SCUFF = (166, 164, 158)
SOLE = (112, 110, 106)
EYE_WHITE = (240, 240, 240)
IRIS = (74, 52, 36)
MOUTH = (152, 92, 82)


def jitter(color, amount):
    return tuple(max(0, min(255, c + rng.randint(-amount, amount))) for c in color)


def fill(img, x0, y0, w, h, color, noise=0):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), jitter(color, noise) + (255,))


def box_faces(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a Minecraft model box UV layout."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_head(img):
    f = box_faces(0, 0, 8, 8, 8)

    # Buzz cut: dark, slightly speckled hair on top, sides and back.
    x, y, w, h = f["top"]
    for py in range(h):
        for px in range(w):
            img.putpixel((x + px, y + py), (HAIR_LIGHT if rng.random() < 0.25 else HAIR) + (255,))

    x, y, w, h = f["bottom"]
    fill(img, x, y, w, h, SKIN_SHADE)

    # Face.
    x, y, w, h = f["front"]
    fill(img, x, y, w, h, SKIN, 4)
    for px in range(8):
        img.putpixel((x + px, y), HAIR + (255,))
    for px in (0, 7):
        img.putpixel((x + px, y + 1), HAIR + (255,))
    for px in (1, 2, 5, 6):
        img.putpixel((x + px, y + 2), HAIR_LIGHT + (255,))
    img.putpixel((x + 1, y + 3), EYE_WHITE + (255,))
    img.putpixel((x + 2, y + 3), IRIS + (255,))
    img.putpixel((x + 5, y + 3), IRIS + (255,))
    img.putpixel((x + 6, y + 3), EYE_WHITE + (255,))
    img.putpixel((x + 3, y + 4), SKIN_SHADE + (255,))
    img.putpixel((x + 4, y + 4), SKIN_SHADE + (255,))
    for px in range(2, 6):
        img.putpixel((x + px, y + 6), MOUTH + (255,))
    for px in range(1, 7):
        img.putpixel((x + px, y + 7), jitter(STUBBLE, 6) + (255,))
    for py in (5, 6):
        img.putpixel((x, y + py), jitter(STUBBLE, 6) + (255,))
        img.putpixel((x + 7, y + py), jitter(STUBBLE, 6) + (255,))

    # Sides: short hair above the ear. The face is at the high-x edge of "right" and low-x edge of "left".
    for face, front_edge_low in (("right", False), ("left", True)):
        x, y, w, h = f[face]
        fill(img, x, y, w, h, SKIN, 4)
        for py in range(8):
            for px in range(8):
                from_front = px if front_edge_low else 7 - px
                if py < 3 or (py == 3 and from_front > 2):
                    img.putpixel((x + px, y + py), (HAIR_LIGHT if rng.random() < 0.25 else HAIR) + (255,))
        for py in (3, 4, 5):
            ear_x = 3 if front_edge_low else 4
            img.putpixel((x + ear_x, y + py), SKIN_SHADE + (255,))
        for py in (6, 7):
            edge_x = 0 if front_edge_low else 7
            img.putpixel((x + edge_x, y + py), jitter(STUBBLE, 6) + (255,))

    # Back: hair down to the neck.
    x, y, w, h = f["back"]
    fill(img, x, y, w, h, SKIN, 4)
    for py in range(6):
        for px in range(8):
            if py < 5 or rng.random() < 0.5:
                img.putpixel((x + px, y + py), (HAIR_LIGHT if rng.random() < 0.25 else HAIR) + (255,))


def paint_body(img):
    f = box_faces(16, 16, 8, 12, 4)
    for face in f.values():
        fill(img, *face, SHIRT, 6)
    x, y, w, h = f["front"]
    for px in range(2, 6):
        img.putpixel((x + px, y), SHIRT_SHADE + (255,))
    img.putpixel((x + 3, y), SKIN + (255,))
    img.putpixel((x + 4, y), SKIN + (255,))
    for px, py in ((2, 5), (3, 6), (5, 8), (4, 9), (6, 4)):
        img.putpixel((x + px, y + py), SHIRT_SHADE + (255,))
    for px, py in ((1, 2), (6, 7)):
        img.putpixel((x + px, y + py), SHIRT_LIGHT + (255,))
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = f[face]
        for px in range(fw):
            img.putpixel((fx + px, fy + fh - 1), SHIRT_SHADE + (255,))


def paint_arm(img, u, v):
    f = box_faces(u, v, 4, 12, 4)
    fill(img, *f["top"], SHIRT, 6)
    fill(img, *f["bottom"], SKIN_SHADE)
    for face in ("right", "front", "left", "back"):
        x, y, w, h = f[face]
        fill(img, x, y, w, 4, SHIRT, 6)
        fill(img, x, y + 3, w, 1, SHIRT_SHADE)
        fill(img, x, y + 4, w, 8, SKIN, 4)
        fill(img, x, y + 10, w, 2, SKIN_SHADE, 3)


def paint_leg(img, u, v, ripped):
    f = box_faces(u, v, 4, 12, 4)
    fill(img, *f["top"], JEANS, 6)
    fill(img, *f["bottom"], SOLE)
    for face in ("right", "front", "left", "back"):
        x, y, w, h = f[face]
        fill(img, x, y, w, 10, JEANS, 8)
        if face == "front":
            # Faded thighs, worn lightest in the middle.
            for py in range(1, 6):
                for px in (1, 2):
                    img.putpixel((x + px, y + py), jitter(JEANS_FADED, 6) + (255,))
            for py in (2, 3, 4):
                for px in (0, 3):
                    img.putpixel((x + px, y + py), jitter(JEANS_FADED, 10) + (255,))
        if face in ("right", "left"):
            for py in range(10):
                img.putpixel((x + (0 if face == "right" else w - 1), y + py), JEANS_DARK + (255,))
        # Frayed hem.
        for px in range(w):
            if rng.random() < 0.5:
                img.putpixel((x + px, y + 9), JEANS_FADED + (255,))
        fill(img, x, y + 10, w, 1, SHOE, 6)
        fill(img, x, y + 11, w, 1, SOLE, 4)
        if rng.random() < 0.8:
            img.putpixel((x + rng.randrange(w), y + 10), SHOE_SCUFF + (255,))
    if ripped:
        x, y, w, h = f["front"]
        img.putpixel((x + 1, y + 6), FRAY + (255,))
        img.putpixel((x + 2, y + 6), FRAY + (255,))
        img.putpixel((x + 1, y + 7), SKIN + (255,))
        img.putpixel((x + 2, y + 7), SKIN_SHADE + (255,))
        img.putpixel((x + 3, y + 7), FRAY + (255,))


def make_skin():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    paint_head(img)
    paint_body(img)
    paint_arm(img, 40, 16)
    paint_arm(img, 32, 48)
    paint_leg(img, 0, 16, ripped=True)
    paint_leg(img, 16, 48, ripped=False)
    return img


# Bike atlas: BikeModel uses a 64x64 UV space; the image is 4x that for detail.
BIKE_SCALE = 4
FRAME_RED = (208, 26, 26)
FRAME_HIGHLIGHT = (236, 70, 64)
SILVER = (188, 190, 196)
SILVER_LIGHT = (222, 224, 230)
TIRE = (28, 28, 30)
TREAD = (46, 46, 50)


def draw_wheel(img, ox, oy, size):
    draw = ImageDraw.Draw(img)
    c = (ox + size / 2 - 0.5, oy + size / 2 - 0.5)
    r = size / 2
    draw.ellipse([ox, oy, ox + size - 1, oy + size - 1], fill=TIRE + (255,))
    for i in range(36):
        a = i / 36 * math.tau
        draw.point((c[0] + math.cos(a) * (r - 1.5), c[1] + math.sin(a) * (r - 1.5)), fill=TREAD + (255,))
    rim = r - 3.5
    draw.ellipse([c[0] - rim, c[1] - rim, c[0] + rim, c[1] + rim], fill=SILVER + (255,))
    inner = rim - 1.5
    draw.ellipse([c[0] - inner, c[1] - inner, c[0] + inner, c[1] + inner], fill=(0, 0, 0, 0))
    for i in range(16):
        a = i / 16 * math.tau
        draw.line(
            [c[0] + math.cos(a) * 2.5, c[1] + math.sin(a) * 2.5, c[0] + math.cos(a) * inner, c[1] + math.sin(a) * inner],
            fill=SILVER_LIGHT + (255,),
            width=1,
        )
    draw.ellipse([c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3], fill=SILVER + (255,))
    draw.ellipse([c[0] - 1.2, c[1] - 1.2, c[0] + 1.2, c[1] + 1.2], fill=(90, 90, 96, 255))


def draw_chainring(img, ox, oy, size):
    draw = ImageDraw.Draw(img)
    c = (ox + size / 2 - 0.5, oy + size / 2 - 0.5)
    r = size / 2
    for i in range(24):
        a = i / 24 * math.tau
        draw.ellipse([c[0] + math.cos(a) * (r - 1.2) - 1, c[1] + math.sin(a) * (r - 1.2) - 1,
                      c[0] + math.cos(a) * (r - 1.2) + 1, c[1] + math.sin(a) * (r - 1.2) + 1], fill=SILVER + (255,))
    draw.ellipse([c[0] - (r - 1.5), c[1] - (r - 1.5), c[0] + (r - 1.5), c[1] + (r - 1.5)], fill=SILVER + (255,))
    draw.ellipse([c[0] - (r - 4), c[1] - (r - 4), c[0] + (r - 4), c[1] + (r - 4)], fill=(0, 0, 0, 0))
    for i in range(5):
        a = i / 5 * math.tau
        draw.line([c[0], c[1], c[0] + math.cos(a) * (r - 3), c[1] + math.sin(a) * (r - 3)], fill=SILVER_LIGHT + (255,), width=2)
    draw.ellipse([c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2], fill=(90, 90, 96, 255))


def make_bike():
    s = BIKE_SCALE
    img = Image.new("RGBA", (64 * s, 64 * s), (0, 0, 0, 0))
    fill(img, 0 * s, 0 * s, 16 * s, 24 * s, FRAME_RED, 6)
    for y in range(0, 24 * s, 3):
        for x in range(0, 16 * s, 4):
            if rng.random() < 0.15:
                img.putpixel((x, y), FRAME_HIGHLIGHT + (255,))
    fill(img, 16 * s, 0 * s, 24 * s, 16 * s, SILVER, 8)
    for y in range(0, 16 * s, 4):
        fill(img, 16 * s, y, 24 * s, 1, SILVER_LIGHT, 4)
    fill(img, 40 * s, 0 * s, 16 * s, 8 * s, (26, 26, 28), 4)
    # Wheel panel sides: box(w=0, h=12, d=12) at texOffs(0, 24).
    draw_wheel(img, 0 * s, 36 * s, 12 * s)
    draw_wheel(img, 12 * s, 36 * s, 12 * s)
    # Chainring sides: box(w=0, h=5, d=5) at texOffs(24, 24).
    draw_chainring(img, 24 * s, 29 * s, 5 * s)
    draw_chainring(img, 29 * s, 29 * s, 5 * s)
    return img


GIFTCARD = [
    "................",
    "................",
    "................",
    ".RRRRRRRRRRRRRR.",
    "RRRRRRRRRRRRRRRR",
    "RRRYYRRRRRRYYRRR",
    "RRYRRYRRRRYRRYRR",
    "RRYRRRYRRYRRRYRR",
    "RRYRRRRYYRRRRYRR",
    "RRYRRRRYYRRRRYRR",
    "RRYRRRRYYRRRRYRR",
    "RRRRRRRRRRRRRRRR",
    "RWWWWWRRRRRRRRRD",
    ".DDDDDDDDDDDDDD.",
    "................",
    "................",
]
GIFTCARD_COLORS = {
    "R": (218, 41, 28),
    "D": (160, 24, 16),
    "Y": (255, 199, 44),
    "W": (250, 246, 236),
}


def make_giftcard():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GIFTCARD):
        for x, ch in enumerate(row):
            if ch in GIFTCARD_COLORS:
                img.putpixel((x, y), GIFTCARD_COLORS[ch] + (255,))
    return img


def make_spawn_egg():
    """Recolors the vanilla zombie egg: tan base (skin) with red spots (shirt)."""
    if VANILLA_EGG.exists():
        base = Image.open(VANILLA_EGG).convert("RGBA")
        img = Image.new("RGBA", base.size, (0, 0, 0, 0))
        for y in range(base.height):
            for x in range(base.width):
                r, g, b, a = base.getpixel((x, y))
                if a == 0:
                    continue
                lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
                spot = r > b
                tint = SHIRT if spot else SKIN
                shade = 0.55 + lum * 0.8
                img.putpixel((x, y), tuple(min(255, int(c * shade)) for c in tint) + (a,))
        return img
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([3, 1, 12, 14], fill=SKIN + (255,), outline=SKIN_SHADE + (255,))
    return img


def make_icon(skin):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([2, 2, 125, 125], fill=SHIRT + (255,))
    face = skin.crop((8, 8, 16, 16)).resize((80, 80), Image.NEAREST)
    img.alpha_composite(face, (24, 24))
    return img


def main():
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    skin = make_skin()
    skin.save(ASSETS / "textures/entity/heysir.png")
    make_bike().save(ASSETS / "textures/entity/heysir_bike.png")
    make_giftcard().save(ASSETS / "textures/item/mcdonalds_giftcard.png")
    make_spawn_egg().save(ASSETS / "textures/item/heysir_spawn_egg.png")
    make_icon(skin).save(ASSETS / "icon.png")
    print("Textures written to", ASSETS)


if __name__ == "__main__":
    main()
