package dev.tlong.traveler.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads a trip file and says, in plain words, whether it can be imported. The checks mirror
 * tools/validate_trip.py: errors block the import, warnings are shown on the preview.
 * Both run against the same fixtures in schema/ (TripReaderTest).
 */
data class ReadResult(val trip: Trip?, val errors: List<String>, val warnings: List<String>) {
    val ok get() = trip != null && errors.isEmpty()
}

object TripReader {

    fun read(text: String): ReadResult {
        // JsonDecodingException is both a SerializationException and an IllegalArgumentException.
        val element = try {
            TripJson.reader.parseToJsonElement(TripJson.extractJson(text))
        } catch (e: SerializationException) {
            return fail("This is not a readable trip file: the JSON is broken (${e.message?.lineSequence()?.first()}).")
        }
        if (element !is JsonObject) return fail("This file holds JSON, but not a trip: expected one object.")
        val format = (element["format"] as? JsonPrimitive)?.content
        if (format != null && format != TRIP_FORMAT) {
            return fail("This is a '$format' file, not a Traveler trip (format must be \"$TRIP_FORMAT\").")
        }
        if (format == null && element["stays"] == null) {
            return fail("This JSON does not look like a Traveler trip: it has no \"format\" and no \"stays\".")
        }
        val version = (element["formatVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (version != null && version > TRIP_FORMAT_VERSION) {
            return fail("This trip uses format version $version; this app reads version $TRIP_FORMAT_VERSION. Update the app.")
        }
        // The decoder's own message for a malformed price says only "$.price", not which one.
        badPrices(element).takeIf { it.isNotEmpty() }?.let { return ReadResult(null, it, emptyList()) }
        val trip = try {
            TripJson.fromElement(element)
        } catch (e: SerializationException) {
            return fail(TripJson.plainError(e))
        } catch (e: IllegalArgumentException) {
            return fail(e.message ?: "The trip could not be read.")
        }
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (format == null) warnings += "No \"format\" field; read as a Traveler trip anyway."
        TripJson.unknownFields(element).forEach { warnings += "$it: unknown field (typo?) — ignored" }
        check(trip, errors, warnings)
        return ReadResult(trip, errors, warnings)
    }

    private fun fail(msg: String) = ReadResult(null, listOf(msg), emptyList())

    /** Every price that can't be read, named by where it is: "stays[0] (bsas).lodging.price: …". */
    private fun badPrices(e: JsonElement, path: String = ""): List<String> = when (e) {
        is JsonObject -> e.entries.flatMap { (k, v) ->
            val where = if (path.isEmpty()) k else "$path.$k"
            if (k == "price") listOfNotNull(priceProblem(v)?.let { "$where: $it" }) else badPrices(v, where)
        }
        is JsonArray -> e.flatMapIndexed { i, v ->
            badPrices(v, "$path[$i]" + ((v as? JsonObject)?.get("id") as? JsonPrimitive)?.let { " (${it.content})" }.orEmpty())
        }
        else -> emptyList()
    }

    private fun priceProblem(v: JsonElement): String? = when {
        v is JsonNull -> null
        v !is JsonObject -> "must be an object {amount, max?, currency?, note?}; got $v"
        (v["amount"] as? JsonPrimitive)?.doubleOrNull == null -> "missing required field 'amount' (a number); got $v"
        else -> null
    }

    fun check(trip: Trip, errors: MutableList<String>, warnings: MutableList<String>) {
        val start = date(trip.startDate, "startDate", errors)
        val end = date(trip.endDate, "endDate", errors)
        if (start != null && end != null && end < start) errors += "endDate is before startDate"
        if (trip.title.isBlank()) errors += "title is empty"
        if (trip.stays.isEmpty()) errors += "the trip has no stays"
        fun inTrip(d: LocalDate?) = d == null || start == null || end == null || d in start..end

        val stayIds = unique(trip.stays.map { it.id }, "stays", errors)
        unique(trip.transfers.map { it.id }, "transfers", errors)
        val activityIds = unique(trip.activities.map { it.id }, "activities", errors)
        unique(trip.commitments.map { it.id }, "commitments", errors)

        var previousArrive: LocalDate? = null
        trip.stays.forEachIndexed { i, s ->
            val where = "stays[$i] (${s.id})"
            val a = date(s.arrive, "$where.arrive", errors)
            val d = date(s.depart, "$where.depart", errors)
            if (a != null && d != null) {
                if (d < a) errors += "$where: depart is before arrive"
                if (!inTrip(a) || !inTrip(d)) warnings += "$where: dates fall outside the trip's dates"
                if (previousArrive != null && a < previousArrive) warnings += "$where: stays should be listed in visit order"
                previousArrive = a
            }
            if (s.timezone == null) warnings += "$where: no timezone — times are shown in the phone's zone"
            else zone(s.timezone, where, errors)
            s.workRhythm?.let { w ->
                w.timezone?.let { zone(it, "$where.workRhythm", errors) }
                time(w.start, "$where.workRhythm.start", errors); time(w.end, "$where.workRhythm.end", errors)
                w.days.forEach { enum(it, Vocab.weekdays, "$where.workRhythm.days", errors) }
            }
            if (s.place?.hasCoordinates != true) warnings += "$where: no coordinates — not shown on the overview map"
            s.lodging?.status?.let { enum(it, Vocab.lodgingStatuses, "$where.lodging.status", errors) }
            price(s.lodging?.price, "$where.lodging.price", errors)
            s.lodging?.priority?.let { priority(it, "$where.lodging.priority", errors) }
            checkPlace(s.place, where, errors)
        }
        trip.transfers.forEachIndexed { i, t ->
            val where = "transfers[$i] (${t.id})"
            if (t.from !in stayIds) errors += "$where: unknown stay '${t.from}'"
            if (t.to !in stayIds) errors += "$where: unknown stay '${t.to}'"
            date(t.date, "$where.date", errors)
            t.mode?.let { enum(it, Vocab.transferModes, "$where.mode", errors) }
            t.depart?.let { time(it, "$where.depart", errors) }
            t.arrive?.let { time(it, "$where.arrive", errors) }
            t.booking?.status?.let { enum(it, Vocab.bookingStatuses, "$where.booking.status", errors) }
            price(t.booking?.price, "$where.booking.price", errors)
            t.booking?.priority?.let { priority(it, "$where.booking.priority", errors) }
        }
        trip.activities.forEachIndexed { i, a ->
            val where = "activities[$i] (${a.id})"
            if (a.stayId !in stayIds) errors += "$where: unknown stay '${a.stayId}'"
            if (a.name.isBlank()) errors += "$where: name is empty"
            a.fit?.let { enum(it, Vocab.fits, "$where.fit", errors) }
            a.effort?.let { enum(it, Vocab.efforts, "$where.effort", errors) }
            a.stars?.let { if (it !in 1..3) errors += "$where.stars: must be 1, 2 or 3; got $it" }
            a.confidence?.let { enum(it, Vocab.confidences, "$where.confidence", errors) }
            a.bestTime.forEach { enum(it, Vocab.bestTimes, "$where.bestTime", errors) }
            a.origin?.let { enum(it, Vocab.origins, "$where.origin", errors) }
            a.availability.forEach { w ->
                w.days.forEach { enum(it, Vocab.weekdays, "$where.availability.days", errors) }
                w.start?.let { time(it, "$where.availability.start", errors) }
                w.end?.let { time(it, "$where.availability.end", errors) }
            }
            a.checkedOn?.let { date(it, "$where.checkedOn", errors) }
            a.booking?.status?.let { enum(it, Vocab.bookingStatuses, "$where.booking.status", errors) }
            price(a.booking?.price, "$where.booking.price", errors)
            a.booking?.priority?.let { priority(it, "$where.booking.priority", errors) }
            checkPlace(a.place, where, errors)
        }
        trip.commitments.forEachIndexed { i, c ->
            val where = "commitments[$i] (${c.id})"
            if (c.stayId != null && c.stayId !in stayIds) errors += "$where: unknown stay '${c.stayId}'"
            if (c.activityId != null && c.activityId !in activityIds) errors += "$where: unknown activity '${c.activityId}'"
            val d = date(c.date, "$where.date", errors)
            if (!inTrip(d)) warnings += "$where: date is outside the trip"
            c.start?.let { time(it, "$where.start", errors) }
            c.end?.let { time(it, "$where.end", errors) }
            c.kind?.let { enum(it, Vocab.commitmentKinds, "$where.kind", errors) }
            price(c.price, "$where.price", errors)
            c.priority?.let { priority(it, "$where.priority", errors) }
        }
        val seen = mutableSetOf<String>()
        trip.days.forEachIndexed { i, day ->
            val where = "days[$i] (${day.date})"
            val d = date(day.date, "$where.date", errors)
            if (!seen.add(day.date)) errors += "$where: a second entry for the same date"
            if (!inTrip(d)) warnings += "$where: date is outside the trip"
            if (day.stayId != null && day.stayId !in stayIds) errors += "$where: unknown stay '${day.stayId}'"
            day.kind?.let { enum(it, Vocab.dayKinds, "$where.kind", errors) }
            day.plan.forEachIndexed { j, item ->
                if (item.activityId !in activityIds) errors += "$where.plan[$j]: unknown activity '${item.activityId}'"
                enum(item.slot, Vocab.slots, "$where.plan[$j].slot", errors)
                item.status?.let { enum(it, Vocab.statuses, "$where.plan[$j].status", errors) }
                item.time?.let { time(it, "$where.plan[$j].time", errors) }
            }
        }
    }

    private fun checkPlace(p: Place?, where: String, errors: MutableList<String>) {
        if (p == null) return
        p.lat?.let { if (it !in -90.0..90.0) errors += "$where.place.lat: must be between -90 and 90" }
        p.lng?.let { if (it !in -180.0..180.0) errors += "$where.place.lng: must be between -180 and 180" }
    }

    private fun unique(ids: List<String>, label: String, errors: MutableList<String>): Set<String> {
        val seen = mutableSetOf<String>()
        ids.forEach { if (!seen.add(it)) errors += "$label: duplicate id '$it'" }
        return seen
    }

    private fun date(s: String, where: String, errors: MutableList<String>): LocalDate? =
        runCatching { LocalDate.parse(s) }.getOrElse { errors += "$where: \"$s\" is not a date (YYYY-MM-DD)"; null }

    private val timeRe = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    private fun time(s: String, where: String, errors: MutableList<String>) {
        if (!timeRe.matches(s)) errors += "$where: \"$s\" is not a time (HH:MM, 24-hour)"
    }

    private fun enum(value: String, allowed: List<String>, where: String, errors: MutableList<String>) {
        if (value !in allowed) errors += "$where: must be one of ${allowed.joinToString(", ")}; got \"$value\""
    }

    private fun price(p: Price?, where: String, errors: MutableList<String>) {
        if (p == null) return
        if (p.amount < 0 || (p.max ?: 0.0) < 0) errors += "$where: must not be negative"
        if (p.max != null && p.max < p.amount) errors += "$where: max is below amount"
        if (p.currency != null && !isCurrencyCode(p.currency)) errors += "$where.currency: \"${p.currency}\" is not a currency code like USD"
    }

    private fun priority(p: Int, where: String, errors: MutableList<String>) {
        if (p !in 1..3) errors += "$where: must be 1, 2 or 3; got $p"
    }

    private fun zone(tz: String, where: String, errors: MutableList<String>) {
        runCatching { ZoneId.of(tz) }.onFailure {
            errors += "$where: '$tz' is not an IANA time zone (e.g. America/Argentina/Buenos_Aires)"
        }
    }
}
