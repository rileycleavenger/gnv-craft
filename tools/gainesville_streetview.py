#!/usr/bin/env python3
"""
One-time Street View capture of the outlined part of Gainesville, FL, using streetview-dl,
kept inside Google's free tier (100k Map Tiles API requests/month, per the streetview-dl README).

Why not every panorama: each panorama costs 32 tile requests even at the lowest quality, so
100k requests buys roughly 2,600 images. The outlined area (about 41 km²) holds far more
panoramas than that. To still cover ALL of it, the script lays a grid over the outline and
keeps one panorama per grid cell, sizing the cells so the total fits the budget. The result
is an even sample of roughly one image per block across the entire outlined area.

How it works (one command, three steps):
  1. discover  - query a coarse grid of points inside the outline for nearby panoramas
                 (capped at a small slice of the budget).
  2. plan      - drop older captures (keep only the newest imagery at each spot), then pick
                 one panorama per cell, newest first, with the largest number of cells the
                 remaining budget can pay for. Saved to plan.json.
  3. download  - fetch the planned panoramas at low quality, saved as
                 <lat>_<lng>_<date>_<pano_id>.jpg using the panorama's own coordinates.

Every request is counted (conservatively) in gainesville_sv/usage.json and the script never
lets the total pass BUDGET. If it's interrupted, re-running continues without re-spending.

Run:
  pip install streetview-dl
  export GOOGLE_MAPS_API_KEY="..."
  python gainesville_streetview.py          # discover + plan, then stops and shows the plan
  python gainesville_streetview.py --yes    # same, then downloads

Important: the budget only counts this script. Any other use of the same API key in the same
calendar month also counts toward Google's 100k, so use a key that nothing else uses, and
set a budget alert in Google Cloud as a backstop.
"""
import argparse
import json
import math
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# ------------------------------------------------------------------ settings

# The area outlined on the map: the west loop (NW 8th Ave / NW 34th St / Archer Rd /
# SW 16th Ave) joined to the north-east lobe (Waldo Rd corridor up past the airport).
# (lat, lng) vertices traced from the drawing, accurate to roughly 50-100 m.
OUTLINE = [
    (29.6631, -82.3739), (29.6596, -82.3793), (29.6538, -82.3834), (29.6468, -82.3857),
    (29.6421, -82.3861), (29.6363, -82.3830), (29.6322, -82.3827), (29.6275, -82.3813),
    (29.6258, -82.3780), (29.6247, -82.3665), (29.6255, -82.3610), (29.6299, -82.3516),
    (29.6322, -82.3455), (29.6357, -82.3428), (29.6392, -82.3353), (29.6393, -82.3224),
    (29.6392, -82.3130), (29.6430, -82.3042), (29.6485, -82.3008), (29.6543, -82.2981),
    (29.6613, -82.2947), (29.6689, -82.2920), (29.6750, -82.2879), (29.6765, -82.2818),
    (29.6788, -82.2683), (29.6823, -82.2581), (29.6870, -82.2541), (29.6963, -82.2527),
    (29.7033, -82.2541), (29.7062, -82.2581), (29.7083, -82.2649), (29.7086, -82.2751),
    (29.7071, -82.2859), (29.7027, -82.2900), (29.6998, -82.2923), (29.6940, -82.2933),
    (29.6887, -82.2954), (29.6841, -82.2988), (29.6800, -82.3015), (29.6753, -82.3042),
    (29.6707, -82.3062), (29.6648, -82.3089), (29.6625, -82.3109), (29.6613, -82.3157),
    (29.6611, -82.3218), (29.6615, -82.3258), (29.6615, -82.3394), (29.6622, -82.3563),
    (29.6628, -82.3698),
]

FREE_TIER = 100_000
BUDGET = 95_000            # hard ceiling for this run; 5% margin under the free tier
DISCOVERY_SHARE = 0.10     # at most this fraction of BUDGET goes to finding panoramas

QUALITY = "low"            # lowest quality = fewest requests = most coverage
TILES_PER_PANO = {"low": 32, "medium": 128, "high": 512}   # from the streetview-dl README
EXTRA_CALLS_PER_PANO = 2   # session + metadata, counted to be safe

GRID_SPACING_M = 250       # discovery grid
SEARCH_RADIUS_M = 190      # > spacing * 0.71 so search circles overlap
MAX_PANOS_PER_POINT = 8    # streetview-dl --max-panos (API calls per point); budgeted in full

WORKDIR = Path("gainesville_sv")


# ------------------------------------------------------------------ geometry

def point_in_polygon(lat, lng, poly=OUTLINE):
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        yi, xi = poly[i]
        yj, xj = poly[j]
        if (yi > lat) != (yj > lat) and lng < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def grid_points(spacing_m, poly=OUTLINE):
    lats, lngs = [p[0] for p in poly], [p[1] for p in poly]
    s, w, n, e = min(lats), min(lngs), max(lats), max(lngs)
    dlat = spacing_m / 111_320
    dlng = spacing_m / (111_320 * math.cos(math.radians((s + n) / 2)))
    lat = s
    while lat <= n:
        lng = w
        while lng <= e:
            if point_in_polygon(lat, lng, poly):
                yield round(lat, 6), round(lng, 6)
            lng += dlng
        lat += dlat


KY, KX = 111_320, 111_320 * math.cos(math.radians(29.66))  # meters per degree here


def date_key(p):
    """Sortable capture date ('2024-03' -> '2024-03'); unknown dates sort as oldest."""
    return p.get("date") or "0000-00"


def newest_only(panos, radius_m=15):
    """Drop older captures of the same spot: any panorama with a newer one within radius_m.
    Street View keeps past years' captures as separate panoramas a few meters apart."""
    buckets = {}
    for p in panos:
        key = (int(float(p["lat"]) * KY // radius_m), int(float(p["lng"]) * KX // radius_m))
        buckets.setdefault(key, []).append(p)
    keep = []
    for p in panos:
        y, x = float(p["lat"]) * KY, float(p["lng"]) * KX
        ky, kx = int(y // radius_m), int(x // radius_m)
        newer_nearby = any(
            date_key(q) > date_key(p)
            and (float(q["lat"]) * KY - y) ** 2 + (float(q["lng"]) * KX - x) ** 2 <= radius_m ** 2
            for dy in (-1, 0, 1) for dx in (-1, 0, 1)
            for q in buckets.get((ky + dy, kx + dx), ())
        )
        if not newer_nearby:
            keep.append(p)
    return keep


def thin_to(panos, max_count):
    """Pick an evenly spread subset of at most max_count panoramas: one per square cell,
    using the smallest cell size that fits. In each cell the newest capture wins, then the
    one nearest the cell's center. Returns (subset, cell_size_m)."""
    if len(panos) <= max_count:
        return list(panos), 0

    def one_per_cell(cell_m):
        cells = {}
        for p in panos:
            y, x = float(p["lat"]) * KY, float(p["lng"]) * KX
            key = (int(y // cell_m), int(x // cell_m))
            cy, cx = (key[0] + 0.5) * cell_m, (key[1] + 0.5) * cell_m
            # rank: newest date first, then closest to the cell center
            rank = (date_key(p), -((y - cy) ** 2 + (x - cx) ** 2))
            if key not in cells or rank > cells[key][0]:
                cells[key] = (rank, p)
        return [v[1] for v in cells.values()]

    lo, hi = 1.0, 5000.0
    for _ in range(40):  # binary search for the smallest cell size that fits
        mid = (lo + hi) / 2
        if len(one_per_cell(mid)) > max_count:
            lo = mid
        else:
            hi = mid
    return one_per_cell(hi), round(hi)


# ------------------------------------------------------------------ budget

class Ledger:
    """Counts every request this run makes, persisted so restarts can't overspend."""

    def __init__(self, path):
        self.path = path
        self.data = json.loads(path.read_text()) if path.exists() else {"discovery": 0, "download": 0}

    @property
    def used(self):
        return self.data["discovery"] + self.data["download"]

    @property
    def remaining(self):
        return max(BUDGET - self.used, 0)

    def charge(self, phase, n):
        self.data[phase] += n
        self.path.write_text(json.dumps(self.data, indent=2))

    def report(self, label=""):
        print(f"{label}Requests used: {self.used:,} of {BUDGET:,} budget "
              f"(free tier {FREE_TIER:,}): discovery {self.data['discovery']:,}, "
              f"download {self.data['download']:,}.")


# ------------------------------------------------------------------ step 1: discover

def query_point(lat, lng):
    cmd = ["streetview-dl", "query", "--lat", str(lat), "--lng", str(lng),
           "--radius", str(SEARCH_RADIUS_M), "--max-results", "25",
           "--depth", "1", "--max-panos", str(MAX_PANOS_PER_POINT), "--json"]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout).strip()[-300:])
    start = r.stdout.find("{")
    return json.loads(r.stdout[start:]).get("panoramas", []) if start != -1 else []


def discover(ledger, workers):
    done_file, panos_file = WORKDIR / "done_points.txt", WORKDIR / "panos.jsonl"
    points = list(grid_points(GRID_SPACING_M))
    done = set(done_file.read_text().split()) if done_file.exists() else set()
    todo = [p for p in points if f"{p[0]},{p[1]}" not in done]
    already_done = len(points) - len(todo)

    panos = {}
    if panos_file.exists():
        for line in panos_file.read_text().splitlines():
            if line.strip():
                p = json.loads(line)
                panos[p["pano_id"]] = p

    cap = int(BUDGET * DISCOVERY_SHARE)
    affordable = max(cap - ledger.data["discovery"], 0) // MAX_PANOS_PER_POINT
    if affordable < len(todo):
        print(f"Discovery budget covers {affordable:,} of {len(todo):,} remaining points; "
              "skipping the rest.")
        todo = todo[:affordable]

    print(f"Step 1/3 discover: {len(points):,} grid points ({GRID_SPACING_M} m apart), "
          f"{already_done:,} already done, querying {len(todo):,}.")
    if todo:
        t0 = time.time()
        with ThreadPoolExecutor(max_workers=workers) as pool, \
             open(done_file, "a") as df, open(panos_file, "a") as pf, \
             open(WORKDIR / "errors.log", "a") as ef:
            futures = {pool.submit(query_point, lat, lng): (lat, lng) for lat, lng in todo}
            for i, fut in enumerate(as_completed(futures), 1):
                lat, lng = futures[fut]
                ledger.charge("discovery", MAX_PANOS_PER_POINT)
                try:
                    for p in fut.result():
                        pid = p.get("pano_id")
                        if pid and pid not in panos:  # outline filter happens in the plan step
                            panos[pid] = p
                            pf.write(json.dumps(p) + "\n")
                    df.write(f"{lat},{lng}\n")
                except Exception as e:
                    ef.write(f"discover {lat},{lng}\t{e}\n")  # not retried: retrying costs budget
                    df.write(f"{lat},{lng}\n")
                if i % 25 == 0 or i == len(todo):
                    pf.flush(); df.flush()
                    eta = (len(todo) - i) / (i / max(time.time() - t0, 1e-6)) / 60
                    print(f"  {i:,}/{len(todo):,} points | {len(panos):,} panoramas | ~{eta:,.0f} min left",
                          flush=True)
    print(f"Found {len(panos):,} panoramas in and around the outline.")
    return list(panos.values())


# ------------------------------------------------------------------ step 2: plan

def make_plan(panos, ledger):
    plan_file = WORKDIR / "plan.json"
    if plan_file.exists():  # plan is fixed once made, so a restart downloads the same set
        plan = json.loads(plan_file.read_text())
        print(f"Step 2/3 plan: using existing plan of {len(plan['panos']):,} panoramas.")
        return plan
    cost = TILES_PER_PANO[QUALITY] + EXTRA_CALLS_PER_PANO
    max_count = ledger.remaining // cost
    newest = newest_only(panos)  # compared against everything found, incl. just outside
    current = [p for p in newest if point_in_polygon(float(p["lat"]), float(p["lng"]))]
    print(f"Step 2/3 plan: {len(panos) - len(newest):,} older captures dropped "
          f"(a newer capture exists within 15 m); {len(current):,} current panoramas "
          f"inside the outline.")
    chosen, cell = thin_to(current, max_count)
    plan = {"quality": QUALITY, "cost_per_pano": cost, "cell_size_m": cell,
            "panos": sorted(chosen, key=lambda p: p["pano_id"])}
    plan_file.write_text(json.dumps(plan))
    spacing = f"one per ~{cell} m square" if cell else "every panorama found"
    print(f"Step 2/3 plan: {len(chosen):,} panoramas at '{QUALITY}' quality ({spacing}), "
          f"{len(chosen) * cost:,} requests; about {len(chosen) * 1:,} MB on disk.")
    return plan


# ------------------------------------------------------------------ step 3: download

def pano_url(p):
    heading = p.get("heading", 0) or 0
    return (f"https://www.google.com/maps/@{p['lat']},{p['lng']},3a,75y,{heading}h,90t"
            f"/data=!3m7!1e1!3m5!1s{p['pano_id']}!")


def image_name(p):
    """e.g. 29.651934_-82.324812_2024-03_<pano_id>.jpg  (lat_lng_date_panoid)."""
    date = (p.get("date") or "unknown").replace("/", "-")
    safe_id = "".join(c if c.isalnum() or c in "-_" else "-" for c in p["pano_id"])
    return f"{float(p['lat']):.6f}_{float(p['lng']):.6f}_{date}_{safe_id}.jpg"


def download_one(p, out_dir, quality):
    final = out_dir / image_name(p)
    tmp = out_dir / f".{final.stem}.part.jpg"
    cmd = ["streetview-dl", "--quality", quality, "--retries", "1", "--output", str(tmp), pano_url(p)]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if r.returncode != 0 or not tmp.exists():
        tmp.unlink(missing_ok=True)
        raise RuntimeError((r.stderr or r.stdout).strip()[-300:])
    tmp.rename(final)


def download(plan, ledger, workers):
    out_dir = WORKDIR / "panoramas"
    out_dir.mkdir(exist_ok=True)
    attempted_file = WORKDIR / "attempted.txt"
    attempted = set(attempted_file.read_text().split()) if attempted_file.exists() else set()
    todo = [p for p in plan["panos"] if p["pano_id"] not in attempted]
    cost = plan["cost_per_pano"]
    affordable = ledger.remaining // cost
    if affordable < len(todo):
        print(f"Budget left covers {affordable:,} of {len(todo):,}; downloading those only.")
        todo = todo[:affordable]
    print(f"Step 3/3 download: {len(todo):,} panoramas ({len(attempted):,} already attempted).")
    if not todo:
        return

    ok = fail = 0
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool, open(attempted_file, "a") as af, \
         open(WORKDIR / "errors.log", "a") as ef:
        futures = {pool.submit(download_one, p, out_dir, plan["quality"]): p for p in todo}
        for i, fut in enumerate(as_completed(futures), 1):
            p = futures[fut]
            ledger.charge("download", cost)  # charged even on failure: tiles may have been fetched
            af.write(p["pano_id"] + "\n"); af.flush()
            try:
                fut.result(); ok += 1
            except Exception as e:
                fail += 1
                ef.write(f"download {p['pano_id']}\t{e}\n")
            if i % 10 == 0 or i == len(todo):
                eta = (len(todo) - i) / (i / max(time.time() - t0, 1e-6)) / 60
                print(f"  {i:,}/{len(todo):,} | ok {ok:,} | failed {fail:,} | ~{eta:,.0f} min left",
                      flush=True)
    print(f"Saved {ok:,} images to {out_dir}/" + (f" ({fail:,} failed, see errors.log)" if fail else ""))


# ------------------------------------------------------------------ main

def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--yes", action="store_true", help="download after planning (otherwise stop at the plan)")
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    try:
        subprocess.run(["streetview-dl", "--help"], capture_output=True, check=True)
    except (FileNotFoundError, subprocess.CalledProcessError):
        sys.exit("streetview-dl not found. Install it with:  pip install streetview-dl")
    if not os.environ.get("GOOGLE_MAPS_API_KEY"):
        print("Note: GOOGLE_MAPS_API_KEY is not set; relying on `streetview-dl --configure`.\n")

    WORKDIR.mkdir(exist_ok=True)
    ledger = Ledger(WORKDIR / "usage.json")
    ledger.report("Start. ")

    panos = discover(ledger, args.workers)
    if not panos:
        sys.exit("No panoramas found; check your API key and errors.log.")
    plan = make_plan(panos, ledger)

    if not args.yes:
        print("\nPlan saved. Run again with --yes to download.")
        ledger.report()
        return
    download(plan, ledger, args.workers)
    ledger.report("Done. ")


if __name__ == "__main__":
    main()