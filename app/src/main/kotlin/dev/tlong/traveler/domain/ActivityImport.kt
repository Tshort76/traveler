package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.ActivityFile
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.PlanItem
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip
import java.text.Normalizer
import java.time.LocalDate

/**
 * Fitting an activities file into a trip: which trip it is for, which stay each activity joins,
 * and which are already there. Adding never changes or removes anything the trip has, except that
 * the file's days replace those dates' suggested plans.
 */
object ActivityImport {

    data class Skip(val name: String, val reason: String)

    /** A file's day for [date]: [items] name trip activities; [replaced] is how many current items it takes off. */
    data class DayChange(val date: String, val items: List<PlanItem>, val replaced: Int)

    /** [add] carry the stay they join; [unmatched] are the file's ids whose stay fit none and went to the fallback. */
    data class Plan(val add: List<Activity>, val unmatched: Set<String>, val skipped: List<Skip>, val days: List<DayChange> = emptyList())

    /** What a file's day leaves in place: what the traveler did, booked or added themselves. */
    fun keeps(trip: Trip, item: PlanItem): Boolean {
        if (ItemStatus.of(item.status) == ItemStatus.DONE) return true
        val a = trip.activity(item.activityId) ?: return false
        return a.isCustom || trip.isBooked(a)
    }

    /** Replaces each day's plan with its kept items plus [days]' items; the trip must already hold the added activities. */
    fun applyDays(trip: Trip, days: List<DayChange>): Trip = days.fold(trip) { t, change ->
        val kept = t.day(change.date)?.plan.orEmpty().filter { keeps(t, it) }
        val keptIds = kept.map { it.activityId }.toSet()
        Edits.setPlan(t, change.date, kept + change.items.filter { it.activityId !in keptIds })
    }

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
        val named = trip.activities.associate { (it.stayId to slug(it.name)) to it.id }.toMutableMap()
        // The file's id → the trip's: a new id for one added, the existing one for a duplicate.
        val idOf = mutableMapOf<String, String>()
        val add = mutableListOf<Activity>()
        val unmatched = mutableSetOf<String>()
        val skipped = mutableListOf<Skip>()
        for (a in file.activities) {
            val matched = stayFor(trip, a.stayId, today)
            val stay = matched ?: trip.stay(fallbackStayId)
            if (stay == null) { skipped += Skip(a.name, "no stay to add it to"); continue }
            if (matched == null) unmatched += a.id
            named[stay.id to slug(a.name)]?.let { existing ->
                idOf[a.id] = existing
                skipped += Skip(a.name, "already in ${stay.name}")
                continue
            }
            var id = a.id
            var n = 2
            while (id in ids) id = "${a.id}-${n++}"
            ids += id
            idOf[a.id] = id
            named[stay.id to slug(a.name)] = id
            // A suggestion like any other: the app's own fields start empty.
            add += a.copy(id = id, stayId = stay.id, origin = null, userEdited = emptyList(), userNote = null, userStatus = null)
        }
        val days = file.days.mapNotNull { d ->
            val current = trip.day(d.date) ?: run { skipped += Skip(d.date, "not a day of this trip"); return@mapNotNull null }
            val items = d.plan.mapNotNull { item -> idOf[item.activityId]?.let { item.copy(activityId = it, status = null) } }
            DayChange(d.date, items, current.plan.count { !keeps(trip, it) })
        }
        return Plan(add, unmatched, skipped, days)
    }
}
