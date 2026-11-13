#!/usr/bin/env python3
"""Copy only planner code into an existing Sites checkout. Never copy trip data."""
import argparse
from pathlib import Path
import shutil


def prepare(target):
    source = Path(__file__).parent / 'planner'
    (target / 'planner').mkdir(parents=True, exist_ok=True)
    for name in ['core.js', 'ui.js', 'index.html']:
        shutil.copyfile(source / name, target / 'planner' / name)
    for name in ['worker.mjs', 'build.mjs']:
        shutil.copyfile(source / 'hosted' / name, target / name)
    # Schema-only initial migration. Future changes append migrations.
    migration = target / 'drizzle' / '0000_project_revisions.sql'
    migration.parent.mkdir(parents=True, exist_ok=True)
    sql = (source / 'hosted' / 'schema.sql').read_text()
    if migration.exists() and migration.read_text() != sql:
        raise ValueError('Existing migration differs; append a new migration instead.')
    migration.write_text(sql)
    (target / '.gitignore').write_text('dist/\nnode_modules/\n.sites-runtime/\n.env*\n*.planner.json\n*.trip.json\n')
    (target / 'package.json').write_text('{"private":true,"type":"module","scripts":{"build":"node build.mjs"}}\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkout', type=Path)
    prepare(parser.parse_args().checkout)
