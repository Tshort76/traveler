#!/usr/bin/env python3
"""Prints one stay of a Traveler trip file as context for a focused chat: dates, getting there and
away, lodging, work hours, weather, and the activities and bookings it already has.

    python3 stay_brief.py <trip.json> <place>     # "Puerto Madryn", "madryn", or a number from the list

With no match, or more than one, it prints the stays numbered and exits 2, so the caller can ask
which one is meant and run it again with that number.
"""
import json
import sys
from datetime import date

STATUS = {"done": "✓ done", "skipped": "⨯ skipped"}


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


def money(p):
    if not p:
        return ""
    cur = p.get("currency", "USD")
    sign = "$" if cur == "USD" else cur + " "
    amount = f"{sign}{p['amount']:,.0f}" + (f"–{p['max']:,.0f}" if p.get("max") else "")
    return amount + {"night": "/night", "person": " per person"}.get(p.get("unit"), "")


def stars(a):
    return "★" * a.get("stars", 0)


def listing(stays):
    return "\n".join(f"{i}. {s['name']} · {day(s['arrive'])} – {day(s['depart'])}" for i, s in enumerate(stays, 1))


def brief(trip, s):
    names = {x["id"]: x["name"] for x in trip.get("stays", [])}
    nights = (date.fromisoformat(s["depart"]) - date.fromisoformat(s["arrive"])).days
    out = [f"{s['name']}" + (f", {s['region']}" if s.get("region") else ""),
           f"{day(s['arrive'])} – {day(s['depart'])} {s['depart'][:4]} · {nights} night{'s' * (nights != 1)} · {s.get('timezone', '')}".rstrip(" ·")]
    if s.get("summary"):
        out.append(s["summary"])
    for t in trip.get("transfers", []):
        if s["id"] not in (t.get("to"), t.get("from")):
            continue
        way = f"from {names.get(t['from'], t['from'])}" if t.get("to") == s["id"] else f"to {names.get(t['to'], t['to'])}"
        times = "–".join(x for x in (t.get("depart"), t.get("arrive")) if x)
        booked = " · booked" if (t.get("booking") or {}).get("status") == "booked" else ""
        out.append(f"{'Arrive' if t.get('to') == s['id'] else 'Leave'}: {t.get('mode') if t.get('mode') not in (None, 'other') else 'transfer'} {way}, {day(t['date'])} {times}{booked}".rstrip())
    lod = s.get("lodging") or {}
    if lod:
        out.append("Lodging: " + " · ".join(x for x in (lod.get("name"), lod.get("status"), money(lod.get("price")),
                                                           f"{len(lod['options'])} options" if lod.get("options") else "") if x))
    w = s.get("workRhythm")
    if w:
        out.append(f"Work: {', '.join(w.get('days', [])) or 'every day'} {w['start']}–{w['end']}" + (f" ({w['timezone']})" if w.get("timezone") else ""))
    c = s.get("climate")
    if c:
        out.append(f"Weather: highs {c['highC']:.0f}°C / {c['highC'] * 9 / 5 + 32:.0f}°F, lows {c['lowC']:.0f}°C / {c['lowC'] * 9 / 5 + 32:.0f}°F"
                   + (f", {', '.join(c['flags'])}" if c.get("flags") else ""))
    if s.get("transport"):
        out.append(f"Getting around: {s['transport']}")

    acts = {a["id"]: a for a in trip.get("activities", []) if a.get("stayId") == s["id"]}
    booked = {c["activityId"] for c in trip.get("commitments", []) if c.get("booked") and c.get("activityId")}
    booked |= {i for i, a in acts.items() if (a.get("booking") or {}).get("status") == "booked"}
    planned, seen = [], set()
    for d in sorted(trip.get("days", []), key=lambda d: d["date"]):
        if (d.get("stayId") or s["id"]) != s["id"] or not s["arrive"] <= d["date"] <= s["depart"]:
            continue
        for item in d.get("plan", []):
            a = acts.get(item["activityId"]) or next((x for x in trip.get("activities", []) if x["id"] == item["activityId"]), None)
            if not a:
                continue
            seen.add(a["id"])
            when = item.get("time") or item.get("slot", "")
            marks = " ".join(x for x in (stars(a), "booked" if a["id"] in booked else "", STATUS.get(item.get("status"), "")) if x)
            planned.append(f"  {day(d['date'])}  {when:<9} {a.get('name')}  {marks}".rstrip())
    out += ["", "Planned:"] + (planned or ["  nothing yet"])
    rest = sorted((a for i, a in acts.items() if i not in seen), key=lambda a: (-a.get("stars", 0), a.get("rank", 99)))
    if rest:
        out += ["", "Not scheduled:"]
        out += [f"  {stars(a):<3} {a.get('name')}" + ("  (needs booking)" if (a.get("booking") or {}).get("status") == "needed" else "") for a in rest]
    loose = [c for c in trip.get("commitments", []) if c.get("stayId") == s["id"] and not c.get("activityId")]
    if loose:
        out += ["", "Other bookings:"]
        out += [f"  {day(c['date'])}  {c.get('start', ''):<5} {c['title']}" + ("  booked" if c.get("booked") else "") for c in loose]
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
