# CLAUDE.md

Guidance for Claude Code in this repository.

## What this is

A single-user, offline-first Android app (Kotlin, Jetpack Compose, Room). An LLM writes a trip as a `traveler-trip` JSON file and the app imports, displays and edits it. `README.md` is for the user, `docs/ARCHITECTURE.md` explains the design, and `docs/BUILD.md` covers setup, the emulator and signing.

## Commands

```bash
make check      # validator tests + example validation + JVM tests: the pre-commit gate
make test       # JVM tests only
make install    # debug build onto the attached device or emulator
make fixtures   # regenerate the derived example trips (r2 revision, long synthetic trip)
make emulator   # headless 'traveler' AVD
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew compileDebugKotlin   # fastest compile check
```

`adb` is not on `PATH` by default: prepend `$(sed -n 's/^sdk\.dir=//p' local.properties)/platform-tools`. `tools/emu/ui.py` drives the app (tap by text, `texts`, `shot`, `scroll`, `longdrag`, `back`). Its `scroll` takes y coordinates, not a direction.

## The format is a contract, kept in four places

`docs/trip-format.md` (for people), `schema/trip.schema.json`, `tools/validate_trip.py`, and `model/` + `TripReader.kt` in the app. A format change touches all four, plus `prompts/itinerary-instructions.md` and the examples. `make skill` packages the prompt as the assistant skill; after a change, `make assistant` stages the files to re-upload. `TripReaderTest` and `tools/test_validate_trip.py` run the same `schema/examples` and `schema/invalid` fixtures, which keeps the app and the validator agreeing.

- The format is versioned (`formatVersion`). Additive, optional fields stay at version 1; anything an older app would misread needs a bump, and the reader refuses newer versions with a plain message.
- Ids are stable across revisions; the merge depends on it.
- Fields the app writes (`origin: "user"`, `userNote`, `userEdited`) must survive a round trip through an assistant, so the prompt tells it to keep them.
- `schema/examples/demo.trip.json` is the demo trip (**Settings → Load the demo trip**). It should show every feature: when you add one, give the demo an instance of it and keep it passing and stamped: `python3 tools/validate_trip.py --stamp schema/examples/demo.trip.json` after every edit (a test fails on a stale stamp). It must hold no personal data.

## Traps

- **Android's ICU regex is stricter than the JVM's.** An unescaped `}` or `]` compiles in JVM tests and throws `PatternSyntaxException` on a device. Escape both. This surfaced once as a bogus "the JSON is broken" on every import.
- **kotlinx-serialization omits defaults.** A field whose value must appear in the file (`format`, `formatVersion`, `revision`, a backup's `format`) needs `@EncodeDefault`.
- **Edits go through `domain/Edits.kt`**, which records `userEdited` provenance, and through `TripSession.edit`, which provides undo and saving. Writing `localJson` any other way breaks both the merge and undo.
- **Never claim "Saved" before the write lands.** `SaveIndicator` reads `TripSession.saveState`; keep it that way.
- The example trips ship in the APK (the `schema/examples` and `prompts` asset dirs in `app/build.gradle.kts`), so a broken example breaks "Try the example trip".

## Not in this repository

Personal travel-profile files live outside this repository and must never be copied here.
