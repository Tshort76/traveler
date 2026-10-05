#!/usr/bin/env python3
"""Generate the derived example trips from the hand-written one.

    python3 tools/make_fixtures.py

Writes, under schema/examples/:
  iguazu-short.r2.trip.json  a revision of iguazu-short that the app's merge tests
                             and the acceptance walkthrough import on top of user edits
  long-synthetic.trip.json   a four-month, 14-stay, ~350-activity trip for checking the
                             app stays usable at size. Synthetic: names are placeholders.

Deterministic, so re-running produces byte-identical files.
"""
import copy
import json
from datetime import date, timedelta
from pathlib import Path

EX = Path(__file__).resolve().parent.parent / "schema" / "examples"


def write(name, trip):
    (EX / name).write_text(json.dumps(trip, ensure_ascii=False, indent=2) + "\n")
    print(f"wrote schema/examples/{name}")


def by_id(items, id_):
    return next(x for x in items if x.get("id") == id_)


def day(trip, d):
    return next(x for x in trip["days"] if x["date"] == d)


def revision_two(r1):
    """What an assistant sends back after "the boat moved and the island is closed"."""
    r2 = copy.deepcopy(r1)
    r2["revision"] = 2
    r2["generatedAt"] = "2026-10-20T15:00:00Z"
    # A booking changed time: the app must apply it, not silently move anything else.
    by_id(r2["commitments"], "boat-gran-aventura").update(start="10:30", end="12:00")
    sunday = day(r2, "2026-11-08")
    sunday["note"] = "Boat at 10:30 first, then the Garganta del Diablo and the Upper Circuit; Lower Circuit to finish."
    for item in sunday["plan"]:
        if item["activityId"] == "gran-aventura":
            item.update(slot="morning", time="10:30")
        if item["activityId"] == "garganta-del-diablo":
            item.update(slot="afternoon", time="13:00")
    # The island is closed this season: dropped from the suggestions.
    r2["activities"] = [a for a in r2["activities"] if a["id"] != "isla-san-martin"]
    r2["activities"].append({
        "id": "itaipu-dam", "stayId": "iguazu", "name": "Itaipu Dam panoramic tour", "tag": "🏛️",
        "short": "Bus tour of one of the world's largest dams",
        "detail": ["A guided bus tour across the dam on the Brazil–Paraguay border, from the Brazilian visitor center north of Foz do Iguaçu."],
        "rank": 8, "stars": 2,
        "place": {"query": "Itaipu Binacional visitor center, Foz do Iguaçu", "lat": -25.4080, "lng": -54.5888},
        "duration": {"minutes": 180, "includesTravel": True}, "fit": "half-day", "effort": "easy",
        "conditions": ["free-afternoon", "rainy-day"],
        "practical": {"access": "Passport for the border; tours sell out on weekends."},
        "confidence": "check",
    })
    # Revised wording of an existing suggestion.
    by_id(r2["activities"], "garganta-del-diablo")["detail"].append(
        "Since the boat moved to the morning, go after lunch instead; the afternoon light on the spray is better anyway.")
    # Monday's plan rewritten — the merge test edits Monday's note locally to create the conflict.
    monday = day(r2, "2026-11-09")
    monday["note"] = "Work; at 3 pm the hummingbird garden, then dinner at the Feirinha."
    monday["plan"] = [{"activityId": "jardin-picaflores", "slot": "afternoon"},
                      {"activityId": "feirinha", "slot": "evening"}]
    by_id(r2["stays"], "iguazu")["lodging"] = {
        "name": "Hotel in the town center", "status": "booked", "ref": "Confirmation in email",
        "notes": "Walking distance to the bus terminal."}
    return r2


CITIES = [  # (id, name, region, lat, lng, tz, nights)
    ("lima", "Lima", "Peru", -12.0464, -77.0428, "America/Lima", 7),
    ("cusco", "Cusco", "Peru", -13.5320, -71.9675, "America/Lima", 10),
    ("arequipa", "Arequipa", "Peru", -16.4090, -71.5375, "America/Lima", 7),
    ("la-paz", "La Paz", "Bolivia", -16.4897, -68.1193, "America/La_Paz", 6),
    ("santiago", "Santiago", "Chile", -33.4489, -70.6693, "America/Santiago", 10),
    ("valparaiso", "Valparaíso", "Chile", -33.0472, -71.6127, "America/Santiago", 5),
    ("mendoza", "Mendoza", "Argentina", -32.8895, -68.8458, "America/Argentina/Mendoza", 8),
    ("bariloche", "Bariloche", "Argentina", -41.1335, -71.3103, "America/Argentina/Salta", 12),
    ("buenos-aires", "Buenos Aires", "Argentina", -34.6037, -58.3816, "America/Argentina/Buenos_Aires", 10),
    ("montevideo", "Montevideo", "Uruguay", -34.9011, -56.1645, "America/Montevideo", 7),
    ("florianopolis", "Florianópolis", "Brazil", -27.5954, -48.5480, "America/Sao_Paulo", 9),
    ("sao-paulo", "São Paulo", "Brazil", -23.5505, -46.6333, "America/Sao_Paulo", 8),
    ("rio", "Rio de Janeiro", "Brazil", -22.9068, -43.1729, "America/Sao_Paulo", 10),
    ("lima-2", "Lima", "Peru", -12.0464, -77.0428, "America/Lima", 3),
]
TAGS = ["🌿", "☕", "🍽️", "🎨", "📸", "🏃", "🚶", "🏛️", "🏡", "🌊", "🎭", "🏔️"]
FITS = ["short", "half-day", "full-day", "evening"]
EFFORTS = ["easy", "moderate", "hard"]
CONDITIONS = [[], ["rainy-day"], ["short-morning"], ["free-afternoon"], []]


def long_trip():
    start = date(2027, 1, 4)
    trip = {
        "format": "traveler-trip", "formatVersion": 1, "id": "long-synthetic", "revision": 1,
        "generatedAt": "2026-10-03T18:00:00Z", "generatedBy": "tools/make_fixtures.py",
        "title": "Synthetic long trip (size fixture)",
        "summary": "Four months across South America with placeholder activities, for checking the app at size.",
        "startDate": start.isoformat(), "stays": [], "transfers": [], "activities": [], "commitments": [], "days": [],
    }
    d = start
    for n, (sid, name, region, lat, lng, tz, nights) in enumerate(CITIES):
        stay = {"id": sid, "name": name, "region": region, "place": {"name": name, "lat": lat, "lng": lng},
                "arrive": d.isoformat(), "depart": (d + timedelta(days=nights)).isoformat(), "timezone": tz,
                "summary": f"{nights} nights in {name}. Placeholder summary for the size fixture.",
                "priorities": [f"{name} sight 1", f"{name} sight 2"],
                "lodging": {"status": "booked" if n % 3 else "undecided"},
                "workRhythm": {"days": ["mon", "tue", "wed", "thu", "fri"], "start": "08:00", "end": "15:00",
                               "timezone": "America/Denver"}}
        trip["stays"].append(stay)
        count = 15 + nights * 1
        ids = []
        for i in range(count):
            aid = f"{sid}-a{i + 1:02d}"
            ids.append(aid)
            trip["activities"].append({
                "id": aid, "stayId": sid, "name": f"{name} sight {i + 1}", "tag": TAGS[(i + n) % len(TAGS)],
                "short": f"Placeholder activity {i + 1} in {name}",
                "detail": [f"Synthetic description for {name} sight {i + 1}."],
                "rank": i + 1, "place": {"query": f"{name} {i + 1}", "lat": round(lat + (i % 7 - 3) * 0.01, 4),
                                         "lng": round(lng + (i % 5 - 2) * 0.01, 4)},
                "duration": {"minutes": 45 + (i % 6) * 30}, "fit": FITS[i % 4], "effort": EFFORTS[i % 3],
                "conditions": CONDITIONS[i % 5], "confidence": ["confirmed", "estimate", "check"][i % 3]})
        for k in range(nights):
            dd = d + timedelta(days=k)
            weekend = dd.weekday() >= 5
            plan = []
            if weekend:
                plan = [{"activityId": ids[(2 * k) % count], "slot": "morning"},
                        {"activityId": ids[(2 * k + 1) % count], "slot": "afternoon"},
                        {"activityId": ids[(2 * k + 2) % count], "slot": "evening"}]
            else:
                plan = [{"activityId": ids[k % count], "slot": "evening"}]
            trip["days"].append({"date": dd.isoformat(), "stayId": sid, "kind": "plan" if weekend else "work",
                                 "note": f"Placeholder plan for {name}, day {k + 1}.", "plan": plan})
        if n + 1 < len(CITIES):
            nxt = CITIES[n + 1][0]
            dep = d + timedelta(days=nights)
            trip["transfers"].append({"id": f"t-{sid}-{nxt}", "from": sid, "to": nxt, "date": dep.isoformat(),
                                      "mode": "flight" if n % 2 == 0 else "bus"})
        trip["commitments"].append({"id": f"c-{sid}", "title": f"Booked tour in {name}", "date": (d + timedelta(days=1)).isoformat(),
                                    "start": "16:00", "end": "18:00", "kind": "booking", "booked": True,
                                    "stayId": sid, "activityId": ids[0]})
        d += timedelta(days=nights)
    trip["endDate"] = d.isoformat()
    last = CITIES[-1][0]
    trip["days"].append({"date": d.isoformat(), "stayId": last, "kind": "travel", "note": "Fly home."})
    return trip


def main():
    r1 = json.loads((EX / "iguazu-short.trip.json").read_text())
    write("iguazu-short.r2.trip.json", revision_two(r1))
    write("long-synthetic.trip.json", long_trip())


if __name__ == "__main__":
    main()
