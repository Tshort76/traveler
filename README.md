# Traveler

An offline-first Android travel companion. An LLM writes the trip as one JSON file; the app imports it, shows it, and does the day-to-day planning on the phone, with no network needed after import.

## The loop

1. **Generate.** Give an assistant (Claude, ChatGPT, Gemini) [`prompts/itinerary-instructions.md`](prompts/itinerary-instructions.md) plus your destination and dates. It answers with a `traveler-trip` JSON file. The app can copy the prompt for you: **Settings → Copy instructions**.
2. **Check (optional).** `python3 tools/validate_trip.py my-trip.json` gives the same errors and warnings the app's import preview shows.
3. **Import.** Share or open the file with Traveler. A preview lists the stays, the counts and any warnings before anything is saved. It also says whether the assistant really ran the validator (its stamp matches), and lists anything the assistant should fix, with a **Copy fix request** button to paste back into the chat.
4. **Plan on the phone.** Each day is a calendar: anything with a time sits at its time, and a suggestion without one shows dashed in the first free gap of its morning, afternoon or evening. Hold a block and drag it to give it a time; tap it to change the time, mark it done, or move it to another day. On work days your work hours appear as 2-hour blocks you can drag or cancel; they stay on the phone, outside the trip file. **🧭** opens Google Maps with directions for the day: from the stay's lodging through what is still to do, in the calendar's order (up to ten stops). Once a day is settled, **+** → **Add to calendar…** writes its timed items into a calendar on the phone (your Google calendar, say); doing it again replaces what it added before. For one booking, open its details on **Bookings** or **Events** and tap **📅 Add to calendar**: your calendar app opens on the event filled in (lodging all day from check-in to check-out, flights at their times), and you save it there. Add your own entries and notes, add bookings you made (a day's **+** → **Booking…**; linking a suggestion schedules it at that time), and mark items done or skipped. Undo covers every edit, and everything saves as you go. The trip page also has your own notes (the plan's own notes and warnings sit read-only beneath them, refreshed with each revision) and a **Checklist** of to-dos and packing items, started from templates you keep in the app (**Settings → Checklist templates**); neither goes into the trip file.
5. **Revise.** Export the trip (the share icon on the overview), hand it back to the assistant with your change request, and import the reply. Ask it to find lodging for a stay and the reply carries a shortlist: open that lodging in **Bookings**, tap an option for its advantage, drawback and link, and **Booked** fills in the booking from it. The app merges it with your edits. Your notes, entries, moves and done marks are kept, and where you and the file changed the same thing, you choose. The previous version is kept in History.

## Using it with the Concierge project

The Concierge project's itinerary step used `itinerary_template.html` and `render_itinerary.py`. To switch it to Traveler, replace those two files in the project with:

- `prompts/itinerary-instructions.md`, in place of the Itineraries section of the project instructions
- `schema/trip.schema.json`, as the contract the assistant writes to
- `tools/validate_trip.py`, so the assistant can check its own output where it can run code

Keep your profile files in the project as they are; they never belong in this repository.

## Using it with ChatGPT

A ChatGPT Project writes trip files for the app, reading its instructions from this repository. [integrations/skill/README.md](integrations/skill/README.md) covers setting it up.

## A Google My Maps map of a trip

In Claude Code in this repository, `/build-map <exported trip file>` writes one KMZ file for My Maps whose folders import as layers, with small hollow rings instead of pins so the places underneath stay visible: an Overview (stays, lodging, airports and stations, and the flights, buses and trains between towns) and a layer per stay with every activity, planned or not. Import them at mymaps.google.com, and the map opens in Google Maps on the phone, where each layer can be hidden. Days are not layers: the day screen's 🧭 gives the day's route. The script behind it is `tools/make_map.py`.

## What's in the repository

| Path | What it is |
| --- | --- |
| `docs/trip-format.md` | The trip file format, written for people |
| `schema/trip.schema.json` | The same format as JSON Schema |
| `schema/examples/` | Example trips; they also ship inside the app ("Try the example trip", and the full-feature demo under **Settings → Load the demo trip**) |
| `prompts/itinerary-instructions.md` | The assistant prompt for trip files |
| `integrations/skill/` | The assistant's instructions, read by a ChatGPT Project |
| `tools/` | Validator, stay brief, My Maps and fixture and basemap generators, emulator helper |
| `app/` | The Android app (Kotlin, Jetpack Compose, Room) |
| `docs/ARCHITECTURE.md` | How the app is put together |
| `docs/BUILD.md` | Toolchain, emulator, installing on a phone, release signing |

## Build

```bash
make doctor     # check JDK 17 and the Android SDK
make check      # validator tests, example validation, app unit tests
make install    # build and install the debug app on a phone or emulator
```

See [docs/BUILD.md](docs/BUILD.md) for setup.

## License

MIT. See [LICENSE](LICENSE).
