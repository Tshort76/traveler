package dev.tlong.traveler.domain

import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Place
import dev.tlong.traveler.model.Trip
import java.time.LocalTime

/** One timed thing on a day, as a calendar event in the stay's local time. [end] before [start] means it ends the next day. */
data class DayEvent(
    val key: String,
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val location: String? = null,
    val description: String? = null,
    val work: Boolean = false,
)

private const val DEFAULT_MINUTES = 60L

/**
 * The day's timed items as calendar events: planned activities with a time (not skipped),
 * bookings with a start, transfers with a departure, and the work [blocks]. Anything without a
 * time is left out, since a calendar needs one.
 */
fun dayEvents(trip: Trip, date: String, blocks: List<WorkBlock>): List<DayEvent> {
    val events = mutableListOf<DayEvent>()
    trip.day(date)?.plan?.forEachIndexed { i, item ->
        val start = item.time?.toTime() ?: return@forEachIndexed
        if (ItemStatus.of(item.status) == ItemStatus.SKIPPED) return@forEachIndexed
        val a = trip.activity(item.activityId) ?: return@forEachIndexed
        val booking = trip.commitments.firstOrNull { it.activityId == a.id && it.date == date }
        events += DayEvent(
            "i-$i-${a.id}", listOfNotNull(a.tag, a.name).joinToString(" "),
            start, start.plusMinutes(a.duration?.minutes?.toLong() ?: DEFAULT_MINUTES),
            locationOf(a.place),
            listOfNotNull(a.short, booking?.ref?.let { "Confirmation: $it" }, a.url).joinToString("\n").ifEmpty { null },
        )
    }
    trip.commitments.filter { it.date == date && it.activityId == null }.forEach { c ->
        val start = c.start?.toTime() ?: return@forEach
        events += DayEvent(
            "c-${c.id}", c.title, start, c.end?.toTime()?.takeIf { it > start } ?: start.plusMinutes(DEFAULT_MINUTES),
            locationOf(c.place),
            listOfNotNull(c.ref?.let { "Confirmation: $it" }, c.notes, c.url).joinToString("\n").ifEmpty { null },
        )
    }
    trip.transfers.filter { it.date == date }.forEach { t ->
        val start = t.depart?.toTime() ?: return@forEach
        val mode = (t.shownMode ?: "transfer").replaceFirstChar { it.uppercase() }
        events += DayEvent("t-${t.id}", listOfNotNull(mode, t.details).joinToString(": "), start, t.arrive?.toTime() ?: start.plusMinutes(DEFAULT_MINUTES))
    }
    blocks.forEach { b -> events += DayEvent("w-${b.start}", "Work", b.startTime, b.endTime, work = true) }
    return events.sortedBy { it.start }
}

private fun locationOf(p: Place?): String? =
    p?.address ?: p?.query ?: p?.name ?: if (p?.hasCoordinates == true) "${p.lat},${p.lng}" else null
