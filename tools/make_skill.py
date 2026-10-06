#!/usr/bin/env python3
"""Builds build/skill/: the traveler-trip and trip-workbook skills for ChatGPT and Claude, and a
plugin wrapping both.

Each SKILL.md is a preamble from integrations/skill/ (front matter, and for traveler-trip the
working rules) followed by the matching prompt from prompts/, minus its usage comment, so the
skills and the app's "Copy instructions" buttons work from the same text. Skills follow the open
Agent Skills format, which ChatGPT and Claude both read; each zip holds one top-level skill folder.

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
        },
    },
    "trip-workbook": {
        "preamble": "workbook-preamble.md",
        "prompt": "workbook-instructions.md",
        "display": ("Trip Planning Workbook", "Drafts a trip's route as a spreadsheet and My Maps file"),
        "files": {},
    },
}
AUTHOR = "Tshort76"
PLUGIN = {
    "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
    "name": "traveler",
    "version": "1.0.1",
    "description": "Plans and revises trips as files for the Traveler Android app, with a planning spreadsheet and map.",
    "author": {"name": AUTHOR},
    "extensions": {"com.openai": {"interface": {
        "displayName": "Traveler",
        "shortDescription": "Trip files, sheets and maps",
        "longDescription": "Plans a trip as a file for the Traveler Android app, revises an exported trip without losing "
                           "the traveler's edits, and builds a planning spreadsheet and a Google My Maps file.",
        "developerName": AUTHOR,
        "category": "Productivity",
    }}},
}
PROJECT_INSTRUCTIONS = """You plan my trips. This project's files hold the instructions.

- A trip, an itinerary, or a .trip.json file I send: first print traveler-trip.md in full with Python and follow it exactly; searching it for snippets misses rules. Build the file with code, copy validate_trip.py and trip.schema.json into one folder, and run `python3 validate_trip.py --stamp <file>` until it prints "OK and stamped". Hand over that file unchanged (the app checks the stamp) and end your reply with that OK line, copied exactly; never hand over a file the validator hasn't passed.
- A planning spreadsheet, a route or budget sheet, or a trip map: follow trip-workbook.md. It drafts the route (stays, dates, travel between them); once I've settled it, its Stays tab is the plan the trip file follows.

Every trip file must have, or the validator fails:
- `stars` (1-3) on every activity: 3 must do, 2 I'll likely enjoy, 1 worth it with time to spare.
- A `booking` object {status: "needed", priority, price, url, how} on every activity needing a ticket or reservation, and on every flight, train, bus or boat. Never only a sentence in practical.booking.
- `price` and `priority` on lodging that isn't booked. Prices in USD (approximate), the local price in `note`; a price only inside lodging, booking or a commitment, never on the activity itself. Lodging gives the nightly rate with `unit: "night"`; a per-traveler fare or ticket has `unit: "person"`, with `travelers` set at the top. The app multiplies; never add other fields such as `per`.
- Flights from home are transfers from and to the home-airport stay (zero nights, first and last), never commitments. A return ticket is priced once, on the outbound leg; the return leg gets `amount: 0`. Priority: 1 book now (sells out, or airfare about to jump), 2 a week or more ahead, 3 fine last minute.
- A tour's or venue's link in that activity's `url`, never in the trip's `links`.

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


def build_project(dest: Path):
    """The skills flattened into ChatGPT Project files: one folder, no subfolders."""
    dest.mkdir(parents=True)
    (dest / "INSTRUCTIONS.txt").write_text(PROJECT_INSTRUCTIONS)
    for name, spec in SKILLS.items():
        text = (OUT / name / "SKILL.md").read_text()
        body = re.sub(r"\A---.*?---\s*", "", text, flags=re.S)
        for old in ("scripts/", "references/", " from this skill's folder"):
            body = body.replace(old, "")
        (dest / f"{name}.md").write_text(body)
        for to, src in spec["files"].items():
            shutil.copy(ROOT / src, dest / Path(to).name)


def zip_dir(src: Path, zip_path: Path, prefix: str = ""):
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(src.rglob("*")):
            if f.is_file():
                z.write(f, prefix + f.relative_to(src).as_posix())


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    plugin = OUT / "traveler-plugin"
    for name in SKILLS:
        skill = OUT / name
        build_skill(name, skill)
        build_skill(name, plugin / "skills" / name)
        zip_dir(skill, OUT / f"{name}.zip", prefix=f"{name}/")
    (plugin / "plugin.json").write_text(json.dumps(PLUGIN, indent=2) + "\n")
    zip_dir(plugin, OUT / "traveler-plugin.zip")
    build_project(OUT / "chatgpt-project")
    for p in sorted(OUT.iterdir()):
        print(p.relative_to(ROOT))


if __name__ == "__main__":
    main()
