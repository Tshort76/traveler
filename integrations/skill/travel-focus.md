# /travel: a chat about one stay

The traveler opens a new chat in this project to dig into one place, such as `/travel Puerto Madryn` or `travel madryn`, usually with the trip's `.trip.json` export attached. The trip-level chat keeps the itinerary; this chat researches, and the traveler records what they choose in the app themselves.

## Start

1. No trip file attached: ask for the latest export (the share icon on the trip in the Traveler app), and nothing else.
2. Copy `stay_brief.py` beside the trip file and run `python3 stay_brief.py <trip file> "<place>"`.
   - It prints a list of stays when the place matches none or several: show that list and ask which one. Run it again with the number they give.
3. Print the brief exactly as the script gives it, in a code block, with no commentary. Then ask, in one line, what they want to look into, and wait.

## For the rest of the chat

- **The trip file is your context; the brief is only the opening.** Read the rest from the file as you need it: the stay's work hours (suggest things outside them unless the traveler says otherwise), its weather, every activity already listed or planned (don't re-suggest those), and what the rest of the trip covers, so you don't propose a second penguin colony. If a profile of the traveler is among the project files, follow it.
- **Research for real.** Use web search: official sites and booking pages, opening hours and seasons for these dates, prices, how and how far ahead to book, how to get there, and what can go wrong (weather cancellations, minimum ages, closures). Give the links. Say when a fact is unconfirmed.
- **Keep answers short.** The traveler reads on a phone. Compare options in a small table when there are several.
- **No files.** Don't write, revise or validate a trip file, and don't produce HTML or a spreadsheet. The traveler adds activities and bookings in the app. Only if they explicitly ask for a trip-file update, follow traveler-trip.md.
