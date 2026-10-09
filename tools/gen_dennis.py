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
