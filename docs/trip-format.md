# Trip file format (`traveler-trip`, version 1)

A trip file is one JSON document describing one trip. An assistant writes it, Traveler imports it, the traveler edits it on the phone, and Traveler exports it again — so the same file makes the round trip in both directions. This page is the contract. `schema/trip.schema.json` is the machine-readable copy, `tools/validate_trip.py` checks a file against it, and `prompts/` holds the instructions you give an LLM.

File names end in `.trip.json` by convention, e.g. `argentina-2026-11.trip.json`.

## The shape in one screen

```
Trip ─┬─ stays[]        a period based in one place (a return to the same city is a new stay)
      ├─ transfers[]    getting from one stay to the next (flight, bus, boat…)
      ├─ activities[]   everything worth doing, each belonging to one stay — this is the pool
      ├─ commitments[]  fixed things with a date: bookings, appointments, work meetings
      └─ days[]         the suggested plan: per date, an optional note and activities placed in slots
```

An activity exists once, in `activities`. A day's plan points at activities by id. An activity no day points at is "in the pool"; removing it from a day returns it there. That separation is what lets the traveler move things around without losing them.

## Rules that make revisions safe

1. **Every `id` is stable.** When revising a trip, keep every id you were given and mint new ids only for new things. Ids are lower-case slugs (`garganta-del-diablo`, `bsas-2`), unique within their list.
2. **The trip `id` never changes** across revisions. A new trip gets a new id.
3. **`revision` goes up by one** each time you produce a new version of the trip.
4. **Fields named `origin`, `userEdited`, `userNote` and `userStatus` are written by the app.** Keep them exactly as given. An item with `"origin": "user"` is the traveler's own entry: never delete or reword it. A field listed in `userEdited` holds the traveler's wording: do not overwrite it — propose changes in your reply instead.
5. **Never invent facts.** Leave out hours, prices, bookings and availability you have not verified, or mark the activity `"confidence": "check"`.

## Trip (top level)

| Field | Req | Type | Meaning |
|---|---|---|---|
| `format` | ✓ | `"traveler-trip"` | Identifies the file. |
| `formatVersion` | ✓ | `1` | This document. |
| `id` | ✓ | slug | Stable across revisions. |
| `revision` | ✓ | integer ≥ 1 | Bump on each new version. |
| `generatedAt` | | ISO date-time | When this revision was written. Used to warn about importing an older copy. |
| `generatedBy` | | string | e.g. `"Claude (Concierge)"`. |
| `title` | ✓ | string | `"Argentina — Iguazú and Buenos Aires"` |
| `kicker` | | string | Short line above the title. |
| `summary` | | string | One line: the shape of the trip. |
| `startDate`, `endDate` | ✓ | ISO date | First and last day of the trip, inclusive. |
| `travelers` | | integer ≥ 1 | How many people the trip is for; per-person prices are multiplied by it. Absent means one. |
| `links` | | Link[] | Planning spreadsheet, the planning chat, docs. |
| `tripMap` | | TripMap | A custom Google map and/or an overview image. |
| `stays` | ✓ | Stay[] | In visit order. At least one. A zero-night stay (arrive = depart) first or last is the home airport, there so the flights out and back have a `from` and `to`; the app shows those flights but no stay card or map pin for it. |
| `transfers` | | Transfer[] | |
| `activities` | | Activity[] | |
| `commitments` | | Commitment[] | |
| `days` | | Day[] | Any dates left out get an empty day. |
| `notes`, `warnings` | | string[] | Trip-wide logistics. Shown once, not per day. |
| `exportedFrom` | | object | Written by the app on export: `{app, exportedAt, basedOnRevision, localChanges}`. |
| `validated` | | object | Written by `validate_trip.py --stamp` when the file passes: `{by, hash}`, a fingerprint of everything else in the file (whitespace and key order ignored, numbers as written). The import preview shows whether it matches. The app drops it on export. |

**Link** — `{ "label": "Planning sheet", "url": "https://…", "kind": "spreadsheet" | "chat" | "map" | "doc" | "booking" | "other" }`

**TripMap** — `{ "url"?, "imageUrl"?, "attribution"?, "note"? }`. `imageUrl` is a small overview picture showing the destinations, with `attribution` kept beside it. `url` (a custom Google map) is kept but not shown.

**Place** — `{ "name"?, "query"?, "address"?, "lat"?, "lng"?, "placeId"?, "mapsUrl"? }`. Any one is enough. For "Open in Google Maps" the app prefers `mapsUrl`, then `placeId`, then `query`, then `lat`/`lng`, then `name` plus the stay's name. `query` is what you would type into Google Maps (`"Jardín de los Picaflores, Puerto Iguazú"`). Coordinates are what put a place on the trip's Google My Maps map and in the day's directions, so give them for every stay and every activity.

## Stay

A stay is a period based in one location. A return to the same city is a **separate stay** with its own id (`bsas-1`, `bsas-2`).

| Field | Req | Type | Meaning |
|---|---|---|---|
| `id`, `name` | ✓ | | `"Puerto Iguazú"` |
| `region` | | string | `"Misiones, Argentina"` |
| `place` | | Place | Coordinates of the base. |
| `arrive`, `depart` | ✓ | ISO date | Nights = `depart − arrive`. The depart date is the travel day. |
| `timezone` | | IANA zone | `"America/Argentina/Buenos_Aires"`. Strongly recommended: all times in this stay are local to it. |
| `summary` | | string | One short line: what this stay is for. |
| `priorities` | | string[] | The few things not to miss, ranked. |
| `lodging` | | Lodging | |
| `transport` | | string | Getting around locally, once. |
| `workRhythm` | | WorkRhythm | Recurring work hours during this stay. |
| `climate` | | Climate | The typical weather there for these dates. |
| `notes` | | string[] | Stay-level logistics. |
| `mapUrl` | | URL | A custom Google map for this stay. |
| `links` | | Link[] | |

**Lodging** — `{ "name"?, "place"?, "status": "booked" | "tentative" | "undecided", "checkIn"?, "checkOut"?, "ref"?, "url"?, "bookDaysBefore"?, "how"?, "notes"?, "price"?, "options"?, "searched"? }`. Leave `name` out when no hotel is chosen yet. `price` is usually the nightly rate with `"unit": "night"`, which the app multiplies by the stay's nights; without `unit` it is the whole stay.

**Lodging options** — `options` is a lodging search's shortlist, best fit first, and `searched` the date it was researched. Each is `{ "name", "url"?, "type"?, "room"?, "area"?, "price"?, "rating"?, "pro"?, "con"? }`: `type` is hotel, hostel, apartment and so on; `room` says what is rented, with bathroom sharing when known ("double, own bath"); `area` is the neighbourhood; `price` is indicative, usually `"unit": "night"`; `rating` is `{ "score", "source", "outOf"?, "reviews"? }` on the platform's own scale (9.0 of 10 on Booking.com, 4.9 of 5 on Airbnb); `pro` and `con` are the main advantage and drawback in a few words. The app lists them under the stay's lodging in Bookings until it is booked, and "Booked" on one fills in the booking form from it. Options are candidates only: none of them is the stay's lodging until the traveler books it.

**Price** — `{ "amount": number, "max"?: number, "currency"?: "USD", "unit"?: "total" | "night" | "person", "note"? }`. `amount` alone is one figure; `amount` to `max` is a range. `unit` says what it is for: the whole item (the default), one night (lodging only; multiplied by the stay's nights), or one traveler (multiplied by the trip's `travelers`). A return ticket's later legs carry `amount` 0, so the fare counts once. `currency` is an ISO code and defaults to USD; assistants write USD, converted approximately, with the local price in `note`. Until the item is booked, a price is an estimate; once booked, it is what was paid. The app's Bookings screen totals them per currency.

**Booking guidance**, on lodging, a Booking and a commitment: `url` is where to book (the official site or the operator), `bookDaysBefore` how many days before the item's date to book it by, and `how` one or two sentences on how to book it. The app counts back from the item's current date (check-in, the departure, the day an activity is planned on), so the deadline moves with a planned day. An open item is due from a week before its book-by date, and stays due once that date passes; each row shows it, and the Bookings tab (lodging and travel) or the 🎟️ Events chip (everything else) turns red. Typical leads: 21 for a domestic or regional flight and for lodging, 60 for a long-haul flight, 7 for an interchangeable tour, 1 for a timed admission; something to book as soon as it is chosen (a particular concert, a capped tour) gets the days from now to its date. A paid entry bought at the door or on the day (most museums, gardens, parks) has a booking with status `onsite`: its price, no `bookDaysBefore`, and no place on the Bookings screen. A free one has no booking. Files from before `bookDaysBefore` carry `priority` 1–3, which the app still reads (1 is due now, 2 is 14 days, 3 is 1 day) and the full check asks to replace.

**Climate** — `{ "highC": 22, "lowC": 12, "flags"?: ["rainy"] }`. The typical daytime high and night-time low in °C for the place at that time of year: climate normals, not a forecast. `flags` names the seasonal concerns worth packing for, from `dry`, `rainy`, `stormy`, `windy`, `humid` and `snowy`; leave it out when none stands out. The stay card shows the range, in °F on a phone set to the US, with a glyph per flag.

**WorkRhythm** — `{ "days": ["mon","tue","wed","thu","fri"], "start": "08:00", "end": "15:00", "timezone"?: "America/Denver", "note"? }`. When `timezone` differs from the stay's, the app converts the hours to local time for each date, so a fixed-offset destination and a home zone that changes its clocks both come out right.

## Transfer

`{ "id", "from": stayId, "to": stayId, "date", "mode": "flight"|"train"|"bus"|"boat"|"ferry"|"car"|"taxi"|"hike"|"other", "depart"?: "HH:MM", "arrive"?: "HH:MM", "details"?, "fromPlace"?: Place, "toPlace"?: Place, "booking"?: Booking }`

**Booking** — `{ "status": "booked"|"tentative"|"needed"|"onsite", "ref"?, "url"?, "bookDaysBefore"?: days, "how"?, "price"?: Price }`. A flight, train, bus or boat transfer is listed on the Bookings screen even without one.

`mode` is the main vehicle; the app shows it as an icon. For `"other"`, the app shows the first vehicle the `details` name.

`fromPlace` and `toPlace` are where it leaves from and arrives at when that isn't the stay itself: a flight's airports (`{ "name": "Aeroparque Jorge Newbery (AEP)", "query", "lat", "lng" }`), or the stations or ports of a long bus, train or ferry ride between towns. The trip map marks them and draws the leg between them.

Times are local to the place they happen.

## Activity

| Field | Req | Type | Meaning |
|---|---|---|---|
| `id`, `stayId`, `name` | ✓ | | A recognizable place or experience: `"Garganta del Diablo walkway"`, never `"Explore the park"`. |
| `tag` | | emoji | One of the category emoji below. |
| `short` | | string | ≤ 10 words, for the compact list. |
| `detail` | | string[] | Paragraphs: what you actually do. Plain text; `**bold**` and `[links](https://…)` are allowed. |
| `why` | | string | One line on why it fits this traveler. |
| `rank` | | integer | 1 = strongest pick in its stay. Lower is better; ties allowed. |
| `stars` | | 1–3 | How strongly it is recommended for this traveler: 3 must do (shown "(✨)" after the name), 2 likely to enjoy ("(⭐)"), 1 a common pick worth doing with time to spare (no mark). |
| `place` | | Place | |
| `duration` | | Duration | `{ "minutes"?, "maxMinutes"?, "includesTravel"?: bool }` — an estimate, not a rule. |
| `fit` | | enum | `"short"` (≤ 2 h), `"half-day"`, `"full-day"`, `"evening"`. |
| `bestTime` | | enum[] | Any of `"morning"`, `"afternoon"`, `"evening"`. |
| `effort` | | enum | `"easy"`, `"moderate"`, `"hard"`. |
| `conditions` | | string[] | When it is a good alternative: `"rainy-day"`, `"short-morning"`, `"free-afternoon"`, `"hot-day"`, `"needs-car"`… |
| `hours` | | string | Opening hours as a human sentence, only if verified. |
| `availability` | | Window[] | Machine-readable opening windows: `{ "days"?: ["tue",…], "start"?: "HH:MM", "end"?: "HH:MM", "note"? }`. Used to warn when a plan puts it outside them. |
| `practical` | | object | Any of `transport`, `booking`, `cost`, `weather`, `access`, `tips` — strings. |
| `booking` | | Booking | Set when it needs a reservation or ticket: `{ "status": "needed", "price": {…} }`. It then appears on the Bookings screen; booking it in the app adds a commitment linked to it. `"status": "onsite"` is a paid entry bought at the door: the app shows its price on the activity but leaves it off Bookings. |
| `confidence` | | enum | `"confirmed"` (checked recently), `"estimate"`, `"check"` (verify before going). |
| `checkedOn` | | ISO date | When details were last verified. |
| `url`, `sources` | | URL, URL[] | Official site; where the facts came from. |
| `origin`, `userEdited`, `userNote` | | | **App-written.** See the rules above. |
| `userStatus` | | enum | **App-written.** `"done"`: the traveler did it. `"wontdo"`: not interested; don't plan it or suggest it again. |

Category tags: 🏛️ architecture · 🌿 nature · ☕ coffee · 🍽️ food · 🎨 art · 🛍️ shopping · 📸 viewpoint · 🏃 run/hike · 🧗 climbing · 🚶 walk · 🏡 neighborhood · 💻 work · 🚗 driving · 🎭 culture · 🌊 water · 🌲 forest · 🏔️ mountains · 📚 books · 🎵 music · 🍸 bar · 💃 dance · 🏊 swimming

## Commitment

Something fixed on a date that the plan must work around.

`{ "id", "title", "date", "start"?: "HH:MM", "end"?: "HH:MM", "kind": "booking"|"appointment"|"work"|"other", "booked"?: bool, "stayId"?, "place"?, "ref"?, "url"?, "bookDaysBefore"?, "how"?, "price"?: Price, "notes"?, "activityId"? }`

`booked: true` means a reservation exists outside the app. The app never moves it and warns before anything is planned on top of it. `activityId` links a booked tour to the activity it is for. A commitment of kind `booking`, or any booked one, appears on the Bookings screen. Recurring work hours belong in the stay's `workRhythm`, not here.

## Day

| Field | Req | Type | Meaning |
|---|---|---|---|
| `date` | ✓ | ISO date | |
| `stayId` | | id | Defaults to the stay covering that date. |
| `kind` | | enum | `"plan"` (default), `"work"`, `"rest"`, `"travel"`, `"free"`. A work, rest or travel day may have an empty plan — never invent an outing to fill it. |
| `title` | | string | A few words: `"Brazilian side"`. |
| `note` | | string | The traveler's own note for the day, written in the app. An assistant leaves it out: the app shows a day note only when the traveler wrote it. |
| `plan` | | Item[] | Activities placed on this day, in order. |
| `userEdited` | | string[] | **App-written.** |

**Item** — `{ "activityId", "slot": "morning"|"afternoon"|"evening"|"allday", "time"?: "HH:MM", "status"?: "proposed"|"done"|"skipped", "note"? }`. A `time` makes it a fixed-time item; without one it is loosely ordered within its slot. Status defaults to `proposed`.

## Activities file (`traveler-activities`, version 1)

New activities for a trip, written without the trip file at hand: in a fresh chat, say, or by another assistant. Importing one only ever adds; nothing already in the trip changes or goes.

```json
{ "format": "traveler-activities", "formatVersion": 1, "tripId": "argentina-2027",
  "activities": [ { "id": "punta-tombo", "stayId": "puerto-madryn", "name": "Punta Tombo penguin colony", "stars": 3, "place": { "lat": -44.05, "lng": -65.22 } } ] }
```

| Field | Req | Type | Notes |
|---|---|---|---|
| `format` | ✓ | `"traveler-activities"` | |
| `formatVersion` | ✓ | `1` | |
| `tripId` | | id | The trip's id if known. Without it, the app picks the trip most of the stays fit, and the traveler can change it. |
| `generatedAt`, `generatedBy`, `validated` | | | As in a trip file. |
| `activities` | ✓ | Activity[] | At least one, each a full Activity as above. |

An activity's `stayId` is the stay's id, or a slug of its name (`"puerto-madryn"`) when the id is unknown. The app matches the id, then the name, then a name that starts with it; a city visited twice takes its current or next visit. One that fits no stay goes to a stay the traveler picks on the preview. An activity whose name is already in that stay is skipped, and a taken `id` gets a suffix. `schema/examples/demo.activities.json` is a worked example for the demo trip.

## What the app does with a file

- **New trip id** → a preview (name, dates, stays, problems), then import.
- **Known trip id, same content** → "already imported", nothing changes.
- **Known trip id, new content** → a revision: the app compares the incoming file with the version it last imported and with the traveler's current plan, lists what changed, keeps the traveler's edits, asks about anything both sides changed, and keeps a snapshot so the import can be undone.
- **Lower `revision` or older `generatedAt` than the copy in use** → a warning before anything happens.
- **An activities file** → a preview of what joins which stay, with any already there listed as skipped; the traveler unticks what they don't want and adds the rest in one step, which History can undo.
- **Export** writes the current plan — the traveler's edits included — in this same format, with `origin`, `userEdited`, `userNote` and `userStatus` filled in, so an assistant can revise it and the app can merge the result.
