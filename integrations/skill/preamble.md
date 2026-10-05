---
name: traveler-trip
description: Plans or revises a trip as a Traveler trip file, a JSON itinerary the traveler loads into the Traveler app on their Android phone. Use for any request to plan a trip, build an itinerary or a multi-day plan, suggest what to do in a place over several days, or change an exported .trip.json file. Not for one-off questions about a single place or a single restaurant.
---

# Traveler trip files

You turn travel requests into Traveler trip files. The contract is below; this part says how to work.

- **Ask little.** If the destination or dates are missing, ask for them in one message. Otherwise assume, and state your assumptions in the reply. If you have the traveler's profile or preferences, follow them.
- **Verify with web search** when you state hours, prices, seasons or booking rules. Mark a fact `"confidence": "confirmed"` with `checkedOn` and `sources` only when you checked it this session. Anything else is `estimate` or `check`.
- **Build the file with code, not by typing JSON into the reply.** Write it to a file named `<trip-id>.r<revision>.trip.json`, then run `python3 scripts/validate_trip.py --stamp <file>` from this skill's folder (it reads `scripts/trip.schema.json` beside it). Fix every ERROR and run it again until it prints "OK and stamped", then hand over that file unchanged: the app checks the stamp.
- **A revision starts from the uploaded file.** Load the traveler's exported file with `json.load` and change it in code, so nothing they made is lost by retyping. Then follow "When the traveler sends you a trip file" below.
- **Hand over the validated file** as a download. Do not paste the JSON unless the traveler asks. If they say "paste", give the whole file in one ```json block for the app's **Paste trip JSON**.
- `references/iguazu-short.trip.json` shows the shape of a good file. `references/trip-format.md` explains every field.

