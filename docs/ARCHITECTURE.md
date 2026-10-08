# Architecture

One Gradle module (`:app`), package `dev.tlong.traveler`, hand-wired. `AppContainer` owns the database, the store and the open trip sessions; screens reach it through `LocalContainer`.

## The trip is a document

A trip is not normalized into tables. Each `TripRow` (Room) holds two JSON documents:

- **`baseJson`**: the last file imported, exactly as the assistant wrote it (normalized). It is the common ancestor for the next merge.
- **`localJson`**: the plan as the traveler has it now, edits included.

A `SnapshotRow` copy of both is taken before anything replaces them (a revision import, a snapshot restore, a backup restore). The newest 20 are kept per trip. A deleted trip is soft-deleted and purged after 30 days.

Some of the traveler's things live beside the trip file, never in it, so no export carries them and no revision changes them: `TripRow.notes` (free text) and `TripRow.checklist` (a `Checklist` document of to-dos and packing items, `domain/Checklist.kt`). Checklist templates are their own `TemplateRow` documents; applying one copies its items into the trip, skipping any already there. Backups carry all three.

Everything the app adds to the file format is a field the format already allows: `origin: "user"` on the traveler's own activities, `userNote`, and `userEdited` (the list of fields the traveler changed). That is what lets an exported file go back to an assistant and come back merged.

## Layers

| Package | Role |
| --- | --- |
| `model/` | The format: `Trip` and friends (kotlinx-serialization), `TripJson` (lenient reader, writer, unknown-field detection), `TripReader` (plain-language errors and warnings, mirroring `tools/validate_trip.py`). |
| `domain/` | Pure functions, no Android: `TripCalendar` (dates, stays, slots, work hours across time zones), `Edits` (every edit, recording provenance), `Conflicts` (can this go here?), `Merge` (three-way merge), `Export`. |
| `data/` | Room, `TripStore` (rows, snapshots, backup), `TripSession` (the open trip: undo stack of 50, serialized conflated saves, a `SaveState` the UI shows), `ImportRouter` (decides new, already imported, revision, backup, or invalid). |
| `ui/` | Compose screens: trips list, import preview, overview with map, stay (itinerary, activities, info), day planner, history, settings. |

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

## Map

The trip overview's map (`TripMap` in `ui/map/TripBackdrop.kt`) is a Canvas in Web Mercator over a picture. The picture is the terrain style plus Natural Earth shaded relief, which MapLibre's snapshotter renders once while online, framed exactly as the canvas frames the stays (`snapshotCamera`). It is kept as WebP per trip, stays and size: 23 KB for the card and 62 KB full screen on the example trip. Until it exists, or after the stays move, the canvas draws `assets/basemap/countries.txt`, Natural Earth 50m country outlines from `tools/make_basemap.py`. Stays are numbered markers, clustered in screen space ("1–2,14" when a city is visited twice). Transfers are dashed arcs. Google Maps links open only when online and say so when not.

A stay's Activities tab has a terrain map (`ui/map/AreaMap.kt`): MapLibre Native drawing OpenFreeMap's OpenStreetMap vector tiles with the app's own style, which is only land, water, parks and built-up areas. There are no streets and no text, so no font downloads. Zoom stops at 13, where a pin is about a quarter mile wide; Google Maps does directions. Pins are bitmaps carrying the activity's number from the list, and pins that would overlap merge ("3–5", or "6×" for many), re-clustered whenever the camera settles. **Offline maps…** in the trip menu (`OfflineMaps.kt`) saves one MapLibre offline region per stay, covering the stay's places plus 2 km (`domain/MapAreas.kt`), zoom 8–13: 3.3 MB for the example trip's three stays. The downloader fetches only over HTTP, so the style JSON is put in the database under a placeholder URL first. Regions are tagged `tripId/stayId`, and the app-start purge drops those of trips that no longer exist. Without tiles, the map is plain land with the pins and the scale bar.

## Tests

JVM only (`make check`). The model and domain tests run plain. `TripStoreTest` and the UI checks use Robolectric (SDK 35). `TripReaderTest` runs the same `schema/examples` and `schema/invalid` fixtures as the Python validator, so the two cannot drift apart silently.
