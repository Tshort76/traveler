# My Maps export

`python3 tools/make_map.py <trip-file>` turns a `.trip.json` export into one KMZ file for My Maps, with a folder per layer: one import gives every layer. The assistant's itinerary is the source of truth; this only draws it.

## Steps

1. **Find the file.** Use the path given. Given a name or nothing, look for the newest `*.trip*.json` in `~/Downloads` whose `format` is `traveler-trip` and whose title or id matches; if several match, list them and ask. Trip files are personal: read them in place, never copy one into this repository.
2. **Build it:** `python3 tools/make_map.py <file>`. It writes `~/Downloads/<trip-id>-map.kmz`, replacing an earlier one, and prints each layer with its count.
3. **Report** the layers and their counts, and anything missing: stays or activities without coordinates aren't drawn, and a flight, bus or train without `fromPlace`/`toPlace` is drawn between the stays instead of its airports or stations. Fixing those is a request for the ChatGPT Project, not an edit here.
4. **Give the import steps:**
   - New map: mymaps.google.com → **Create a new map**, name it after the trip. In the first layer, **Import** `<trip-id>-map.kmz`. Each folder in it becomes a layer (Overview, then one per stay).
   - A new export of a trip already mapped: delete the map and import again; My Maps can't update a layer in place.
   - On the phone: Google Maps → **You** → **Maps** opens it, and its layers can be hidden there.


## What the map holds

- **Overview:** each stay (dates, nights, summary), its lodging (booked or not, with a Google Maps link), the airports and stations, and each leg between towns (flight, long bus or train, ferry, a drive) as a straight line from terminal to terminal, or stay to stay; a ride across town is left out. The home airport, a zero-night stay that opens or closes the trip, is left out with its flights; the airport at their far end stays.
- **Points** are thin hollow rings, not teardrop pins, so the place underneath stays visible and tappable.
- **One layer per stay:** every activity with coordinates, a blue ring when planned (with its days) and grey when not; each pin has the short description, stars and an Open in Google Maps link, plus Stay, Status and Day as data columns for restyling in My Maps. Past My Maps' 10 layers, the stays with the fewest activities merge into "Other stays".

Days are not layers: the app's day screen gives directions through the day's stops.
