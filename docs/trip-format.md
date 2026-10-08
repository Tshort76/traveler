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
4. **Fields named `origin`, `userEdited` and `userNote` are written by the app.** Keep them exactly as given. An item with `"origin": "user"` is the traveler's own entry: never delete or reword it. A field listed in `userEdited` holds the traveler's wording: do not overwrite it — propose changes in your reply instead.
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
| `userNote` | | string | **App-written.** The traveler's own notes on the trip; a line starting `- ` is a bullet. Keep it exactly as given. |
| `exportedFrom` | | object | Written by the app on export: `{app, exportedAt, basedOnRevision, localChanges}`. |
| `validated` | | object | Written by `validate_trip.py --stamp` when the file passes: `{by, hash}`, a fingerprint of everything else in the file (whitespace and key order ignored, numbers as written). The import preview shows whether it matches. The app drops it on export. |

**Link** — `{ "label": "Planning sheet", "url": "https://…", "kind": "spreadsheet" | "chat" | "map" | "doc" | "booking" | "other" }`

**TripMap** — `{ "url"?, "imageUrl"?, "attribution"?, "note"? }`. `imageUrl` is a small overview picture showing the destinations, with `attribution` kept beside it. `url` (a custom Google map) is still read but no longer shown. The app draws its own overview map from stay coordinates either way.

**Place** — `{ "name"?, "query"?, "address"?, "lat"?, "lng"?, "placeId"?, "mapsUrl"? }`. Any one is enough. For "Open in Google Maps" the app prefers `mapsUrl`, then `placeId`, then `query`, then `lat`/`lng`, then `name` plus the stay's name. `query` is what you would type into Google Maps (`"Jardín de los Picaflores, Puerto Iguazú"`). Coordinates are what put a place on a map, so give them for every stay (the overview map) and every activity (the stay map, which numbers them so the traveler can see what is near what).

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
| `notes` | | string[] | Stay-level logistics. |
| `mapUrl` | | URL | A custom Google map for this stay. |
| `links` | | Link[] | |

**Lodging** — `{ "name"?, "place"?, "status": "booked" | "tentative" | "undecided", "checkIn"?, "checkOut"?, "ref"?, "url"?, "priority"?, "how"?, "notes"?, "price"? }`. Leave `name` out when no hotel is chosen yet. `price` is usually the nightly rate with `"unit": "night"`, which the app multiplies by the stay's nights; without `unit` it is the whole stay.

**Price** — `{ "amount": number, "max"?: number, "currency"?: "USD", "unit"?: "total" | "night" | "person", "note"? }`. `amount` alone is one figure; `amount` to `max` is a range. `unit` says what it is for: the whole item (the default), one night (lodging only; multiplied by the stay's nights), or one traveler (multiplied by the trip's `travelers`). A return ticket's later legs carry `amount` 0, so the fare counts once. `currency` is an ISO code and defaults to USD; assistants write USD, converted approximately, with the local price in `note`. Until the item is booked, a price is an estimate; once booked, it is what was paid. The app's Bookings screen totals them per currency.

**Booking guidance**, on lodging, a Booking and a commitment: `url` is where to book (the official site or the operator), `priority` how soon to book it, and `how` one or two sentences on how to book it. Priority 1 means book now: it sells out early, or it is airfare likely to jump soon. 2 means book a week or more ahead: venues that usually fill a week out, and any other airfare. 3 means it can be booked last minute without penalty or risk. The app raises an unbooked 2 to 1 once its date is two weeks away or less, since "a week or more ahead" has run out by then. The Bookings screen tags them P1 (red), P2 (orange) and P3 (green) and can sort by them.

**WorkRhythm** — `{ "days": ["mon","tue","wed","thu","fri"], "start": "08:00", "end": "15:00", "timezone"?: "America/Denver", "note"? }`. When `timezone` differs from the stay's, the app converts the hours to local time for each date, so a fixed-offset destination and a home zone that changes its clocks both come out right.

## Transfer

`{ "id", "from": stayId, "to": stayId, "date", "mode": "flight"|"train"|"bus"|"boat"|"ferry"|"car"|"taxi"|"hike"|"other", "depart"?: "HH:MM", "arrive"?: "HH:MM", "details"?, "booking"?: Booking }`

**Booking** — `{ "status": "booked"|"tentative"|"needed", "ref"?, "url"?, "priority"?: 1|2|3, "how"?, "price"?: Price }`. A flight, train, bus or boat transfer is listed on the Bookings screen even without one.

`mode` is the main vehicle; the app shows it as an icon. For `"other"`, the app shows the first vehicle the `details` name.

Times are local to the place they happen. The overview map draws transfers as straight dashed lines labelled by mode — never as driving routes.

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
| `booking` | | Booking | Set when it needs a reservation or ticket: `{ "status": "needed", "price": {…} }`. It then appears on the Bookings screen; booking it in the app adds a commitment linked to it. |
| `confidence` | | enum | `"confirmed"` (checked recently), `"estimate"`, `"check"` (verify before going). |
| `checkedOn` | | ISO date | When details were last verified. |
| `url`, `sources` | | URL, URL[] | Official site; where the facts came from. |
| `origin`, `userEdited`, `userNote` | | | **App-written.** See the rules above. |

Category tags: 🏛️ architecture · 🌿 nature · ☕ coffee · 🍽️ food · 🎨 art · 🛍️ shopping · 📸 viewpoint · 🏃 run/hike · 🧗 climbing · 🚶 walk · 🏡 neighborhood · 💻 work · 🚗 driving · 🎭 culture · 🌊 water · 🌲 forest · 🏔️ mountains · 📚 books · 🎵 music · 🍸 bar · 💃 dance · 🏊 swimming

## Commitment

Something fixed on a date that the plan must work around.

`{ "id", "title", "date", "start"?: "HH:MM", "end"?: "HH:MM", "kind": "booking"|"appointment"|"work"|"other", "booked"?: bool, "stayId"?, "place"?, "ref"?, "url"?, "priority"?, "how"?, "price"?: Price, "notes"?, "activityId"? }`

`booked: true` means a reservation exists outside the app. The app never moves it and warns before anything is planned on top of it. `activityId` links a booked tour to the activity it is for. A commitment of kind `booking`, or any booked one, appears on the Bookings screen. Recurring work hours belong in the stay's `workRhythm`, not here.

## Day

| Field | Req | Type | Meaning |
|---|---|---|---|
| `date` | ✓ | ISO date | |
| `stayId` | | id | Defaults to the stay covering that date. |
| `kind` | | enum | `"plan"` (default), `"work"`, `"rest"`, `"travel"`, `"free"`. A work, rest or travel day may have an empty plan — never invent an outing to fill it. |
| `title` | | string | A few words: `"Brazilian side"`. |
| `note` | | string | Only what the plan doesn't show: an order or timing that matters, in one line. `"Garden at opening, before the tour buses."` |
| `plan` | | Item[] | Activities placed on this day, in order. |
| `userEdited` | | string[] | **App-written.** |

**Item** — `{ "activityId", "slot": "morning"|"afternoon"|"evening"|"allday", "time"?: "HH:MM", "status"?: "proposed"|"done"|"skipped", "note"? }`. A `time` makes it a fixed-time item; without one it is loosely ordered within its slot. Status defaults to `proposed`.

## What the app does with a file

- **New trip id** → a preview (name, dates, stays, problems), then import.
- **Known trip id, same content** → "already imported", nothing changes.
- **Known trip id, new content** → a revision: the app compares the incoming file with the version it last imported and with the traveler's current plan, lists what changed, keeps the traveler's edits, asks about anything both sides changed, and keeps a snapshot so the import can be undone.
- **Lower `revision` or older `generatedAt` than the copy in use** → a warning before anything happens.
- **Export** writes the current plan — the traveler's edits included — in this same format, with `origin`, `userEdited` and `userNote` filled in, so an assistant can revise it and the app can merge the result.
