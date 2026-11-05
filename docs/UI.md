
# UI rules

Traveler is a single-user, offline-first Android app (Compose, Material 3). The owner's taste is a requirement, not a preference. Hold every screen to the rules below; cite `file:line` and a screenshot for each finding.

## The owner's taste (requirements)

- Slim and sleek. Glyphs and small text over labels and sentences, but never at the cost of a 48dp target.
- Separate pages reached from a compact glyph row (the trip page row `🎫 4/9 · ✅ 3/14 · 🎒 0/16 · 📝` is the model), not cards stacked on the parent.
- One signal per state. The Bookings chip goes red background + red outline when a priority-1 booking is open; no banner, no ⚠, nothing else.
- No filler: no explanatory body text on a screen that has content, no "tip" sentences under fields, no captions that restate what is visible.
- Dislikes card-heavy layouts. Homogeneous rows are a list; a card is for heterogeneous content with its own actions.
- Decided, do not re-raise: the booked mark is `✓` after the name.
- No screen-reader (TalkBack) work. Do not add `contentDescription`s for it or flag their absence.

## Design rules (checkable)

1. Every tappable thing has a 48×48dp target. Compose widens the hit area of a lone small clickable to 48dp (verified on the stay-card tallies), so an isolated glyph is fine; rows and items stacked closer than 48dp are not, because neighbours split the touch. Tappable list rows get `heightIn(min = 48.dp)`.
2. No `Color(0x…)`, `Color.White`, `Color.Black` outside `ui/theme/Theme.kt`. Colours are scheme roles so dark mode is free.
3. Text is never dimmed below `onSurfaceVariant`; done/booked/skipped are a glyph or a pill, never alpha. Blended colours must still pass 4.5:1.
4. One signal per state: colour, glyph or pill, not two.
5. `✓` after the name, built only in `Common.kt marked()`.
6. No explanatory sentence on a screen with content. A sentence explaining a control means the control is wrong.
7. Empty state = one line + at most one action.
8. Navigation and filters are glyph + count.
9. Section pages are separate routes from a compact row, never stacked cards.
10. Small `TopAppBar`: one title line, ≤ 3 actions. Context goes in the first content line, not a subtitle `Column`.
11. Field labels are nouns; examples and formats go in `placeholder`; supporting text only for an error.
12. Right M3 chip type: `FilterChip` to refine, `AssistChip` for an action in context, `InputChip` for a chosen value. Chip label is one or two words or a glyph.
13. Vocabulary keys (`mon`, `morning`, `tentative`) never reach the screen raw; they go through a label function in `Common.kt`.
14. Drag handles use a drag-handle icon, not `Icons.Default.Menu`.

## Where things live

- Screens: `app/src/main/kotlin/dev/tlong/traveler/ui/{trips,overview,stay,day,activity,bookings,checklist,importer,history,settings}`.
- Shared pieces: `ui/common/Common.kt` (`SaveIndicator`, `Pill`, `marked()`, label functions), `ui/theme/Theme.kt` (the only palette).
- Routes: `ui/Nav.kt`. Overlays (activity sheet, place sheet, booking dialog): `ui/activity/`.
- Demo data: `schema/examples/demo.trip.json` (**Settings → Load the demo trip**). Every feature must appear in it.

## Driving the emulator

```bash
export PATH=$(sed -n 's/^sdk\.dir=//p' local.properties)/platform-tools:$PATH ANDROID_SERIAL=emulator-5554
make emulator                      # if nothing is running (headless 'traveler' AVD)
make install                       # build + install the current code
adb shell am start -n dev.tlong.traveler/.MainActivity
python3 tools/emu/ui.py texts      # (x, y) text [content-desc] for every node
python3 tools/emu/ui.py tap "Demo: Buenos"     # substring; "=Exact" for an exact match
adb shell input tap X Y            # for glyph-only nodes: take X Y from `texts`
python3 tools/emu/ui.py shot out/NN-name.png
python3 tools/emu/ui.py scroll 1700 600        # y coordinates, from → to
python3 tools/emu/ui.py back
adb shell cmd uimode night yes     # dark mode; set it back with `no` when done
make emulator-stop                 # always, when the work ends
```

The emulator holds about 1 GB of RAM. Run `make emulator-stop` as soon as you are done with it, including when the work stops on a failure. Keep it running only when the owner asks; then start it with `KEEP=1 make emulator`. Otherwise `make emulator` stops it by itself once the agent session that started it exits.

Traps:
- `tap "<text>"` matches content descriptions too; a stay name also matches the map's description, so use `=Exact` for stay cards.
- Bottom sheets are visible to `texts`; dropdown menus and keyboards are not always. Verify a step before chaining the next (`texts | grep -c "<known text>"`).
- Pressing back one time too many lands on the launcher; relaunch with `am start`.
- Use only the demo trip. Other trips on the device may be the owner's real plans: do not open, screenshot or quote them.
- Never delete trips, restore backups, export, or change Settings during a review. Small edits on the demo trip are fine (undoable).

## Review procedure

1. Read the screen's Compose file end to end. Grep it for `Color(0x`, `.height(`, `heightIn(min`, `alpha(`, `Column {` inside `TopAppBar(title`, `Text("` longer than ~60 chars, `supportingText`, and `Icon(` with `null` outside a labelled control.
2. Launch the app on the demo trip, screenshot the screen in light and dark, every tab, every dialog and sheet it opens, and its empty state if one exists.
3. For each rule above, write a finding only when you can point at `file:line` and name the fix. Mark effort S/M/L. Skip generic advice.
4. Say plainly what already works; it is what the next change must not break.
5. Order findings by what the owner feels first: touch targets and contrast, then double signals, then filler text, then structure.
6. If you are making the change, re-screenshot after and keep the before/after pair beside the issue note.
