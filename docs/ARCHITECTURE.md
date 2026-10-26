# Architecture

One Gradle module (`:app`), package `dev.tlong.traveler`, hand-wired. `AppContainer` owns the database, the store and the open trip sessions; screens reach it through `LocalContainer`.

## The trip is a document

A trip is not normalized into tables. Each `TripRow` (Room) holds two JSON documents:

- **`baseJson`**: the last file imported, exactly as the assistant wrote it (normalized). It is the common ancestor for the next merge.
- **`localJson`**: the plan as the traveler has it now, edits included.

A `SnapshotRow` copy of both is taken before anything replaces them (a revision import, a snapshot restore, a backup restore). The newest 20 are kept per trip. A deleted trip is soft-deleted and purged after 30 days.

Some of the traveler's things live beside the trip file, never in it, so no export carries them and no revision changes them: `TripRow.notes` (free text; the Notes page shows the trip file's `warnings` and `notes` read-only beneath it, so a revision replaces those and never the text), `TripRow.checklist` (a `Checklist` document of to-dos and packing items, `domain/Checklist.kt`) and `TripRow.work` (a `WorkPlan`, `domain/WorkBlocks.kt`: the days whose work blocks were moved or cancelled; any other work day follows the stay's `workRhythm` cut into 2-hour blocks). Checklist templates are their own `TemplateRow` documents; applying one copies its items into the trip, skipping any already there. Backups carry all of them.

Everything the app adds to the file format is a field the format already allows: `origin: "user"` on the traveler's own activities, `userNote`, and `userEdited` (the list of fields the traveler changed). That is what lets an exported file go back to an assistant and come back merged.

## Layers

| Package | Role |
| --- | --- |
| `model/` | The format: `Trip` and friends (kotlinx-serialization), `TripJson` (lenient reader, writer, unknown-field detection), `TripReader` (plain-language errors and warnings, mirroring `tools/validate_trip.py`). |
| `domain/` | Pure functions, no Android: `TripCalendar` (dates, stays, slots, work hours across time zones), `Edits` (every edit, recording provenance), `Conflicts` (can this go here?), `DayLayout` (where blocks sit on the day calendar), `DayEvents` (a day's timed items as calendar events), `CalendarEntry` (one booking as an event for the phone's calendar app), `Merge` (three-way merge), `Export`. |
| `data/` | Room, `TripStore` (rows, snapshots, backup), `TripSession` (the open trip: undo stack of 50, serialized conflated saves, a `SaveState` the UI shows), `ImportRouter` (decides new, already imported, revision, backup, or invalid), `DeviceCalendar` (writes a day's events to a phone calendar and remembers their ids per trip and date, so a re-export replaces only its own events). |
| `ui/` | Compose screens: trips list, import preview, overview with map, stay (itinerary, activities), day planner, history, settings. |

## Merge

`Merge.plan(base, local, incoming)` compares entities by id (days by date), field by field.

- Changed only in the file: taken. Changed only by the traveler: kept. Changed on both sides to different values: a conflict, defaulting to "keep mine".
- App fields (`origin`, `userEdited`, `userNote`) always keep the local value. A day's plan is merged as one value.
- An entity the file drops becomes a removal decision. It defaults to remove, unless it is scheduled or the traveler edited it.
- An entity the traveler deleted but the file changed is offered back ("restorables").
- `freshness` warns when the incoming revision is older or the same as the base.

`Merge.apply(plan, decisions)` produces the new local document; the incoming file becomes the new base.

## Placement rules

`Conflicts.checkPlacement` returns what is wrong with putting an activity in a slot. A **booked commitment**, a **transfer** (from 90 minutes before departure), **work hours**, **closed days** and **opening hours** block the move unless the traveler chooses "anyway". A suggestion from another stay, or one already planned elsewhere, only warns. Work hours are stated in one zone and converted per date, so a daylight-saving change mid-trip shifts them correctly.

## Maps

The app draws no maps of its own. `tools/make_map.py`, run by the `/build-map` skill in Claude Code, turns an exported trip into one KMZ for Google My Maps: an Overview layer and a layer per stay, with hollow ring icons packed in. On the phone, places open in Google Maps through `MapsLinks`, and the day screen's 🧭 opens directions through the day's remaining stops (`MapsLinks.route`, by coordinates).

## Tests

JVM only (`make check`). The model and domain tests run plain. `TripStoreTest` and the UI checks use Robolectric (SDK 35). `TripReaderTest` runs the same `schema/examples` and `schema/invalid` fixtures as the Python validator, so the two cannot drift apart silently.
