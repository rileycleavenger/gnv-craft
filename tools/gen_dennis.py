#!/usr/bin/env python3
"""Dennis: bald, very short pink shorts, cutoff green shirt. Writes assets/gnvcraft/textures/entity/dennis.png (64x64 classic skin)."""
import random
from pathlib import Path
from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/gnvcraft"
SKIN = (238, 200, 172); SHADE = (214, 172, 144)
GREEN = (46, 140, 62); GREEN_D = (34, 108, 48)
PINK = (255, 105, 180); PINK_D = (224, 78, 150)
SHOE = (240, 240, 240); SOLE = (120, 120, 120)
rnd = random.Random(7)

def fill(img, x, y, w, h, c, n=6):
    for i in range(x, x + w):
        for j in range(y, y + h):
            k = rnd.randint(-n, n)
            img.putpixel((i, j), (max(0, min(255, c[0] + k)), max(0, min(255, c[1] + k)), max(0, min(255, c[2] + k)), 255))

def box(img, u, v, w, h, d, painter):
    # side faces: right(d) front(w) left(d) back(w), each h tall, starting at (u, v+d)
    xs = [(u, d), (u + d, w), (u + d + w, d), (u + d + w + d, w)]
    for x, fw in xs:
        painter(x, v + d, fw, h)

def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # head: all skin (bald), simple face on the front
    fill(img, 8, 0, 8, 8, SKIN); fill(img, 16, 0, 8, 8, SHADE)
    for x in (0, 8, 16, 24):
        fill(img, x, 8, 8, 8, SKIN)
    img.putpixel((10, 12), (40, 40, 40, 255)); img.putpixel((13, 12), (40, 40, 40, 255))   # eyes
    for x in range(10, 14):
        img.putpixel((x, 14), (170, 100, 90, 255))                                          # mouth
    # torso (16,16): skin underneath, green cutoff top 8px (midriff shows the bottom 4)
    box(img, 16, 16, 8, 12, 4, lambda x, y, w, h: (fill(img, x, y, w, h, SKIN), fill(img, x, y, w, 8, GREEN), fill(img, x, y + 7, w, 1, GREEN_D)))
    fill(img, 20, 16, 8, 4, SKIN); fill(img, 28, 16, 8, 4, SHADE)
    # arms (right 40,16 / left 32,48): cutoff sleeves = 2px green on the shoulder, rest skin
    for (u, v) in ((40, 16), (32, 48)):
        box(img, u, v, 4, 12, 4, lambda x, y, w, h: (fill(img, x, y, w, h, SKIN), fill(img, x, y, w, 2, GREEN)))
        fill(img, u + 4, v, 4, 4, GREEN); fill(img, u + 8, v, 4, 4, SHADE)
    # legs (right 0,16 / left 16,48): skin with 3px very short pink shorts, white sneakers
    for (u, v) in ((0, 16), (16, 48)):
        box(img, u, v, 4, 12, 4, lambda x, y, w, h: (fill(img, x, y, w, h, SKIN), fill(img, x, y, w, 3, PINK), fill(img, x, y + 2, w, 1, PINK_D), fill(img, x, y + h - 3, w, 3, SHOE)))
        fill(img, u + 4, v, 4, 4, PINK); fill(img, u + 8, v, 4, 4, SOLE)
    out = ASSETS / "textures/entity/dennis.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print("wrote", out)

main()

# --- drink item icons -------------------------------------------------------------
def drinks():
    from PIL import ImageDraw
    out = ASSETS / "textures/item"
    out.mkdir(parents=True, exist_ok=True)
    def glass(name, body, foam=None, shape="mug"):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        if shape == "mug":
            d.rectangle([3, 4, 10, 14], fill=(220, 230, 240, 255)); d.rectangle([4, 6, 9, 13], fill=body + (255,))
            d.rectangle([11, 6, 13, 11], outline=(220, 230, 240, 255)); 
            if foam: d.rectangle([3, 3, 10, 5], fill=foam + (255,))
        elif shape == "shot":
            d.polygon([(5, 6), (10, 6), (9, 14), (6, 14)], fill=(220, 230, 240, 255)); d.polygon([(6, 8), (9, 8), (9, 13), (6, 13)], fill=body + (255,))
        else:
            d.polygon([(3, 4), (12, 4), (8, 9), (8, 13), (11, 14), (5, 14), (8, 13), (8, 9)], fill=(220, 230, 240, 255)); d.polygon([(4, 5), (11, 5), (8, 8)], fill=body + (255,))
            d.rectangle([11, 2, 12, 5], fill=(255, 80, 60, 255))
        im.save(out / (name + ".png"))
    glass("beer", (232, 168, 36), (250, 244, 220), "mug")
    glass("shot", (150, 90, 40), None, "shot")
    glass("cocktail", (240, 90, 120), None, "cocktail")
drinks()

# --- Gator fan skins: 6 orange/blue outfit variants --------------------------------
def fans():
    ORANGE = (250, 70, 22); BLUE = (0, 33, 165); WHITE = (245, 245, 245)
    skins = [(190, 140, 110), (238, 200, 172), (120, 80, 56), (222, 170, 130), (90, 58, 40), (246, 214, 190)]
    hairs = [(40, 30, 24), (120, 80, 40), (20, 20, 20), (210, 170, 90), (90, 40, 20), (60, 60, 64)]
    combos = [(ORANGE, BLUE), (BLUE, ORANGE), (ORANGE, WHITE), (BLUE, WHITE), (ORANGE, ORANGE), (BLUE, BLUE)]
    for i in range(6):
        sk, hr = skins[i], hairs[i]; top, bottom = combos[i]
        im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for x in (0, 8, 16, 24): fill(im, x, 8, 8, 8, sk, 4)
        fill(im, 8, 0, 8, 8, hr, 5); fill(im, 16, 0, 8, 8, sk, 4)
        fill(im, 0, 8, 8, 3, hr, 5); fill(im, 8, 8, 8, 2, hr, 5); fill(im, 16, 8, 8, 3, hr, 5); fill(im, 24, 8, 8, 8, hr, 5)
        im.putpixel((10, 12), (30, 30, 30, 255)); im.putpixel((13, 12), (30, 30, 30, 255))
        box(im, 16, 16, 8, 12, 4, lambda x, y, w, h: fill(im, x, y, w, h, top, 5)); fill(im, 20, 16, 8, 4, top); fill(im, 28, 16, 8, 4, top)
        for (u, v) in ((40, 16), (32, 48)):
            box(im, u, v, 4, 12, 4, lambda x, y, w, h: (fill(im, x, y, w, h, sk, 4), fill(im, x, y, w, 4, top, 5)))
            fill(im, u + 4, v, 4, 4, top); fill(im, u + 8, v, 4, 4, sk)
        for (u, v) in ((0, 16), (16, 48)):
            box(im, u, v, 4, 12, 4, lambda x, y, w, h: (fill(im, x, y, w, h, bottom, 5), fill(im, x, y + h - 2, w, 2, WHITE)))
            fill(im, u + 4, v, 4, 4, bottom); fill(im, u + 8, v, 4, 4, bottom)
        p = ASSETS / ("textures/entity/fan_%d.png" % (i + 1)); im.save(p)
fans()

def scooter():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    fill(im, 0, 0, 32, 16, (120, 220, 20), 6)      # lime deck
    fill(im, 0, 16, 14, 10, (40, 40, 44), 4)       # wheels
    fill(im, 0, 26, 8, 6, (200, 200, 205), 4)      # stem
    fill(im, 8, 26, 14, 6, (30, 30, 30), 4)        # handlebar
    im.save(ASSETS / "textures/entity/lime_scooter.png")
scooter()

def plane():
    im = Image.new("RGBA", (128, 96), (0, 0, 0, 0))
    fill(im, 0, 0, 128, 40, (240, 240, 244), 4)        # fuselage, white
    fill(im, 0, 40, 128, 14, (245, 245, 248), 3)       # wing
    fill(im, 0, 54, 128, 16, (0, 33, 165), 4)          # gator blue tail pieces
    fill(im, 0, 70, 30, 12, (250, 70, 22), 4)          # orange nose
    fill(im, 30, 70, 20, 12, (30, 30, 34), 3)          # prop
    fill(im, 0, 82, 20, 14, (40, 40, 44), 3)           # landing gear
    im.save(ASSETS / "textures/entity/plane.png")
plane()

def blocks():
    out = ASSETS / "textures/block"; out.mkdir(parents=True, exist_ok=True)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255)); fill(im, 0, 0, 16, 16, (170, 172, 180), 5)
    fill(im, 2, 2, 12, 12, (110, 112, 120), 4); fill(im, 7, 3, 2, 10, (30, 30, 34), 3)   # sliding doors
    fill(im, 5, 1, 6, 1, (255, 220, 80), 0); im.save(out / "elevator.png")
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255)); fill(im, 0, 0, 16, 16, (92, 58, 34), 5)
    fill(im, 6, 2, 4, 8, (200, 170, 70), 4); fill(im, 7, 10, 2, 4, (200, 170, 70), 4); im.save(out / "bar_tap.png")
blocks()

# --- Chandler: white Nike cap worn backwards, wavy brown hair, gray tee, navy shorts, light sneakers --------------
def chandler():
    SK = (232, 190, 160); SKD = (214, 168, 138); HAIR = (92, 60, 38); HAIRD = (70, 44, 28)
    CAP = (242, 242, 244); CAPD = (220, 220, 224); TEE = (150, 152, 158); TEED = (128, 130, 136)
    SHORTS = (36, 40, 62); SHOE = (196, 196, 214); SOLE = (240, 240, 240)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x in (0, 8, 16, 24): fill(im, x, 8, 8, 8, SK, 4)
    fill(im, 8, 0, 8, 8, HAIR, 5); fill(im, 16, 0, 8, 8, SK, 4)
    for x in (0, 16, 24): fill(im, x, 8, 8, 6, HAIR, 6)          # hair at the sides and back, below the cap
    fill(im, 8, 8, 8, 2, HAIR, 6)                                # fringe through the strap opening
    im.putpixel((10, 12), (90, 64, 44, 255)); im.putpixel((13, 12), (90, 64, 44, 255))
    for x in (9, 14): im.putpixel((x, 11), HAIRD + (255,))
    for x in range(10, 14): im.putpixel((x, 14), (176, 120, 108, 255))
    # cap on the hat overlay: crown on top and upper rows of every side, strap + opening at the front, Nike swoosh on the back
    fill(im, 40, 0, 8, 8, CAP, 3)
    for x in (32, 48, 56): fill(im, x, 8, 8, 3, CAP, 3)
    fill(im, 40, 8, 8, 2, CAP, 3); fill(im, 42, 10, 4, 1, CAPD, 2)      # strap across the forehead
    fill(im, 56, 11, 8, 1, CAPD, 2)                                     # brim edge at the back
    for (x, y) in ((58, 9), (59, 10), (60, 10), (61, 9), (62, 8)): im.putpixel((x, y), (20, 20, 20, 255))   # swoosh
    box(im, 16, 16, 8, 12, 4, lambda x, y, w, h: fill(im, x, y, w, h, TEE, 5)); fill(im, 20, 16, 8, 4, TEE); fill(im, 28, 16, 8, 4, TEED)
    fill(im, 22, 22, 4, 4, (200, 210, 214), 6)                                           # tee graphic
    for (u, v) in ((40, 16), (32, 48)):
        box(im, u, v, 4, 12, 4, lambda x, y, w, h: (fill(im, x, y, w, h, SK, 4), fill(im, x, y, w, 4, TEE, 5)))
        fill(im, u + 4, v, 4, 4, TEE); fill(im, u + 8, v, 4, 4, SK)
    for (u, v) in ((0, 16), (16, 48)):
        box(im, u, v, 4, 12, 4, lambda x, y, w, h: (fill(im, x, y, w, h, SK, 4), fill(im, x, y, w, 6, SHORTS, 4), fill(im, x, y + h - 3, w, 3, SHOE, 5), fill(im, x, y + h - 1, w, 1, SOLE, 0)))
        fill(im, u + 4, v, 4, 4, SHORTS); fill(im, u + 8, v, 4, 4, SOLE)
    im.save(ASSETS / "textures/entity/chandler.png")
chandler()

# --- Donnie: dark skin, short black hair, old blue flannel, old black pants, worn shoes ----------------------------
def donnie():
    SK = (88, 58, 42); SKD = (72, 46, 33); HAIR = (24, 20, 18)
    FL = [(46, 74, 140), (30, 46, 92), (120, 150, 200), (40, 60, 112)]
    PANTS = (34, 34, 38); PANTSW = (58, 58, 62); SHOE = (70, 56, 44)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x in (0, 8, 16, 24): fill(im, x, 8, 8, 8, SK, 4)
    fill(im, 8, 0, 8, 8, HAIR, 3); fill(im, 16, 0, 8, 8, SK, 3)
    for x in (0, 8, 16, 24): fill(im, x, 8, 8, 2, HAIR, 3)
    fill(im, 24, 10, 8, 2, HAIR, 3)
    im.putpixel((10, 12), (240, 236, 226, 255)); im.putpixel((13, 12), (240, 236, 226, 255))
    im.putpixel((11, 12), (30, 20, 16, 255)); im.putpixel((12, 12), (30, 20, 16, 255))
    for x in range(10, 14): im.putpixel((x, 14), (60, 36, 30, 255))
    def plaid(x0, y0, w, h):
        for i in range(x0, x0 + w):
            for j in range(y0, y0 + h):
                a, b2 = (i - x0) % 4, (j - y0) % 4
                c = FL[2] if (a == 0 and b2 == 0) else FL[1] if (a == 0 or b2 == 0) else FL[0] if (a + b2) % 2 else FL[3]
                k = random.randint(-6, 6)
                im.putpixel((i, j), tuple(max(0, min(255, v + k)) for v in c) + (255,))
    box(im, 16, 16, 8, 12, 4, plaid); plaid(20, 16, 8, 4); plaid(28, 16, 8, 4)
    fill(im, 23, 20, 1, 12, FL[1], 3)                                   # button placket
    for (u, v) in ((40, 16), (32, 48)):
        box(im, u, v, 4, 12, 4, lambda x, y, w, h: (plaid(x, y, w, h), fill(im, x, y + h - 2, w, 2, SK, 4)))
        plaid(u + 4, v, 4, 4); fill(im, u + 8, v, 4, 4, SK)
    for (u, v) in ((0, 16), (16, 48)):
        box(im, u, v, 4, 12, 4, lambda x, y, w, h: (fill(im, x, y, w, h, PANTS, 6), fill(im, x + 1, y + 5, 2, 2, PANTSW, 4), fill(im, x, y + h - 2, w, 2, SHOE, 5)))
        fill(im, u + 4, v, 4, 4, PANTS); fill(im, u + 8, v, 4, 4, SHOE)
    im.save(ASSETS / "textures/entity/donnie.png")
    return im
donnie_skin = donnie()

def items2():
    out = ASSETS / "textures/item"
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fill(im, 3, 3, 10, 10, (246, 246, 244), 3)                          # folded napkin
    for i in range(3, 13): im.putpixel((i, 3 + (i - 3)), (220, 220, 216, 255))
    fill(im, 3, 12, 10, 1, (214, 214, 210), 2)
    im.save(out / "napkin.png")
    card = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fill(card, 1, 3, 14, 10, (214, 230, 240), 2); fill(card, 1, 3, 14, 2, (40, 80, 150), 2)   # ID card with a blue header
    face = donnie_skin.crop((8, 8, 16, 16)).resize((5, 6), Image.NEAREST)
    card.alpha_composite(face, (2, 6))
    for y in (7, 9, 11): fill(card, 8, y, 6, 1, (90, 100, 110), 0)
    card.save(out / "donnie_id.png")
items2()
