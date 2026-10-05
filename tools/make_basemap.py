#!/usr/bin/env python3
"""Build the offline basemap the overview map draws: country outlines from Natural Earth.

    python3 tools/make_basemap.py      # downloads ~3 MB into tools/cache on first run

Natural Earth is public domain (https://www.naturalearthdata.com). Output is
app/src/main/assets/basemap/countries.txt: one polygon ring per line, as
"lng,lat lng,lat ..." rounded to 0.02 degrees (about 2 km), which is finer than
a phone screen can show at the zoom a whole trip needs, and consecutive
duplicates removed. Standard library only.
"""
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson"
CACHE = ROOT / "tools" / "cache" / "ne_50m_admin_0_countries.geojson"
OUT = ROOT / "app" / "src" / "main" / "assets" / "basemap" / "countries.txt"
STEP = 0.02


def rings(geometry):
    if geometry["type"] == "Polygon":
        yield from geometry["coordinates"][:1]  # outer ring only; lakes are not worth the bytes
    elif geometry["type"] == "MultiPolygon":
        for poly in geometry["coordinates"]:
            yield from poly[:1]


def main():
    if not CACHE.exists():
        CACHE.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(SRC, CACHE)
    data = json.loads(CACHE.read_text())
    lines = []
    for f in data["features"]:
        for ring in rings(f["geometry"]):
            pts, last = [], None
            for lng, lat in ring:
                p = (round(round(lng / STEP) * STEP, 2), round(round(lat / STEP) * STEP, 2))
                if p != last:
                    pts.append(p)
                    last = p
            if len(pts) >= 4:
                lines.append(" ".join(f"{x:g},{y:g}" for x, y in pts))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("# Natural Earth 1:50m admin-0 countries, public domain. Built by tools/make_basemap.py.\n" + "\n".join(lines) + "\n")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(lines)} rings, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
