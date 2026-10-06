# Traveler

An offline-first Android travel companion. An LLM writes the trip as one JSON file; the app imports it, shows it, and does the day-to-day planning on the phone, with no network needed after import.

## The loop

1. **Generate.** Give an assistant (Claude, ChatGPT, Gemini) [`prompts/itinerary-instructions.md`](prompts/itinerary-instructions.md) plus your destination and dates. It answers with a `traveler-trip` JSON file. The app can copy the prompt for you: **Settings → Copy instructions**.
2. **Check (optional).** `python3 tools/validate_trip.py my-trip.json` gives the same errors and warnings the app's import preview shows.
3. **Import.** Share or open the file with Traveler. A preview lists the stays, the counts and any warnings before anything is saved. It also says whether the assistant really ran the validator (its stamp matches), and lists anything the assistant should fix, with a **Copy fix request** button to paste back into the chat.
4. **Plan on the phone.** Move suggestions between morning, afternoon and evening by dragging, or use "Move to…" for another day. Add your own entries and notes, add bookings you made (a day's **Fixed** card → **Add booking**; linking a suggestion schedules it at that time), and mark items done or skipped. Items with a fixed time sort themselves within their part of the day. Undo covers every edit, and everything saves as you go.
5. **Revise.** Export the trip (the share icon on the overview), hand it back to the assistant with your change request, and import the reply. The app merges it with your edits. Your notes, entries, moves and done marks are kept, and where you and the file changed the same thing, you choose. The previous version is kept in History.

## Using it with the Concierge project

The Concierge project's itinerary step used `itinerary_template.html` and `render_itinerary.py`. To switch it to Traveler, replace those two files in the project with:

- `prompts/itinerary-instructions.md`, in place of the Itineraries section of the project instructions
- `schema/trip.schema.json`, as the contract the assistant writes to
- `tools/validate_trip.py`, so the assistant can check its own output where it can run code

Keep your profile files in the project as they are; they never belong in this repository.

## Using it with ChatGPT (or as a Claude skill)

`make skill` builds two skills that ChatGPT and Claude both load, plus a ChatGPT plugin wrapping both for the phone. `traveler-trip` writes trip files for the app. `trip-workbook` drafts the route as a spreadsheet (`.xlsx` for Google Sheets): the stays, their dates and the travel between them, before there is a trip file. It also writes a Google My Maps file; the app copies its prompt from **Settings → Copy spreadsheet instructions**. [integrations/skill/README.md](integrations/skill/README.md) covers uploading it.

## What's in the repository

| Path | What it is |
| --- | --- |
| `docs/trip-format.md` | The trip file format, written for people |
| `schema/trip.schema.json` | The same format as JSON Schema |
| `schema/examples/` | Example trips; they also ship inside the app ("Try the example trip", and the full-feature demo under **Settings → Load the demo trip**) |
| `prompts/itinerary-instructions.md` | The assistant prompt for trip files |
| `prompts/workbook-instructions.md` | The assistant prompt for the route-drafting spreadsheet and My Maps file |
| `integrations/skill/` | The assistant skill and plugin (ChatGPT and Claude) |
| `tools/` | Validator, fixture and basemap generators, emulator helper |
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
