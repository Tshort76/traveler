#!/usr/bin/env python3
"""Turns a Traveler trip file into KML layers for one Google My Maps map.

    python3 tools/make_map.py trip.json                # writes ~/Downloads/<trip-id>-map/
    python3 tools/make_map.py trip.json --out somewhere

One file per layer, since My Maps imports a file into one layer and may drop KML folders:
"01 Overview.kml" (stays, lodging, airports and stations, and the transfers between towns as lines,
by any vehicle; a ride across town is left out), then one
file per stay with every activity for it, planned or not. The home airport (a zero-night stay that
opens or closes the trip) and the flights to and from it are left out; the airport at the other
end of those flights stays. Each pin carries Stay, Status and Day as data columns, so a layer can
be restyled by them in My Maps. Standard library only.
"""
import argparse
import html
import json
import math
import re
import sys
from datetime import date
from pathlib import Path
from urllib.parse import quote

MAX_LAYERS = 10  # My Maps' limit per map
BETWEEN_TOWNS_KM = 20  # a transfer shorter than this, stay to stay, is a ride across town and isn't drawn
GLYPHS = {"flight": "✈️", "train": "🚆", "bus": "🚌", "boat": "⛴️", "ferry": "⛴️", "car": "🚗", "taxi": "🚕", "hike": "🥾"}
# The app's rule for a transfer of mode "other": the first vehicle its details name.
MODE_WORDS = [
    ("flight", r"\b(fly|flies|flight|flights)\b"), ("train", r"\btrains?\b"), ("bus", r"\b(bus|buses|shuttle|coach)\b"),
    ("boat", r"\b(boat|catamaran)\b"), ("ferry", r"\bferry\b"), ("taxi", r"\b(taxi|remis|uber|cab|driver)\b"),
    ("car", r"\b(car|drive|driving)\b"), ("hike", r"\b(hike|hiking|trek|walk|descend|ascend)\b"),
]
# KML colours are aabbggrr.
STAY, LODGING, AIRPORT, ROUTE, PLANNED, UNPLANNED = "ff3d7a1b", "ffb0279c", "ff7a4a2e", "ff7a4a2e", "ff1f6fe8", "ff9e9e9e"


def is_home(stay):
    return stay.get("arrive") == stay.get("depart")


def shown_mode(t):
    mode = t.get("mode")
    if mode and mode != "other":
        return mode
    text = (t.get("details") or "").lower()
    found = [(m.start(), name) for name, rx in MODE_WORDS if (m := re.search(rx, text))]
    return min(found)[1] if found else mode


def km(p, q):
    """Great-circle distance between two places; 0 when either has no coordinates."""
    if not (coords(p) and coords(q)):
        return 0
    la1, lo1, la2, lo2 = map(math.radians, (p["lat"], p["lng"], q["lat"], q["lng"]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def coords(place):
    if place and place.get("lat") is not None and place.get("lng") is not None:
        return f"{place['lng']},{place['lat']},0"
    return None


def maps_url(place, name, context=None):
    """Google Maps for a place, as the app opens it: its own URL, place id, query, coordinates, then name."""
    place = place or {}
    if place.get("mapsUrl"):
        return place["mapsUrl"]
    if place.get("query"):
        q = place["query"]
    elif coords(place):
        q = f"{place['lat']},{place['lng']}"
    else:
        q = ", ".join(x for x in [place.get("name") or name, place.get("address") or context] if x)
    pid = f"&query_place_id={quote(place['placeId'])}" if place.get("placeId") else ""
    return f"https://www.google.com/maps/search/?api=1&query={quote(q)}{pid}"


def day_label(iso):
    d = date.fromisoformat(iso)
    return f"{d.strftime('%a')} {d.day} {d.strftime('%b')}"


def esc(s):
    return html.escape(s or "", quote=False)


def placemark(name, lines, geometry, style, data=None):
    desc = "<br>".join(esc(x) if not x.startswith("http") else f'<a href="{esc(x)}">Open in Google Maps</a>' for x in lines if x)
    ext = "".join(f'<Data name="{esc(k)}"><value>{esc(v)}</value></Data>' for k, v in (data or {}).items())
    return (f"<Placemark><name>{esc(name)}</name><description><![CDATA[{desc}]]></description><styleUrl>#{style}</styleUrl>"
            + (f"<ExtendedData>{ext}</ExtendedData>" if ext else "") + geometry + "</Placemark>")


def point(c):
    return f"<Point><coordinates>{c}</coordinates></Point>"


def kml(name, placemarks):
    styles = "".join(
        f'<Style id="{k}"><IconStyle><color>{c}</color></IconStyle><LineStyle><color>{c}</color><width>3</width></LineStyle></Style>'
        for k, c in [("stay", STAY), ("lodging", LODGING), ("airport", AIRPORT), ("route", ROUTE), ("planned", PLANNED), ("unplanned", UNPLANNED)]
    )
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>{esc(name)}</name>'
            f"{styles}{''.join(placemarks)}</Document></kml>\n")


def overview(trip):
    stays = {s["id"]: s for s in trip.get("stays", [])}
    marks, airports = [], {}
    for s in trip.get("stays", []):
        if is_home(s):
            continue
        nights = (date.fromisoformat(s["depart"]) - date.fromisoformat(s["arrive"])).days
        if c := coords(s.get("place")):
            marks.append(placemark(s["name"], [f"{day_label(s['arrive'])} – {day_label(s['depart'])} · {nights} night{'s' * (nights != 1)}", s.get("summary")],
                                   point(c), "stay", {"Kind": "Stay"}))
        lodging = s.get("lodging") or {}
        if c := coords(lodging.get("place")):
            name = lodging.get("name") or lodging["place"].get("name") or f"Lodging in {s['name']}"
            marks.append(placemark(f"🏨 {name}", [s["name"], "Booked" if lodging.get("status") == "booked" else "Not booked yet",
                                                 maps_url(lodging["place"], name, s["name"])], point(c), "lodging", {"Kind": "Lodging"}))

    def terminal(place, mode):
        c = coords(place)
        if c and c not in airports:
            name = place.get("name") or place.get("query") or "Station"
            airports[c] = placemark(f"{GLYPHS.get(mode, '📍')} {name}", [maps_url(place, name)], point(c), "airport",
                                    {"Kind": "Airport" if mode == "flight" else "Station"})
        return c

    for t in trip.get("transfers", []):
        a, b = stays.get(t["from"], {}), stays.get(t["to"], {})
        mode = shown_mode(t)
        start = terminal(t.get("fromPlace"), mode) if not is_home(a) else None
        end = terminal(t.get("toPlace"), mode) if not is_home(b) else None
        if is_home(a) or is_home(b) or km(a.get("place"), b.get("place")) < BETWEEN_TOWNS_KM:
            continue
        start = start or coords(a.get("place"))
        end = end or coords(b.get("place"))
        if start and end:
            glyph = GLYPHS.get(mode, "➜")
            when = " · ".join(x for x in [day_label(t["date"]), "–".join(x for x in [t.get("depart"), t.get("arrive")] if x)] if x)
            marks.append(placemark(f"{glyph} {a['name']} → {b['name']}", [when, t.get("details")],
                                   f"<LineString><tessellate>1</tessellate><coordinates>{start} {end}</coordinates></LineString>",
                                   "route", {"Kind": "Transfer"}))
    return marks + list(airports.values())


def planned_days(trip):
    """Activity id → where it is planned ("Tue 19 Oct afternoon 14:30"), skipped items left out."""
    out = {}
    for d in trip.get("days", []):
        for p in d.get("plan", []):
            if p.get("status") == "skipped":
                continue
            out.setdefault(p["activityId"], []).append(" ".join(x for x in [day_label(d["date"]), p.get("slot"), p.get("time")] if x))
    return out


def activity_marks(trip, stay_ids):
    stays = {s["id"]: s for s in trip.get("stays", [])}
    planned = planned_days(trip)
    marks = []
    for a in trip.get("activities", []):
        if a.get("stayId") not in stay_ids or not (c := coords(a.get("place"))):
            continue
        stay = stays[a["stayId"]]
        days = planned.get(a["id"], [])
        stars = "★" * (a.get("stars") or 0)
        lines = [a.get("short"), f"Planned: {', '.join(days)}" if days else "Not planned", stars,
                 maps_url(a.get("place"), a["name"], stay["name"])]
        name = " ".join(x for x in [a.get("tag"), a["name"]] if x)
        marks.append(placemark(name, lines, point(c), "planned" if days else "unplanned",
                               {"Stay": stay["name"], "Status": "Planned" if days else "Not planned", "Day": ", ".join(days)}))
    return marks


def layers(trip):
    """(layer name, placemarks) in order: the overview, then a layer per stay, the smallest merged when there are too many."""
    stays = [s for s in trip.get("stays", []) if not is_home(s)]
    count = {s["id"]: sum(1 for a in trip.get("activities", []) if a.get("stayId") == s["id"] and coords(a.get("place"))) for s in stays}
    stays = [s for s in stays if count[s["id"]]]
    room = MAX_LAYERS - 1
    if len(stays) > room:
        keep = {s["id"] for s in sorted(stays, key=lambda s: -count[s["id"]])[:room - 1]}
        merged = [s["id"] for s in stays if s["id"] not in keep]
        stays = [s for s in stays if s["id"] in keep]
    else:
        merged = []
    out = [("Overview", overview(trip))]
    out += [(s["name"], activity_marks(trip, {s["id"]})) for s in stays]
    if merged:
        out.append(("Other stays", activity_marks(trip, set(merged))))
    return out


def file_name(i, name):
    safe = re.sub(r"[^\w ·,.-]+", "", name).strip()
    return f"{i:02d} {safe}.kml"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("trip")
    ap.add_argument("--out")
    args = ap.parse_args()
    trip = json.loads(Path(args.trip).read_text())
    if trip.get("format") != "traveler-trip":
        sys.exit(f"{args.trip} is not a traveler-trip file")
    out = Path(args.out) if args.out else Path.home() / "Downloads" / f"{trip['id']}-map"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.kml"):
        old.unlink()
    for i, (name, marks) in enumerate(layers(trip), 1):
        (out / file_name(i, name)).write_text(kml(name, marks))
        print(f"{file_name(i, name)}: {len(marks)} pins and lines")
    print(f"\nWrote {out}")


if __name__ == "__main__":
    main()
