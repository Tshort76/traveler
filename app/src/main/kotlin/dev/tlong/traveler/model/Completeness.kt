package dev.tlong.traveler.model

import java.time.LocalDate

/**
 * The checks `validate_trip.py --complete` adds, in its words: what the assistant left out or made
 * up. None blocks an import. The preview lists them as problems to send back, because an assistant
 * that skipped the validator is the usual reason a file has them.
 */
object Completeness {
    private val ticketed = setOf("flight", "train", "bus", "boat", "ferry")
    private val flightWord = Regex("\\bflights?\\b", RegexOption.IGNORE_CASE)

    fun problems(trip: Trip, unknownFields: List<String>): List<String> {
        val out = mutableListOf<String>()
        // One line per field, not per occurrence: an assistant that invents a field uses it everywhere.
        unknownFields.groupBy { it.replace(Regex("\\[\\d+\\]"), "[]") }.forEach { (field, at) ->
            out += "${if (at.size == 1) at[0] else field}: not part of the format; the app drops it" +
                (if (at.size > 1) " (${at.size} places)" else "") + ". Use the field trip-format.md defines"
        }
        prices(trip).filter { (_, p) -> (p.currency ?: "USD") != "USD" }.forEach { (where, p) ->
            out += "$where: prices are in USD, as an approximate conversion; got ${p.currency}. Put the local price in its note"
        }
        val first = trip.stays.firstOrNull()
        val last = trip.stays.lastOrNull()
        if (first != null && last != null && (trip.startDate < first.arrive || trip.endDate > last.depart)) {
            out += "stays: the trip starts before the first stay or ends after the last: add the home airport as a " +
                "zero-night stay first and last, and the flights as transfers to and from it"
        }
        trip.commitments.filter { it.origin != ORIGIN_USER && !it.isBooked && flightWord.containsMatchIn(it.title) }.map { it.id }
            .takeIf { it.isNotEmpty() }?.let {
                out += "commitments: a flight still to book is a transfer between stays (from the home-airport stay for the way out), not a commitment: ${names(it)}"
            }
        val acts = trip.activities.filter { it.origin != ORIGIN_USER }
        acts.filter { it.stars == null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
            out += "activities: ${it.size} have no stars (1–3): ${names(it)}"
        }
        acts.filter { it.place?.hasCoordinates != true }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
            out += "activities: ${it.size} have no place.lat/lng, so the stay map can't show them: ${names(it)}. Give every activity's coordinates"
        }
        acts.filter { it.practical?.booking != null && it.booking == null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
            out += "activities: booking advice only in practical.booking, with no booking object {status, priority, price, url?, how}: ${names(it)}"
        }
        trip.stays.forEach { s ->
            val nights = runCatching { LocalDate.parse(s.depart) > LocalDate.parse(s.arrive) }.getOrDefault(false)
            val lod = s.lodging
            if (nights && lod?.status != "booked") missing(lod?.price, lod?.priority)?.let { out += "stay ${s.id}.lodging: needs $it" }
        }
        trip.transfers.forEach { t ->
            val b = t.booking
            if (b == null && t.mode in ticketed) out += "transfer ${t.id}: a ticketed transfer needs a booking {status, priority, price, how}"
            else if (b != null && b.status != "booked") missing(b.price, b.priority)?.let { out += "transfer ${t.id}.booking: needs $it" }
        }
        val bookedVia = trip.commitments.filter { it.booked == true }.mapNotNull { it.activityId }.toSet()
        acts.forEach { a ->
            val b = a.booking ?: return@forEach
            if (b.status != "booked" && a.id !in bookedVia) missing(b.price, b.priority)?.let { out += "activity ${a.id}.booking: needs $it" }
        }
        acts.filter { it.booking?.price != null }.groupBy { it.name }.filterValues { it.size > 1 }.forEach { (name, list) ->
            out += "activities: '$name' is listed in ${list.size} stays with a price; if more than one is planned, its price counts more than once. List it once, under the visit it suits"
        }
        return out
    }

    private fun missing(price: Price?, priority: Int?): String? =
        listOfNotNull("price".takeIf { price == null }, "priority".takeIf { priority == null }).takeIf { it.isNotEmpty() }?.joinToString(" and ")

    private fun names(ids: List<String>, limit: Int = 6) =
        ids.take(limit).joinToString(", ") + if (ids.size > limit) " and ${ids.size - limit} more" else ""

    private fun prices(trip: Trip): List<Pair<String, Price>> = buildList {
        trip.stays.forEachIndexed { i, s -> s.lodging?.price?.let { add("stays[$i].lodging.price" to it) } }
        trip.transfers.forEachIndexed { i, t -> t.booking?.price?.let { add("transfers[$i].booking.price" to it) } }
        trip.activities.forEachIndexed { i, a -> a.booking?.price?.let { add("activities[$i].booking.price" to it) } }
        trip.commitments.forEachIndexed { i, c -> c.price?.let { add("commitments[$i].price" to it) } }
    }
}
