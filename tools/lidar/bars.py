#!/usr/bin/env python3
"""Midtown and downtown bars: find each OSM bar's building in the bake, download the Mapillary street photos that face it,
and build one contact sheet per 6 bars for review. Facade notes then go into overrides.json by hand.

  tools/.venv/bin/python tools/lidar/bars.py
"""
import json, math, sys, urllib.request, datetime
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, str(Path(__file__).resolve().parent))
from bake_core import proj, OUT
from bake_stadium import Grid
from fetch_mapillary import TOKEN, to_ll, to_xz

HERE = Path(__file__).resolve().parent
CACHE = HERE / "cache"
AREAS = {"midtown": (-82.3460, 29.6490, -82.3330, 29.6570), "downtown": (-82.3300, 29.6450, -82.3160, 29.6570)}


def bars():
    seen = {}
    for f in (CACHE / "osm").glob("t_*.json"):
        for e in json.loads(f.read_text()):
            t = e.get("tags", {})
            if t.get("amenity") not in ("bar", "pub", "nightclub", "biergarten") and t.get("name") not in ("UF Plaza", "The Swamp Restaurant"):
                continue
            if e["type"] == "node":
                lat, lon = e["lat"], e["lon"]
            else:
                g = e.get("geometry") or [p for m in e.get("members", []) for p in (m.get("geometry") or [])]
                if not g:
                    continue
                lat = sum(p["lat"] for p in g) / len(g); lon = sum(p["lon"] for p in g) / len(g)
            for area, (w, s, e_, n) in AREAS.items():
                if w < lon < e_ and s < lat < n:
                    seen[t.get("name")] = dict(name=t.get("name"), area=area, lat=lat, lon=lon,
                                               addr=(t.get("addr:housenumber", "") + " " + t.get("addr:street", "")).strip())
    return list(seen.values())


def main():
    table = json.loads((OUT / "buildings.json").read_text())["buildings"]
    out = []
    for bar in sorted(bars(), key=lambda b: (b["area"], b["name"])):
        x, z = proj(bar["lon"], bar["lat"])
        x, z = int(round(x)), int(round(z))
        g = Grid(x - 20, z - 20, x + 20, z + 20)
        b = g.a["b"]
        best = None
        for r in range(0, 16):
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    v = int(b[z + dz - g.z0, x + dx - g.x0])
                    if v and best is None:
                        best = (v, x + dx, z + dz)
            if best:
                break
        bar.update(x=x, z=z, building=best[0] if best else None, point=[best[1], best[2]] if best else None)
        if best:
            t = table[best[0] - 1]
            bar["bbox"] = (t["minX"], t["minZ"], t["maxX"], t["maxZ"])
            bar["photos"] = photos(bar)
        out.append(bar)
        print(bar["area"], bar["name"], bar.get("building"), len(bar.get("photos", [])), "photos", flush=True)
    (CACHE / "bars.json").write_text(json.dumps(out, indent=1))
    sheets(out)


def photos(bar, per=3):
    x, z = bar["x"], bar["z"]
    lon0, lat0 = to_ll(x - 60, z + 60); lon1, lat1 = to_ll(x + 60, z - 60)
    url = (f"https://graph.mapillary.com/images?access_token={TOKEN}&bbox={lon0},{lat0},{lon1},{lat1}"
           f"&fields=id,captured_at,computed_compass_angle,compass_angle,geometry,thumb_1024_url&limit=500")
    try:
        imgs = json.load(urllib.request.urlopen(url, timeout=60)).get("data", [])
    except Exception:
        return []
    scored = []
    for im in imgs:
        ix, iz = to_xz(*im["geometry"]["coordinates"])
        d = math.hypot(x - ix, z - iz)
        if not 6 < d < 45:
            continue
        bearing = math.degrees(math.atan2(x - ix, -(z - iz))) % 360
        head = im.get("computed_compass_angle", im.get("compass_angle", 0)) or 0
        off = abs((head - bearing + 180) % 360 - 180)
        if off < 30:
            scored.append((-(im["captured_at"] // (86400000 * 365)), off + d / 4, im))
    scored.sort(key=lambda s: (s[0], s[1]))
    folder = CACHE / "mapillary" / "bars" / "".join(c if c.isalnum() else "_" for c in bar["name"]).lower()
    folder.mkdir(parents=True, exist_ok=True)
    got = []
    for _, _, im in scored[:per]:
        p = folder / f"{im['id']}.jpg"
        if not p.exists():
            p.write_bytes(urllib.request.urlopen(im["thumb_1024_url"], timeout=60).read())
        got.append(dict(path=str(p), year=datetime.datetime.fromtimestamp(im["captured_at"] / 1000).year))
    return got


def sheets(bars_):
    rows = [b for b in bars_ if b.get("photos")]
    for s in range(0, len(rows), 6):
        sheet = Image.new("RGB", (1500, 6 * 230), "white")
        d = ImageDraw.Draw(sheet)
        for r, bar in enumerate(rows[s:s + 6]):
            for c, ph in enumerate(bar["photos"][:3]):
                im = Image.open(ph["path"]).convert("RGB"); im.thumbnail((500, 225))
                sheet.paste(im, (c * 500, r * 230))
                d.text((c * 500 + 5, r * 230 + 18), str(ph["year"]), fill="yellow")
            d.text((5, r * 230 + 4), f"{bar['area']}: {bar['name']}", fill="yellow")
        sheet.save(CACHE / "mapillary" / "bars" / f"sheet{s // 6 + 1}.jpg", quality=82)


if __name__ == "__main__":
    main()
