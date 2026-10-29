package dev.tlong.traveler.domain

import dev.tlong.traveler.model.PlanItem
import dev.tlong.traveler.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * One booking as a calendar event, for the phone's calendar app to open prefilled. Times are epoch
 * millis; an all-day entry's are UTC midnights with an exclusive end, as Android's calendar expects.
 */
data class CalendarEntry(
    val title: String, val begin: Long, val end: Long, val allDay: Boolean,
    val location: String? = null, val description: String? = null,
)

/**
 * A booking on [date]. With a [start] it runs to [end] (the next day when that comes first, as on an
 * overnight flight), or an hour; the times are local to [startZone] and [endZone], which differ for a
 * flight between time zones. Without a start it is all day, through [lastDate].
 */
fun calendarEntry(
    title: String, date: LocalDate, start: LocalTime?, end: LocalTime?, startZone: ZoneId, endZone: ZoneId = startZone,
    lastDate: LocalDate = date, location: String? = null, details: List<String?> = emptyList(),
): CalendarEntry {
    val description = details.filterNot { it.isNullOrBlank() }.joinToString("\n").ifEmpty { null }
    if (start == null) {
        fun midnight(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEntry(title, midnight(date), midnight(maxOf(lastDate, date).plusDays(1)), true, location, description)
    }
    val begin = ZonedDateTime.of(date, start, startZone)
    val finish = end?.let { ZonedDateTime.of(date, it, endZone) }?.let { if (it.isAfter(begin)) it else it.plusDays(1) } ?: begin.plusHours(1)
    return CalendarEntry(title, begin.toInstant().toEpochMilli(), finish.toInstant().toEpochMilli(), false, location, description)
}

/**
 * A booked item as it was saved: lodging all day from check-in through check-out day, a transfer
 * from departure to arrival in each end's time zone, anything else at its time (for its booking's
 * length or the suggestion's duration) or all day. Null when it has no date.
 */
fun Trip.calendarEntry(b: Bookable): CalendarEntry? {
    val date = b.date?.toDate() ?: return null
    val booked = listOf(b.ref?.let { "Confirmation: $it" }, b.url)
    fun zoneOf(stayId: String?) = stay(stayId)?.zone ?: ZoneId.systemDefault()
    return when (b.kind) {
        Bookable.Kind.LODGING -> {
            val s = stay(b.stayId) ?: return null
            calendarEntry(
                b.title, s.arriveDate, null, null, s.zone, lastDate = s.departDate, location = locationOf(s.lodging?.place),
                details = listOf(s.lodging?.checkIn?.let { "Check-in $it" }) + booked + s.lodging?.notes,
            )
        }
        Bookable.Kind.TRANSFER -> {
            val t = transfers.firstOrNull { it.id == b.id } ?: return null
            calendarEntry(
                listOfNotNull(t.shownMode?.replaceFirstChar { it.uppercase() }, b.title).joinToString(" "), date,
                t.depart?.toTime(), t.arrive?.toTime(), zoneOf(t.from), zoneOf(t.to), details = listOf(t.details) + booked,
            )
        }
        Bookable.Kind.ACTIVITY, Bookable.Kind.BOOKING -> {
            val c = b.record
            val a = (c?.activityId ?: b.id.takeIf { b.kind == Bookable.Kind.ACTIVITY })?.let { activity(it) }
            val start = b.time?.toTime()
            val end = c?.end?.toTime() ?: a?.duration?.minutes?.let { start?.plusMinutes(it.toLong()) }
            calendarEntry(
                b.title, date, start, end, stayFor(date)?.zone ?: zoneOf(b.stayId), location = locationOf(c?.place ?: a?.place),
                details = booked + c?.notes,
            )
        }
    }
}

/**
 * One planned activity on [date]: at its time for its duration. One with no time goes where the day
 * calendar [drawn] it (minutes from midnight), or all day.
 */
fun Trip.calendarEntry(date: String, item: PlanItem, drawn: Span? = null): CalendarEntry? {
    val d = date.toDate() ?: return null
    val a = activity(item.activityId) ?: return null
    fun clock(m: Int) = LocalTime.of(m / 60 % 24, m % 60)
    val start = item.time?.toTime() ?: drawn?.let { clock(it.start) }
    val end = a.duration?.minutes?.let { start?.plusMinutes(it.toLong()) } ?: drawn?.takeIf { item.time == null }?.let { clock(it.end) }
    return calendarEntry(
        a.name, d, start, end, stayFor(d)?.zone ?: ZoneId.systemDefault(),
        location = locationOf(a.place), details = listOf(a.short, a.url),
    )
}

/**
 * The begin and end a calendar app's new-event screen expects. It reads an all-day event's dates at
 * midnight in the phone's [zone], not in UTC, where they would land on the day before west of Greenwich.
 */
fun CalendarEntry.insertTimes(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    if (!allDay) return begin to end
    fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return local(begin) to local(end)
}
