#!/usr/bin/env python3
"""Package the offline desktop planner and its project JSON for one trip."""
import argparse
import json
from pathlib import Path
import subprocess


def build(source, out_dir, overwrite=False):
    if not overwrite and ((out_dir / "index.html").exists() or list(out_dir.glob("*.planner.json"))):
        raise FileExistsError("Planner output already exists; use --force only to replace generated files.")
    assets = Path(__file__).parent / "planner"
    # The same import and airport-coverage logic runs in Node and in the browser.
    script = """
const C = require(process.argv[1]);
const fs = require('fs');
const project = C.openProject(JSON.parse(fs.readFileSync(process.argv[2], 'utf8')));
C.addAirportJourneys(project.trip);
process.stdout.write(JSON.stringify(project));
"""
    result = subprocess.run(
        ["node", "-e", script, str((assets / "core.js").resolve()), str(source.resolve())],
        check=True, capture_output=True, text=True,
    )
    project = json.loads(result.stdout)
    out_dir.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(project, ensure_ascii=False).replace("<", "\\u003c")
    page = (assets / "index.html").read_text()
    for marker, replacement in [
        ("/*__PLANNER_CORE__*/", (assets / "core.js").read_text()),
        ("/*__PLANNER_UI__*/", (assets / "ui.js").read_text()),
        ("__PLANNER_PROJECT__", encoded),
    ]:
        page = page.replace(marker, replacement)
    (out_dir / "index.html").write_text(page)
    (out_dir / (project["trip"]["id"] + ".planner.json")).write_text(
        json.dumps(project, ensure_ascii=False, indent=2) + "\n"
    )
    return project


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--force", action="store_true", help="Replace an existing generated planner and project")
    args = parser.parse_args()
    try:
        build(args.source, args.out_dir, args.force)
    except FileExistsError as error:
        parser.error(str(error))
    print(args.out_dir / "index.html")
