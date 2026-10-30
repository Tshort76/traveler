package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.ActivityFile
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip
import java.text.Normalizer
import java.time.LocalDate

/**
 * Fitting an activities file into a trip: which trip it is for, which stay each activity joins,
 * and which are already there. Adding never changes or removes anything the trip has.
 */
object ActivityImport {

    data class Skip(val name: String, val reason: String)

    /** [add] carry the stay they join; [unmatched] are the file's ids whose stay fit none and went to the fallback. */
    data class Plan(val add: List<Activity>, val unmatched: Set<String>, val skipped: List<Skip>)

    /** Lowercase, no accents, words joined by hyphens: "Puerto Iguazú" → "puerto-iguazu". */
    fun slug(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * The stay [hint] names: its id, or a slug of its name or of the start of its name. A city
     * visited twice matches its current or next visit.
     */
    fun stayFor(trip: Trip, hint: String, today: LocalDate): Stay? {
        trip.stays.firstOrNull { it.id == hint }?.let { return it }
        val h = slug(hint).ifEmpty { return null }
        val exact = trip.stays.filter { slug(it.name) == h }
        val found = exact.ifEmpty { trip.stays.filter { s -> slug(s.name).let { it.startsWith("$h-") } || s.id.startsWith(h) } }
        return found.firstOrNull { it.departDate >= today } ?: found.firstOrNull()
    }

    /** Under way first, then upcoming by start date, then the rest, most recent first. */
    fun ordered(trips: List<Trip>, today: LocalDate): List<Trip> = trips.sortedWith(
        compareBy<Trip> { if (today in it.start..it.end) 0 else if (it.start > today) 1 else 2 }
            .thenBy { if (it.start > today) it.start.toEpochDay() else -it.start.toEpochDay() },
    )

    /** The trip the file names, else the one most of its activities' stays fit, else the first of [ordered]. */
    fun pickTrip(file: ActivityFile, trips: List<Trip>, today: LocalDate): Trip? {
        val list = ordered(trips, today)
        list.firstOrNull { it.id == file.tripId }?.let { return it }
        return list.maxByOrNull { t -> file.activities.count { stayFor(t, it.stayId, today) != null } }
    }

    /** The stay an unmatched activity defaults to: the current or next stay, else the last. */
    fun defaultStay(trip: Trip, today: LocalDate): Stay? =
        trip.stays.firstOrNull { it.departDate > today && it.nights > 0 } ?: trip.stays.lastOrNull()

    fun plan(trip: Trip, file: ActivityFile, fallbackStayId: String?, today: LocalDate): Plan {
        val ids = trip.activities.map { it.id }.toMutableSet()
        val names = trip.activities.map { it.stayId to slug(it.name) }.toMutableSet()
        val add = mutableListOf<Activity>()
        val unmatched = mutableSetOf<String>()
        val skipped = mutableListOf<Skip>()
        for (a in file.activities) {
            val matched = stayFor(trip, a.stayId, today)
            val stay = matched ?: trip.stay(fallbackStayId)
            if (stay == null) { skipped += Skip(a.name, "no stay to add it to"); continue }
            if (matched == null) unmatched += a.id
            if (!names.add(stay.id to slug(a.name))) { skipped += Skip(a.name, "already in ${stay.name}"); continue }
            var id = a.id
            var n = 2
            while (id in ids) id = "${a.id}-${n++}"
            ids += id
            // A suggestion like any other: the app's own fields start empty.
            add += a.copy(id = id, stayId = stay.id, origin = null, userEdited = emptyList(), userNote = null, userStatus = null)
        }
        return Plan(add, unmatched, skipped)
    }
}
