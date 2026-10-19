# The assistant (a ChatGPT Project)

A ChatGPT Project plans and revises trips. Its chats read their instructions from this repository through ChatGPT's GitHub connector, so a change pushed to `main` reaches the next chat with no upload:

| File | What it is |
| --- | --- |
| `preamble.md` | How to work, and which other file each kind of request needs |
| `../../prompts/itinerary-instructions.md` | The trip-file contract |
| `lodging-search.md` | The lodging search, which writes the shortlist into the trip file as `lodging.options` |
| `travel-guide.md` | The `/travel-guide --trip <trip> --stay <place>` mode, a chat about one stay that starts from `stay_brief.py`'s summary of it |

ChatGPT's Python sandbox has no network, so the scripts it runs are uploaded as project files instead.

## Set up

1. Create a Project (say "Travel") and paste `INSTRUCTIONS.txt` (in this folder) into its instructions.
2. Add the three scripts as project files: `tools/validate_trip.py`, `schema/trip.schema.json` and `tools/stay_brief.py`, plus your travel profile if you want your preferences followed, and your trips' exports if you want `/travel-guide` to find them without an attachment. Nothing else: an old instruction file left there competes with the repository's.
3. Connect the GitHub connector, on the web, to this repository.

Re-upload a script only when it changes, and `INSTRUCTIONS.txt` only when it changes; neither happens often.

## From the phone

1. Ask for a trip. The reply is a short summary and a `<trip-id>.r1.trip.json` download.
2. Download it and open it with **Traveler**. Or say "paste", copy the JSON block, and use **Paste trip JSON** on Traveler's trips screen.
3. To revise: in Traveler, tap the share icon on the trip's overview, send the file to the chat, and say what to change. Import the file that comes back; the app merges it with your edits.
