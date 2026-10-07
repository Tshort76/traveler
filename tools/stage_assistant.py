#!/usr/bin/env python3
"""Stages what changed in build/skill/ since the assistant files were last uploaded.

Neither upload can be scripted: ChatGPT has no API for Project files, and Claude's Skills API
uploads to an API workspace, which claude.ai never sees. So this narrows the manual step instead.
It compares the fresh build with build/assistant-uploaded.json (written by --done), copies only
the changed Project files to build/assistant-upload/, puts INSTRUCTIONS.txt on the clipboard when
it changed, and opens the folder and the pages to drop them on.

  make assistant        # build, stage the changes, open Finder and the browser
  make assistant-done   # after uploading: record this build as the uploaded one
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKILL = ROOT / "build" / "skill"
PROJECT = SKILL / "chatgpt-project"
STAGE = ROOT / "build" / "assistant-upload"
RECORD = ROOT / "build" / "assistant-uploaded.json"
INSTRUCTIONS = "INSTRUCTIONS.txt"
CLAUDE_SKILLS = "https://claude.ai/settings/capabilities"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def current() -> dict:
    project = {f.name: digest(f) for f in sorted(PROJECT.iterdir()) if f.is_file()}
    # The zip's bytes change on every build (file times), so the skill is hashed by its contents.
    tree = hashlib.sha256()
    for f in sorted((SKILL / "traveler-trip").rglob("*")):
        if f.is_file():
            tree.update(f.relative_to(SKILL).as_posix().encode() + b"\0" + f.read_bytes())
    return {"chatgpt-project": project, "traveler-trip": tree.hexdigest()}


def open_all(*targets: str):
    for t in targets:
        subprocess.run(["open", t], check=False)


def stage(no_open: bool):
    now = current()
    was = json.loads(RECORD.read_text()) if RECORD.exists() else {"chatgpt-project": {}}
    old = was["chatgpt-project"]
    changed = [n for n, h in now["chatgpt-project"].items() if old.get(n) != h]
    removed = sorted(set(old) - set(now["chatgpt-project"]))
    if not RECORD.exists():
        print("No upload recorded yet, so every file counts as changed.\n")

    shutil.rmtree(STAGE, ignore_errors=True)
    STAGE.mkdir(parents=True)
    uploads = [n for n in changed if n != INSTRUCTIONS]
    for n in uploads:
        shutil.copy2(PROJECT / n, STAGE / n)

    print("ChatGPT Project")
    if not (changed or removed):
        print("  nothing changed")
    if INSTRUCTIONS in changed:
        subprocess.run(["pbcopy"], input=(PROJECT / INSTRUCTIONS).read_bytes(), check=False)
        print(f"  {INSTRUCTIONS} changed: it is on the clipboard; paste it over the Project's instructions")
    for n in uploads:
        print(f"  replace  {n}{'' if n in old or not old else '  (new)'}")
    for n in removed:
        print(f"  delete   {n}  (no longer built)")
    if uploads:
        print(f"  delete the old copies above, then drag in everything in {STAGE.relative_to(ROOT)}/")

    skill_changed = was.get("traveler-trip") != now["traveler-trip"]
    print("\nclaude.ai skill")
    print(f"  {'replace traveler-trip with build/skill/traveler-trip.zip' if skill_changed else 'unchanged'}")
    if changed or removed or skill_changed:
        print("\nThen run: make assistant-done")

    if no_open:
        return
    if uploads:
        open_all(str(STAGE))
    if changed or removed:
        open_all(os.environ.get("TRAVELER_CHATGPT_PROJECT_URL", "https://chatgpt.com/"))
    if skill_changed:
        subprocess.run(["open", "-R", str(SKILL / "traveler-trip.zip")], check=False)
        open_all(CLAUDE_SKILLS)


def done():
    RECORD.write_text(json.dumps(current(), indent=2) + "\n")
    print(f"Recorded this build as uploaded ({RECORD.relative_to(ROOT)}).")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--done", action="store_true", help="record the current build as uploaded")
    ap.add_argument("--no-open", action="store_true", help="print the plan without opening anything")
    args = ap.parse_args()
    if not PROJECT.is_dir():
        raise SystemExit("No build/skill/: run make skill first")
    done() if args.done else stage(args.no_open)


if __name__ == "__main__":
    main()
