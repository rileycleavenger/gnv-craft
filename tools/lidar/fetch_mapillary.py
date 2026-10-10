#!/usr/bin/env python3
"""For each named building, download the Mapillary street photos (CC BY-SA 4.0) that look at it, newest first, plus a
contact sheet, so facade materials can be read off and written into overrides.json.

  tools/.venv/bin/python tools/lidar/fetch_mapillary.py "The Standard" "Hub Gainesville" ...

Needs a Mapillary client token in tools/.mapillary_token (git-ignored). Images go to tools/lidar/cache/mapillary/.
"""
import json, math, sys, urllib.request
from pathlib import Path
from PIL import Image, ImageDraw
from pyproj import Transformer

HERE = Path(__file__).resolve().parent
CACHE = HERE / "cache" / "mapillary"
ROOT = HERE.parent.parent
TOKEN = (HERE.parent / ".mapillary_token").read_text().strip()
ORIGIN = (29.6521, -82.3393)
TO_UTM = Transformer.from_crs("EPSG:4326", "+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", always_xy=True)
TO_LL = Transformer.from_crs("+proj=tmerc +lat_0=29.6521 +lon_0=-82.3393 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs", "EPSG:4326", always_xy=True)
E0, N0 = TO_UTM.transform(ORIGIN[1], ORIGIN[0])


def to_ll(x, z):
    return TO_LL.transform(E0 + x, N0 - z)


def to_xz(lon, lat):
    e, n = TO_UTM.transform(lon, lat)
    return e - E0, N0 - n


def main(names, per=9, radius=70):
    table = json.loads((ROOT / "src/main/resources/data/gnvcraft/gnv_map/raster/buildings.json").read_text())["buildings"]
    CACHE.mkdir(parents=True, exist_ok=True)
    for name in names:
        b = next((t for t in table if t["name"] and name.lower() in t["name"].lower()), None)
        if b is None:
            print("no building", name); continue
        cx, cz = (b["minX"] + b["maxX"]) / 2, (b["minZ"] + b["maxZ"]) / 2
        r = radius + max(b["maxX"] - b["minX"], b["maxZ"] - b["minZ"]) / 2
        lon0, lat0 = to_ll(cx - r, cz + r)
        lon1, lat1 = to_ll(cx + r, cz - r)
        url = (f"https://graph.mapillary.com/images?access_token={TOKEN}&bbox={lon0},{lat0},{lon1},{lat1}"
               f"&fields=id,captured_at,computed_compass_angle,compass_angle,geometry,thumb_1024_url&limit=1000")
        imgs = json.load(urllib.request.urlopen(url, timeout=60)).get("data", [])
        scored = []
        for im in imgs:
            lon, lat = im["geometry"]["coordinates"]
            x, z = to_xz(lon, lat)
            if b["minX"] <= x <= b["maxX"] and b["minZ"] <= z <= b["maxZ"]:
                continue
            bearing = math.degrees(math.atan2(cx - x, -(cz - z))) % 360          # compass: 0 = north (-z), 90 = east (+x)
            heading = im.get("computed_compass_angle", im.get("compass_angle", 0)) or 0
            off = abs((heading - bearing + 180) % 360 - 180)
            dist = math.hypot(cx - x, cz - z)
            if off < 40 and dist > 8:
                scored.append((-im["captured_at"], off + dist / 10, im, dist))
        scored.sort(key=lambda s: (s[0] // (86400000 * 365), s[1]))
        slug = "".join(c if c.isalnum() else "_" for c in b["name"]).strip("_").lower()[:40]
        out = CACHE / slug
        out.mkdir(exist_ok=True)
        picks = scored[:per]
        thumbs = []
        for _, _, im, dist in picks:
            p = out / f"{im['id']}.jpg"
            if not p.exists():
                p.write_bytes(urllib.request.urlopen(im["thumb_1024_url"], timeout=60).read())
            thumbs.append((p, im, dist))
        if thumbs:
            sheet = Image.new("RGB", (1500, 340 * ((len(thumbs) + 2) // 3)), "white")
            for k, (p, im, dist) in enumerate(thumbs):
                t = Image.open(p).convert("RGB"); t.thumbnail((500, 330))
                x, y = (k % 3) * 500, (k // 3) * 340
                sheet.paste(t, (x, y))
                import datetime
                ImageDraw.Draw(sheet).text((x + 5, y + 5), f"{datetime.datetime.fromtimestamp(im['captured_at'] / 1000).year} {dist:.0f}m", fill="yellow")
            sheet.save(out / "sheet.jpg", quality=85)
        print(b["name"], "#%d" % b["i"], len(imgs), "nearby,", len(scored), "facing it, saved", len(thumbs), "->", out / "sheet.jpg")


if __name__ == "__main__":
    main(sys.argv[1:])
