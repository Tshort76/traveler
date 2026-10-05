#!/usr/bin/env python3
"""Validate a Traveler trip file (traveler-trip v1).

Usage:
    python3 validate_trip.py trip.json [more.json ...]
    python3 validate_trip.py --complete trip.json   # also require stars, prices, priorities and booking objects

Prints OK with a one-line summary, or each problem as ERROR / WARNING.
Exits 1 when any file has an error. Errors stop the app importing the file;
warnings are shown on the import preview but do not block it.

Standard library only, so it runs anywhere an assistant can run Python. It
reads trip.schema.json from beside this script or from ../schema/, which is
the one copy of the structural rules; the cross-reference checks (ids exist,
dates line up, time zones are real) live below because JSON Schema cannot say
them.
"""
import json
import re
import sys
from datetime import date
from pathlib import Path

try:
    from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
except ImportError:  # Python < 3.9: skip the time-zone check rather than fail
    ZoneInfo = None


def load_schema():
    here = Path(__file__).resolve().parent
    for candidate in (here / "trip.schema.json", here.parent / "schema" / "trip.schema.json"):
        if candidate.exists():
            return json.loads(candidate.read_text())
    sys.exit("validate_trip.py: cannot find trip.schema.json beside the script or in ../schema/")


class Report:
    def __init__(self):
        self.errors, self.warnings = [], []

    def error(self, where, msg):
        self.errors.append(f"{where}: {msg}" if where else msg)

    def warn(self, where, msg):
        self.warnings.append(f"{where}: {msg}" if where else msg)


# --- structural check: the subset of JSON Schema that trip.schema.json uses ---

def _type_ok(value, t):
    return {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
    }[t]


def check_schema(value, schema, root, path, rep):
    if "$ref" in schema:
        name = schema["$ref"].rsplit("/", 1)[-1]
        return check_schema(value, root["$defs"][name], root, path, rep)
    if "oneOf" in schema:
        trial = [Report() for _ in schema["oneOf"]]
        for sub, r in zip(schema["oneOf"], trial):
            check_schema(value, sub, root, path, r)
        if not any(not r.errors for r in trial):
            rep.error(path, "has the wrong type")
        return
    if "const" in schema and value != schema["const"]:
        rep.error(path, f"must be {json.dumps(schema['const'])}, got {json.dumps(value)}")
        return
    if "enum" in schema and value not in schema["enum"]:
        rep.error(path, f"must be one of {', '.join(map(str, schema['enum']))}; got {json.dumps(value)}")
        return
    t = schema.get("type")
    if t and not _type_ok(value, t):
        rep.error(path, f"must be {'an' if t[0] in 'aeiou' else 'a'} {t}")
        return
    if isinstance(value, str):
        if "pattern" in schema and not re.search(schema["pattern"], value):
            rep.error(path, f"{json.dumps(value)} is not in the expected form ({_pattern_hint(schema)})")
        if len(value) < schema.get("minLength", 0):
            rep.error(path, "must not be empty")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            rep.error(path, f"must be at least {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            rep.error(path, f"must be at most {schema['maximum']}")
    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            rep.error(path, f"needs at least {schema['minItems']} item(s)")
        if "items" in schema:
            for i, item in enumerate(value):
                check_schema(item, schema["items"], root, f"{path}[{i}]", rep)
    if isinstance(value, dict):
        for k in schema.get("required", []):
            if k not in value:
                rep.error(path, f"missing required field '{k}'")
        props = schema.get("properties", {})
        for k, v in value.items():
            if k in props:
                check_schema(v, props[k], root, f"{path}.{k}" if path else k, rep)
            elif props:
                rep.warn(f"{path}.{k}" if path else k, "unknown field (typo?) — it will be ignored")


def _prices(value, path=""):
    """Every `price` object in the trip, with its path."""
    if isinstance(value, dict):
        for k, v in value.items():
            where = f"{path}.{k}" if path else k
            if k == "price" and isinstance(v, dict):
                yield where, v
            else:
                yield from _prices(v, where)
    elif isinstance(value, list):
        for i, v in enumerate(value):
            yield from _prices(v, f"{path}[{i}]")


def _pattern_hint(schema):
    p = schema["pattern"]
    if "\\d{4}" in p:
        return "YYYY-MM-DD"
    if "[0-5]" in p:
        return "HH:MM, 24-hour"
    if "a-z0-9" in p:
        return "lower-case slug: letters, digits, '-', '_', '.'"
    return p



def _date(s):
    try:
        return date.fromisoformat(s)
    except (TypeError, ValueError):
        return None


def _minutes(hhmm):
    try:
        h, m = hhmm.split(":")
        return int(h) * 60 + int(m)
    except (AttributeError, ValueError):
        return None


def _unique_ids(items, label, rep):
    seen = set()
    for i, it in enumerate(items):
        if not isinstance(it, dict) or "id" not in it:
            continue
        if it["id"] in seen:
            rep.error(f"{label}[{i}]", f"duplicate id '{it['id']}'")
        seen.add(it["id"])
    return seen


def check_references(trip, rep):
    def lst(key):
        v = trip.get(key)
        return [x for x in v if isinstance(x, dict)] if isinstance(v, list) else []

    stays, transfers, acts, commits, days = (lst(k) for k in ("stays", "transfers", "activities", "commitments", "days"))
    stay_ids = _unique_ids(stays, "stays", rep)
    _unique_ids(transfers, "transfers", rep)
    act_ids = _unique_ids(acts, "activities", rep)
    _unique_ids(commits, "commitments", rep)

    start, end = _date(trip.get("startDate")), _date(trip.get("endDate"))
    if start and end and end < start:
        rep.error("endDate", "is before startDate")

    def in_trip(d):
        return not (start and end and d) or start <= d <= end

    prev_arrive = None
    for i, s in enumerate(stays):
        where = f"stays[{i}] ({s.get('id', '?')})"
        a, d = _date(s.get("arrive")), _date(s.get("depart"))
        if a and d:
            if d < a:
                rep.error(where, "depart is before arrive")
            if not (in_trip(a) and in_trip(d)):
                rep.warn(where, "dates fall outside the trip's startDate–endDate")
            if prev_arrive and a < prev_arrive:
                rep.warn(where, "stays should be listed in visit order")
            prev_arrive = a
        tz = s.get("timezone")
        if not tz:
            rep.warn(where, "no timezone — times will be shown in the phone's zone")
        else:
            _check_tz(tz, where, rep)
        wr = s.get("workRhythm")
        if isinstance(wr, dict):
            if wr.get("timezone"):
                _check_tz(wr["timezone"], f"{where}.workRhythm", rep)
            if (_minutes(wr.get("end")) or 0) <= (_minutes(wr.get("start")) or 0):
                rep.warn(f"{where}.workRhythm", "end is not after start")
        place = s.get("place")
        if not (isinstance(place, dict) and "lat" in place and "lng" in place):
            rep.warn(where, "no place.lat/lng — this stay cannot appear on the overview map")

    for i, t in enumerate(transfers):
        for k in ("from", "to"):
            if t.get(k) and t[k] not in stay_ids:
                rep.error(f"transfers[{i}].{k}", f"unknown stay '{t[k]}'")

    for i, a in enumerate(acts):
        if a.get("stayId") and a["stayId"] not in stay_ids:
            rep.error(f"activities[{i}] ({a.get('id', '?')}).stayId", f"unknown stay '{a['stayId']}'")
        dur = a.get("duration")
        if isinstance(dur, dict) and isinstance(dur.get("maxMinutes"), int) and isinstance(dur.get("minutes"), int):
            if dur["maxMinutes"] < dur["minutes"]:
                rep.warn(f"activities[{i}].duration", "maxMinutes is below minutes")

    for where, price in _prices(trip):
        if isinstance(price.get("amount"), (int, float)) and isinstance(price.get("max"), (int, float)) and price["max"] < price["amount"]:
            rep.error(where, "max is below amount")

    for i, c in enumerate(commits):
        where = f"commitments[{i}] ({c.get('id', '?')})"
        if c.get("stayId") and c["stayId"] not in stay_ids:
            rep.error(f"{where}.stayId", f"unknown stay '{c['stayId']}'")
        if c.get("activityId") and c["activityId"] not in act_ids:
            rep.error(f"{where}.activityId", f"unknown activity '{c['activityId']}'")
        d = _date(c.get("date"))
        if d and not in_trip(d):
            rep.warn(where, "date is outside the trip")
        s, e = _minutes(c.get("start")), _minutes(c.get("end"))
        if s is not None and e is not None and e <= s:
            rep.warn(where, "end is not after start")

    seen_dates = set()
    for i, day in enumerate(days):
        where = f"days[{i}] ({day.get('date', '?')})"
        d = _date(day.get("date"))
        if d:
            if d in seen_dates:
                rep.error(where, "a second entry for the same date")
            seen_dates.add(d)
            if not in_trip(d):
                rep.warn(where, "date is outside the trip")
        if day.get("stayId") and day["stayId"] not in stay_ids:
            rep.error(f"{where}.stayId", f"unknown stay '{day['stayId']}'")
        plan = day.get("plan") if isinstance(day.get("plan"), list) else []
        for j, item in enumerate(plan):
            if not isinstance(item, dict):
                continue
            aid = item.get("activityId")
            if aid and aid not in act_ids:
                rep.error(f"{where}.plan[{j}]", f"unknown activity '{aid}'")

    planned = {item.get("activityId") for day in days for item in (day.get("plan") or []) if isinstance(item, dict)}
    for s in stays:
        a, d = _date(s.get("arrive")), _date(s.get("depart"))
        if a and d and (d - a).days > 2:
            pool = [x for x in acts if x.get("stayId") == s.get("id") and x.get("id") not in planned]
            if len(pool) < 3:
                rep.warn(f"stay {s.get('id')}", f"{(d - a).days} nights but only {len(pool)} unscheduled "
                                                f"alternative(s) — give longer stays a real pool")
    return {
        "stays": len(stays), "days": len(days), "activities": len(acts),
        "planned": len(planned & act_ids), "commitments": len(commits),
    }


def _check_tz(tz, where, rep):
    if ZoneInfo is None:
        return
    try:
        ZoneInfo(tz)
    except (ZoneInfoNotFoundError, ValueError):
        rep.error(where, f"'{tz}' is not an IANA time zone (e.g. America/Argentina/Buenos_Aires)")


def validate(trip, schema=None):
    """Return (report, summary) for an already-parsed trip document."""
    rep = Report()
    if not isinstance(trip, dict):
        rep.error("", "the file must contain one JSON object (the trip)")
        return rep, None
    schema = schema or load_schema()
    check_schema(trip, schema, schema, "", rep)
    summary = check_references(trip, rep)
    return rep, summary


TICKETED = {"flight", "train", "bus", "boat", "ferry"}


def _names(items, limit=6):
    shown = ", ".join(items[:limit])
    return shown + (f" and {len(items) - limit} more" if len(items) > limit else "")


def check_complete(trip, rep):
    """--complete: the planning data the app's Bookings and stars need, which an assistant tends to skip."""
    # Strict: a field the format doesn't define is silently dropped by the app (a price on the activity
    # itself instead of in its booking, a "per": "night"), so here it fails rather than warns.
    strays = [w for w in rep.warnings if "unknown field" in w]
    rep.warnings = [w for w in rep.warnings if w not in strays]
    for w in strays:
        rep.error(None, w.replace("unknown field (typo?) — it will be ignored",
                                  "not part of the format; the app drops it. Use the field trip-format.md defines"))
    for where, p in _prices(trip):
        if p.get("currency", "USD") != "USD":
            rep.error(where, f"prices are in USD, as an approximate conversion; got {p.get('currency')}. "
                             "Put the local price in its note")
    acts = [a for a in trip.get("activities", []) if a.get("origin") != "user"]
    no_stars = [a.get("id", "?") for a in acts if "stars" not in a]
    if no_stars:
        rep.error("activities", f"{len(no_stars)} have no stars (1–3): {_names(no_stars)}")
    loose = [a.get("id", "?") for a in acts if (a.get("practical") or {}).get("booking") and "booking" not in a]
    if loose:
        rep.error("activities", f"booking advice only in practical.booking, with no booking object "
                                f"{{status, priority, price, url?, how}}: {_names(loose)}")
    for s in trip.get("stays", []):
        lod = s.get("lodging") or {}
        if _date(s.get("arrive")) and _date(s.get("depart")) and _date(s["depart"]) > _date(s["arrive"]) \
                and lod.get("status") != "booked":
            missing = [k for k in ("price", "priority") if k not in lod]
            if missing:
                rep.error(f"stay {s.get('id')}.lodging", f"needs {' and '.join(missing)}")
    for t in trip.get("transfers", []):
        b = t.get("booking")
        if b is None and t.get("mode") in TICKETED:
            rep.error(f"transfer {t.get('id')}", "a ticketed transfer needs a booking {status, priority, price, how}")
        elif b is not None and b.get("status") != "booked":
            missing = [k for k in ("price", "priority") if k not in b]
            if missing:
                rep.error(f"transfer {t.get('id')}.booking", f"needs {' and '.join(missing)}")
    booked = {c.get("activityId") for c in trip.get("commitments", []) if c.get("booked")}
    for a in acts:
        b = a.get("booking")
        if b is not None and b.get("status") != "booked" and a.get("id") not in booked:
            missing = [k for k in ("price", "priority") if k not in b]
            if missing:
                rep.error(f"activity {a.get('id')}.booking", f"needs {' and '.join(missing)}")


def strip_fences(text):
    """Accept JSON pasted with a ```json fence around it, as chat UIs produce."""
    m = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.S)
    return m.group(1) if m else text


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    complete = "--complete" in argv
    schema = load_schema()
    failed = False
    for name in [a for a in argv[1:] if a != "--complete"]:
        try:
            trip = json.loads(strip_fences(Path(name).read_text()))
        except (OSError, json.JSONDecodeError) as e:
            print(f"{name}: ERROR: cannot read as JSON — {e}")
            failed = True
            continue
        rep, s = validate(trip, schema)
        if complete:
            check_complete(trip, rep)
        for e in rep.errors:
            print(f"{name}: ERROR: {e}")
        for w in rep.warnings:
            print(f"{name}: WARNING: {w}")
        if rep.errors:
            failed = True
            print(f"{name}: INVALID — {len(rep.errors)} error(s); fix them and re-run")
        else:
            print(f"{name}: OK — '{trip.get('title')}' r{trip.get('revision')}: {s['stays']} stays, "
                  f"{s['days']} days, {s['activities']} activities ({s['planned']} planned), "
                  f"{s['commitments']} commitments, {len(rep.warnings)} warning(s)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
