# Lodging search and comparison

Help the traveler quickly find, compare, and open credible lodging options for a stay. The default deliverable is a ranked Markdown table in chat. Approximate prices are sufficient; exact costs, live availability, and checkout checks can come later.

## Scope and routing

Use this workflow for requests to find or compare hotels, hostels, apartments, Airbnbs, accommodation, or places to stay. It supplements the Travel project's app + JSON workflow.

- Use the current trip JSON and itinerary guidance for dates, location, preferred neighborhoods, guests, and relevant constraints. An attached trip file is context, not a request to revise it.
- Keep the trip JSON, itinerary, and project instructions unchanged during a lodging-only search. Do not generate HTML, an itinerary, a workbook, or a new trip revision unless requested.
- Neighborhood selection, working conditions, running access, and broad privacy/social trade-offs normally belong to itinerary planning. Reuse those decisions. Briefly flag a property-level issue only when it materially affects the choice or violates an explicit requirement.
- This workflow researches options and provides booking links. It does not make reservations, payments, or contact hosts.

## Resolve the search

1. Extract destination/preferred area, check-in and check-out dates, guests/rooms, optional budget, and allowed accommodation types from current instructions and trip data. Nights are check-out minus check-in.
2. Ask one compact question only when missing information prevents a useful search. Unknown dates or budget need not block an indicative shortlist; state assumptions. Do not ask again for information already supplied. A missing room/guest count may be assumed only if clearly labeled and appropriate to the context.
3. Respect hard constraints. When accommodation type is open, include meaningfully different choices such as an economical private hostel room, a hotel, and an apartment where suitable. Do not add dorms when private accommodation was requested.

## Search and shortlist

- Use current web research. Search Booking.com, Hostelworld, Airbnb, and direct property websites as appropriate to the destination and accommodation types. These are sources to consider, not quotas requiring an option from every platform.
- Aim for 5–8 distinct, named properties per stay, ranked by fit and value. Give fewer when evidence or suitable supply is limited; never pad the table with weak or invented options.
- Prefer credible review histories. Show each platform's own rating scale and review count; do not silently normalize scores or equate ratings across platforms. If unavailable, say so.
- Check accessible recent reviews for recurring cleanliness, noise, maintenance, or reservation problems. Separate guest reports from property claims. Do not claim to have inspected reviews you could not access.
- Deduplicate the same property across platforms. Distinguish room categories where they change the decision. Add an alternate booking link only if useful.
- If a platform is blocked, requires login, or fails to expose prices, continue with other sources. Briefly disclose the coverage gap rather than retrying indefinitely or abandoning the shortlist. Do not imply exhaustive market coverage.

## Prices and booking links

- Show approximate USD per night as a rounded amount or range, labeled **indicative** unless checked for the requested dates and occupancy. Identify the basis: per dorm bed, per private room, or per entire unit. Do not present a dorm-bed rate as a private-room rate.
- Ground estimates in current property/room prices when possible. A broader market estimate must be labeled as such. If there is no defensible price basis, use **price unavailable** rather than inventing a figure.
- Flag known substantial fees or excluded taxes. Do not chase checkout totals, exact cancellation deadlines, or room availability unless requested. A rough stay total is optional; label arithmetic from an indicative nightly rate as an estimate, not a quote.
- A listing being visible does not prove availability. Say **availability unchecked** by default. Claim availability only if observed for the requested dates, guests, and room category, and give the check date.
- Link the actual property page on a booking platform or the property's official booking site. Add dates/guests only through a supported URL you can verify. Do not fabricate listing IDs, URLs, or booking endpoints.
- If only a property-specific search link is available, label it **search link**. Prefer options with usable property links and disclose when links or details could not be verified.
- Include the research date once above the table. Use property links as sources for listing facts; cite a separate source for material claims not supported there. Keep uncertain facts visibly uncertain.

## Output

Start with a direct recommendation: “I'd start with X; choose Y for a cheaper option or Z for more comfort.” Recommend only what the evidence supports.

Then state the location, dates/nights if known, guests/rooms, assumptions, and research date in one compact line. Unless verified otherwise, note once: “Prices are indicative; availability is unchecked.”

| Option / booking link | Type / room | Approx. USD/night | Location | Rating | Main advantage | Main drawback |
| --- | --- | --- | --- | --- | --- | --- |

Populate one row per shortlisted property. Put the rank and linked property name in the first column. Include bathroom sharing in the room description when known. For location, use the neighborhood or relation to the itinerary's preferred area; add a useful proximity detail only when supported. Avoid unsupported precise walking times or generic “safe area” claims. Give concrete, decision-relevant advantages and drawbacks; use “not established” when evidence is missing instead of fabricating a criticism.

Finish with only material caveats, source-access limitations, or checks needed before booking. Keep the table easy to scan. No elaborate scoring system, lengthy area analysis, or unsolicited export.

## After the traveler chooses

A choice does not imply a reservation exists. Offer or perform a trip-file update only when the traveler requests it. For such an update, follow traveler-trip.md and the current project rules, preserve IDs and user edits, use the existing schema, and run the required validator and stamping procedure. Never mark lodging booked without an actual reservation. Do not add shortlist fields to the schema or turn candidate properties into activities.
