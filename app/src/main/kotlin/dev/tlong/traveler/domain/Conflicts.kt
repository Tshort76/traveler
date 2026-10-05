package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * What to tell the traveler before an activity lands somewhere. Nothing here blocks a move:
 * duration estimates are guidance, and an ambitious day is the traveler's call. A booked
 * commitment is never moved to make room; the warning is the whole response.
 */
data class PlacementIssue(val kind: Kind, val message: String) {
    enum class Kind { BOOKED, WORK, CLOSED, HOURS, OTHER_STAY, ALREADY_PLANNED }

    /** Issues worth stopping a move for; the rest are shown inline as information. */
    val blocking get() = kind in setOf(Kind.BOOKED, Kind.WORK, Kind.CLOSED, Kind.HOURS)
}

private data class Busy(val start: LocalTime, val end: LocalTime, val what: String)

private const val DEFAULT_MINUTES = 60L

fun checkPlacement(
    trip: Trip, activity: Activity, date: LocalDate, slot: Slot, time: LocalTime?, ignoreRef: Edits.ItemRef? = null,
): List<PlacementIssue> {
    val issues = mutableListOf<PlacementIssue>()
    val day = trip.day(date.toString())
    val stay = day?.let { trip.stayOf(it) } ?: trip.stayFor(date)
    val minutes = (activity.duration?.minutes?.toLong() ?: DEFAULT_MINUTES).coerceAtLeast(15)

    val busy = mutableListOf<Busy>()
    trip.commitments.filter { it.date == date.toString() && it.isBooked && it.activityId != activity.id }.forEach { c ->
        val s = c.start?.toTime() ?: return@forEach
        val e = c.end?.toTime() ?: s.plusMinutes(DEFAULT_MINUTES)
        busy += Busy(s, e, "booked: ${c.title} ${s.hhmm()}–${e.hhmm()}")
    }
    trip.transfers.filter { it.date == date.toString() }.forEach { t ->
        val s = t.depart?.toTime() ?: return@forEach
        val e = t.arrive?.toTime()?.takeIf { it > s } ?: s.plusMinutes(DEFAULT_MINUTES)
        busy += Busy(s.minusMinutes(90), e, "${t.mode ?: "transfer"} at ${s.hhmm()}")
    }
    val work = stay?.let { workHoursOn(it, date) }
    if (work != null) busy += Busy(work.start, work.end, "work ${work.label}")

    if (time != null) {
        val end = time.plusMinutes(minutes).let { if (it < time) LocalTime.MAX else it }
        busy.filter { it.start < end && time < it.end }.forEach { b ->
            issues += PlacementIssue(if (b.what.startsWith("work")) PlacementIssue.Kind.WORK else PlacementIssue.Kind.BOOKED,
                "${time.hhmm()} overlaps ${b.what}.")
        }
    } else {
        // A loose slot is fine as long as enough of it is free; say what fills it otherwise.
        val window = slot.window()
        val inSlot = busy.filter { it.start < window.endInclusive && window.start < it.end }
        val free = freeMinutes(window, inSlot)
        if (inSlot.isNotEmpty() && free < minutes) {
            val kind = if (inSlot.any { !it.what.startsWith("work") }) PlacementIssue.Kind.BOOKED else PlacementIssue.Kind.WORK
            val what = inSlot.joinToString("; ") { it.what }
            issues += PlacementIssue(kind, "The ${slot.label.lowercase()} is taken by $what — about $free min free for a ${minutes}-min activity.")
        }
    }

    if (activity.availability.isNotEmpty()) {
        val weekday = date.weekdayKey()
        val windows = activity.availability.filter { it.days.isEmpty() || weekday in it.days }
        val note = activity.availability.firstNotNullOfOrNull { it.note }?.let { " ($it)" }.orEmpty()
        if (windows.isEmpty()) {
            issues += PlacementIssue(PlacementIssue.Kind.CLOSED, "${activity.name} is not open on ${date.weekdayShort()}s$note.")
        } else if (time != null) {
            val fits = windows.any { w ->
                val s = w.start?.toTime() ?: LocalTime.MIN
                val e = w.end?.toTime() ?: LocalTime.MAX
                time >= s && time < e
            }
            if (!fits) {
                val hours = windows.mapNotNull { w -> if (w.start != null && w.end != null) "${w.start}–${w.end}" else null }.distinct()
                issues += PlacementIssue(PlacementIssue.Kind.HOURS, "${time.hhmm()} is outside its hours (${hours.joinToString(", ")})$note.")
            }
        }
    }

    if (stay != null && activity.stayId != stay.id) {
        val home = trip.stay(activity.stayId)?.name ?: activity.stayId
        issues += PlacementIssue(PlacementIssue.Kind.OTHER_STAY, "This is a ${home} suggestion; this day is in ${stay.name}.")
    }

    val elsewhere = trip.placementsOf(activity.id).filterNot { ignoreRef != null && it.date == ignoreRef.date && it.index == ignoreRef.index }
    if (elsewhere.isNotEmpty()) {
        val where = elsewhere.mapNotNull { it.date.toDate()?.label() }.distinct().joinToString(", ")
        issues += PlacementIssue(PlacementIssue.Kind.ALREADY_PLANNED, "Already planned on $where — adding it again is fine if you mean to.")
    }
    return issues
}

private fun freeMinutes(window: ClosedRange<LocalTime>, busy: List<Busy>): Long {
    var cursor = window.start
    var free = 0L
    busy.sortedBy { it.start }.forEach { b ->
        val s = maxOf(b.start, window.start)
        if (s > cursor) free += ChronoUnit.MINUTES.between(cursor, s)
        if (b.end > cursor) cursor = minOf(b.end, window.endInclusive)
    }
    if (window.endInclusive > cursor) free += ChronoUnit.MINUTES.between(cursor, window.endInclusive)
    return free
}

/**
 * Day-level heads-up shown once at the top of a day: timed items that collide with a booking.
 * Work hours are shown as a chip instead, so they are not repeated here for every item.
 */
fun dayWarnings(trip: Trip, date: LocalDate): List<String> {
    val day = trip.day(date.toString()) ?: return emptyList()
    return day.plan.mapIndexedNotNull { i, item ->
        val a = trip.activity(item.activityId) ?: return@mapIndexedNotNull null
        val t = item.time?.toTime() ?: return@mapIndexedNotNull null
        checkPlacement(trip, a, date, Slot.of(item.slot), t, Edits.ItemRef(day.date, i))
            .firstOrNull { it.kind == PlacementIssue.Kind.BOOKED }?.let { "${a.name}: ${it.message}" }
    }
}
