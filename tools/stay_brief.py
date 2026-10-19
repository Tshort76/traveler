#!/usr/bin/env python3
"""Prints one stay of a Traveler trip file as the opening of a focused chat: its dates, lodging,
flights in and out, and standout (3-star) activities.

    python3 stay_brief.py --trip argentina --stay madryn
    python3 stay_brief.py --trip trip.json --stay 3      # a path, and a number from the stay list

It finds the trip itself: every .json under /mnt/data, /mnt/user-data, the working folder and
its own folder whose contents are a traveler-trip file, whatever it is named ("x.trip (1).json"
included). --trip narrows them by title, id or file name; with no --trip, the one trip, or the
one trip with that stay.
Copies of one trip resolve to the newest (exported, generated, then file time).

With no trip, several, or a stay that matches none or several, it prints the choices numbered and
exits 2, so the caller can ask which one is meant and run it again.
"""
import argparse
import json
import os
import sys
from datetime import date
from pathlib import Path

ROOTS = ["/mnt/data", "/mnt/user-data", ".", os.path.dirname(os.path.abspath(__file__))]

def stays_of(trip):
    """The stays a traveler would name: every stay but a zero-night home airport."""
    return [s for s in trip.get("stays", []) if s.get("arrive") != s.get("depart")]


def find(stays, query):
    q = query.strip().lower()
    if q.isdigit() and 1 <= int(q) <= len(stays):
        return [stays[int(q) - 1]]
    exact = [s for s in stays if q in (s.get("id", "").lower(), s.get("name", "").lower())]
    if exact:
        return exact
    def text(s):
        return " ".join([s.get("id", ""), s.get("name", ""), s.get("region", ""), (s.get("place") or {}).get("query", "")]).lower()
    return [s for s in stays if all(w in text(s) for w in q.split())]


def day(d):
    return date.fromisoformat(d).strftime("%a %-d %b")


def listing(stays):
    return "\n".join(f"{i}. {s['name']} · {day(s['arrive'])} – {day(s['depart'])}" for i, s in enumerate(stays, 1))


def brief(trip, s):
    names = {x["id"]: x["name"] for x in trip.get("stays", [])}
    nights = (date.fromisoformat(s["depart"]) - date.fromisoformat(s["arrive"])).days
    out = [s["name"], f"{day(s['arrive'])} – {day(s['depart'])} {s['depart'][:4]} · {nights} night{'s' * (nights != 1)}"]
    lod = s.get("lodging") or {}
    where = (lod.get("place") or {})
    out.append("Lodging: " + (" · ".join(x for x in (lod.get("name"), where.get("address") or where.get("query"), "booked" if lod.get("status") == "booked" else "")
                                         if x) or "not chosen yet"))
    for t in trip.get("transfers", []):
        if s["id"] not in (t.get("to"), t.get("from")):
            continue
        way = f"from {names.get(t['from'], t['from'])}" if t.get("to") == s["id"] else f"to {names.get(t['to'], t['to'])}"
        times = "–".join(x for x in (t.get("depart"), t.get("arrive")) if x)
        booked = " · booked" if (t.get("booking") or {}).get("status") == "booked" else ""
        out.append(f"{'Arrive' if t.get('to') == s['id'] else 'Leave'}: {t.get('mode') if t.get('mode') not in (None, 'other') else 'transfer'} {way}, {day(t['date'])} {times}{booked}".rstrip())

    # The standouts (3 stars, ✨ in the app), with the day each is planned on.
    when = {}
    for d in trip.get("days", []):
        if (d.get("stayId") or s["id"]) == s["id"] and s["arrive"] <= d["date"] <= s["depart"]:
            for item in d.get("plan", []):
                when.setdefault(item["activityId"], f"{day(d['date'])} {item.get('time') or item.get('slot', '')}".strip())
    booked = {c["activityId"] for c in trip.get("commitments", []) if c.get("booked") and c.get("activityId")}
    standouts = [a for a in trip.get("activities", []) if a.get("stayId") == s["id"] and a.get("stars") == 3]
    standouts.sort(key=lambda a: (a["id"] not in when, when.get(a["id"], ""), a.get("rank", 99)))
    out += ["", "Standouts ✨:"]
    out += [f"  {a.get('name')} · {when.get(a['id'], 'not scheduled')}"
            + (" · booked" if a["id"] in booked or (a.get("booking") or {}).get("status") == "booked" else "") for a in standouts] or ["  none"]
    return "\n".join(out)


def trips_in(roots):
    """Every traveler-trip file under roots, one per trip id: the newest copy."""
    newest = {}
    for root in roots:
        for f in Path(root).rglob("*.json") if Path(root).is_dir() else []:
            try:
                trip = json.loads(f.read_text())
            except (OSError, ValueError):
                continue
            if not isinstance(trip, dict) or trip.get("format") != "traveler-trip":
                continue
            age = (max((trip.get("exportedFrom") or {}).get("exportedAt") or "", trip.get("generatedAt") or ""), f.stat().st_mtime)
            key = trip.get("id") or str(f)
            if key not in newest or age > newest[key][0]:
                newest[key] = (age, f, trip)
    return [(f, trip) for _, f, trip in sorted(newest.values(), key=lambda x: x[2].get("title", ""))]


def pick_trip(query, roots):
    if query and Path(query).is_file():
        return [(Path(query), json.loads(Path(query).read_text()))]
    trips = trips_in(roots)
    if not query:
        return trips
    q = query.strip().lower()
    return [(f, t) for f, t in trips if all(w in " ".join([t.get("title", ""), t.get("id", ""), f.name]).lower() for w in q.split())]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--trip", help="title, id or file name words, or a path")
    ap.add_argument("--stay", help="place name or words, or a number from the stay list")
    ap.add_argument("--dir", action="append", help="where to look instead of the default folders")
    args = ap.parse_args()
    roots = args.dir or ROOTS
    found = pick_trip(args.trip, roots)
    if len(found) > 1 and args.stay:  # no --trip: the trips with that stay
        found = [x for x in found if len(find(stays_of(x[1]), args.stay)) == 1] or found
    if len(found) != 1:
        every = trips_in(roots)
        if not every:
            print("No Traveler trip file found in " + ", ".join(roots) + ". Attach the trip's export (share icon in the app).")
        else:
            print(("Several trips match." if found else f"No trip matches “{args.trip}”.") + " The trips:\n"
                  + "\n".join(f"- {t.get('title')} (r{t.get('revision')}, {f})" for f, t in every))
        sys.exit(2)
    path, trip = found[0]
    stays = stays_of(trip)
    match = find(stays, args.stay) if args.stay else []
    if len(match) != 1:
        print(f"{trip.get('title')} ({path}): " + ("several stays match." if match else f"no stay matches “{args.stay}”." if args.stay else "which stay?")
              + " The stays:\n" + listing(stays))
        sys.exit(2)
    print(f"(from {path})", file=sys.stderr)
    print(brief(trip, match[0]))


if __name__ == "__main__":
    main()
