# Private planner Site

The existing offline planner remains available through `tools/make_planner.py`.
The hosted variant reuses `tools/planner/{index.html,core.js,ui.js}` and adds a
Cloudflare Worker and D1 storage. Sites owns authentication; the entire Site,
including `/api/*`, must remain owner-private. The server relies on that access
boundary for both signed-in browser requests and authorized Sites service calls.

The Site is `appgprj_6af701eadd148191af40cbbc803dbb56`:
<https://traveler-logistics-planner.tlongushort.chatgpt.site>.
Its separate source checkout is `out/traveler-site/`; do not stage `out/` in
Traveler. Use the Sites skill to open/synchronize/publish that repository.
Preserve its `.openai/hosting.json` identity and `d1: "DB"`, with `r2: null`.

To prepare sanitized source in an already selected Site checkout:

```sh
python3 tools/make_planner_site.py out/traveler-site
cd out/traveler-site
node build.mjs
```

This copies code and schema only. The Worker embeds the interface, then fills the
project JSON from D1 on each page load. It sends private, no-store responses.
Do not package a generated offline planner: that HTML contains trip data.

Import the initial project using an authorized Sites service request to
`PUT /api/project` with `{version: 0, project: <planner JSON>}`. This creates
version 1 only if the database is empty; it cannot replace an existing project.
Obtain the Site service credential through `get_site` and pass it only via the
`OAI-Sites-Authorization` header to this Site. Keep it in memory/stdin.
The optional `INITIAL_PROJECT` runtime secret supports small bootstrap fixtures;
the first read inserts it only if storage is empty. A newer project
can be opened with **Open project…**, then saved against the current shared
version. Never place initial data in SQL migrations or build inputs.

Each save inserts a new immutable row in `project_revisions`. A single SQL
statement compares the client's version with the current maximum and inserts
only if they match. Concurrent writers cannot both succeed. A stale request gets
HTTP 409, keeps its device draft, and offers backup/reload actions. Network and
storage failures never report Saved. Editing during a save leaves subsequent
changes unsaved. There is no force-overwrite route.

`GET /api/project` returns the current project/version. `PUT /api/project` takes
`{version, project}`. `GET /api/backups` lists the most recent 100 versions, and
`GET /api/backups/<version>` downloads any retained version as planner JSON.
All historical saves remain in D1; the UI lists the latest 100. JSON backups keep
the exchange baseline, research and unknown project properties.

`tools/planner/hosted/schema.sql` is the initial schema-only migration. The source
preparer refuses to replace a different existing migration. Append migrations
for later schema changes and publish through Sites so hosted migrations run.

`make check` includes the storage contract tests (Node 22.13+ with `node:sqlite`
required), covering competing saves, immutable backups, validation, origin
checks, bootstrap idempotence and failures. Use only the demo trip for UI tests.
