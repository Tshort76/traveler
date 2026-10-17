#!/usr/bin/env python3
"""Builds build/skill/: the traveler-trip skill as files for a ChatGPT Project, and a plugin wrapping it.

Each SKILL.md is a preamble from integrations/skill/ (front matter, and for traveler-trip the
working rules) followed by the matching prompt from prompts/, minus its usage comment, so the
skills and the app's "Copy instructions" buttons work from the same text. Skills follow the open
Agent Skills format.

ChatGPT refuses uploaded skills on personal plans outside its desktop app, so chatgpt-project/
holds the same text as flat files for a ChatGPT Project, which works on any plan.
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "skill"
SKILLS = {
    "traveler-trip": {
        "preamble": "preamble.md",
        "prompt": "itinerary-instructions.md",
        "display": ("Traveler Trip Builder", "Plans a trip as a file for the Traveler app"),
        "files": {
            "scripts/validate_trip.py": "tools/validate_trip.py",
            "scripts/trip.schema.json": "schema/trip.schema.json",
            "references/trip-format.md": "docs/trip-format.md",
            "references/demo.trip.json": "schema/examples/demo.trip.json",
            "lodging-search.md": "integrations/skill/lodging-search.md",
            "travel-focus.md": "integrations/skill/travel-focus.md",
            "scripts/stay_brief.py": "tools/stay_brief.py",
        },
    },
}
AUTHOR = "Tshort76"
PLUGIN = {
    "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
    "name": "traveler",
    "version": "1.1.0",
    "description": "Plans and revises trips as files for the Traveler Android app.",
    "author": {"name": AUTHOR},
    "extensions": {"com.openai": {"interface": {
        "displayName": "Traveler",
        "shortDescription": "Trip files for the Traveler app",
        "longDescription": "Plans a trip as a file for the Traveler Android app and revises an exported trip without "
                           "losing the traveler's edits.",
        "developerName": AUTHOR,
        "category": "Productivity",
    }}},
}
PROJECT_INSTRUCTIONS = """You plan my trips. This project's files hold the instructions.

- A trip, an itinerary, or a .trip.json file I send: first print traveler-trip.md in full with Python and follow it exactly; searching it for snippets misses rules. Build the file with code, copy validate_trip.py and trip.schema.json into one folder, and run `python3 validate_trip.py --stamp <file>` until it prints "OK and stamped". Hand over that file unchanged (the app checks the stamp) and end your reply with that OK line, copied exactly; never hand over a file the validator hasn't passed.

- Finding or comparing lodging (hotels, hostels, apartments, Airbnbs, places to stay): print lodging-search.md and the "Lodging searches" section of traveler-trip.md in full and follow them. With a .trip.json attached, hand back a validated revision that adds only lodging.options and lodging.searched to the stays searched; without one, the comparison table in chat.

Every trip file must have, or the validator fails:
- `stars` (1-3) on every activity: 3 must do, 2 I'll likely enjoy, 1 worth it with time to spare.
- A `booking` object {status: "needed", bookDaysBefore, price, url, how} on every activity needing a ticket or reservation, and on every flight, train, bus or boat. Never only a sentence in practical.booking.
- `price` and `bookDaysBefore` on lodging that isn't booked. Prices in USD (approximate), the local price in `note`; a price only inside lodging, booking or a commitment, never on the activity itself. Lodging gives the nightly rate with `unit: "night"`; a per-traveler fare or ticket has `unit: "person"`, with `travelers` set at the top. The app multiplies; never add other fields such as `per`.
- Flights from home are transfers from and to the home-airport stay (zero nights, first and last), never commitments. A return ticket is priced once, on the outbound leg; the return leg gets `amount: 0`. `bookDaysBefore`: days before the date to book by, as traveler-trip.md sets out. Never `priority`.
- `place: {query, lat, lng}` on every activity, with real coordinates; the stay map is drawn from them.
- A tour's or venue's link in that activity's `url`, never in the trip's `links`.

- `/travel <place>` (or "travel <place>"), usually starting a new chat with a .trip.json attached: print travel-focus.md in full and follow it. That chat researches one stay; it writes no trip file unless I ask.

If a profile of me is among the files, follow it.
"""


def build_skill(name: str, dest: Path):
    spec = SKILLS[name]
    prompt = (ROOT / "prompts" / spec["prompt"]).read_text()
    body = re.sub(r"\A\s*<!--.*?-->\s*", "", prompt, flags=re.S)
    dest.mkdir(parents=True)
    (dest / "SKILL.md").write_text((ROOT / "integrations" / "skill" / spec["preamble"]).read_text() + body)
    (dest / "agents").mkdir()
    title, short = spec["display"]
    (dest / "agents" / "openai.yaml").write_text(f'interface:\n  display_name: "{title}"\n  short_description: "{short}"\n')
    for to, src in spec["files"].items():
        (dest / to).parent.mkdir(exist_ok=True)
        shutil.copy(ROOT / src, dest / to)


def build_project(dest: Path, skills: Path):
    """The skills flattened into ChatGPT Project files: one folder, no subfolders."""
    dest.mkdir(parents=True)
    (dest / "INSTRUCTIONS.txt").write_text(PROJECT_INSTRUCTIONS)
    for name, spec in SKILLS.items():
        text = (skills / name / "SKILL.md").read_text()
        body = re.sub(r"\A---.*?---\s*", "", text, flags=re.S)
        for old in ("scripts/", "references/", " from this skill's folder"):
            body = body.replace(old, "")
        (dest / f"{name}.md").write_text(body)
        for to, src in spec["files"].items():
            shutil.copy(ROOT / src, dest / Path(to).name)


def zip_dir(src: Path, zip_path: Path):
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(src.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(src).as_posix())


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    plugin = OUT / "traveler-plugin"
    for name in SKILLS:
        build_skill(name, plugin / "skills" / name)
    (plugin / "plugin.json").write_text(json.dumps(PLUGIN, indent=2) + "\n")
    zip_dir(plugin, OUT / "traveler-plugin.zip")
    build_project(OUT / "chatgpt-project", plugin / "skills")
    for p in sorted(OUT.iterdir()):
        print(p.relative_to(ROOT))


if __name__ == "__main__":
    main()
