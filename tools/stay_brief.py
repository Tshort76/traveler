#!/usr/bin/env python3
"""Prints one stay of a Traveler trip file as the opening of a focused chat: its dates, lodging,
flights in and out, and standout (3-star) activities.

    python3 stay_brief.py <trip.json> <place>     # "Puerto Madryn", "madryn", or a number from the list

With no match, or more than one, it prints the stays numbered and exits 2, so the caller can ask
which one is meant and run it again with that number.
"""
import json
import sys
from datetime import date

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


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    trip = json.load(open(sys.argv[1]))
    stays = stays_of(trip)
    found = find(stays, sys.argv[2])
    if len(found) != 1:
        print(("Several stays match" if found else f"No stay matches “{sys.argv[2]}”") + ". The stays:\n" + listing(stays))
        sys.exit(2)
    print(brief(trip, found[0]))


if __name__ == "__main__":
    main()
