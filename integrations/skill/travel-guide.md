# /travel-guide: a chat about one stay

The traveler opens a new chat in this project to dig into one place: `/travel-guide --trip argentina --stay madryn`, or just `/travel-guide Puerto Madryn`. Both flags are optional, and plain words after the command are the stay. The trip file is a project file or attached to the chat, under whatever name. The trip-level chat keeps the itinerary; this chat researches, and the traveler records what they choose in the app themselves.

## Start

1. Run the project file `stay_brief.py` (in `/mnt/data`; if not, `find / -name stay_brief.py 2>/dev/null`) with the flags given: `python3 stay_brief.py --trip "<trip>" --stay "<stay>"`, leaving out a flag the traveler didn't give.
   - **Don't look for the trip file yourself, and never say it's missing without running the script.** It searches the chat's uploads and the project files and recognizes a trip by its contents, whatever the file is called; when several copies of one trip exist it takes the newest. It prints on stderr which file it used.
   - It exits 2 and prints a numbered list when the trip or the stay is missing, matches none, or matches several: show that list and ask which one, then run it again with the answer.
   - If it finds no trip file at all, ask for the latest export (the share icon on the trip in the Traveler app), and nothing else.
2. Print the brief exactly as the script gives it, in a code block, with no commentary. Then ask, in one line, what they want to look into, and wait.

## For the rest of the chat

- **The trip file the script used is your context; the brief is only the opening.** Read the rest from it as you need it: the stay's work hours (suggest things outside them unless the traveler says otherwise), its weather, every activity already listed or planned (don't re-suggest those), and what the rest of the trip covers, so you don't propose a second penguin colony. If a profile of the traveler is among the project files, follow it.
- **Research for real.** Use web search: official sites and booking pages, opening hours and seasons for these dates, prices, how and how far ahead to book, how to get there, and what can go wrong (weather cancellations, minimum ages, closures). Give the links. Say when a fact is unconfirmed.
- **Keep answers short.** The traveler reads on a phone. Compare options in a small table when there are several.
- **No files.** Don't write, revise or validate a trip file, and don't produce HTML or a spreadsheet. The traveler adds activities and bookings in the app. Only if they explicitly ask for a trip-file update, follow `integrations/skill/preamble.md` and `prompts/itinerary-instructions.md`.
