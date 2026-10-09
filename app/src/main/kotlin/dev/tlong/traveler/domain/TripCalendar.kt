package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Day
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.WorkRhythm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun String.toDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
fun String.toTime(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

val Trip.start: LocalDate get() = startDate.toDate() ?: LocalDate.MIN
val Trip.end: LocalDate get() = endDate.toDate() ?: start

val Stay.arriveDate: LocalDate get() = arrive.toDate() ?: LocalDate.MIN
val Stay.departDate: LocalDate get() = depart.toDate() ?: arriveDate
val Stay.nights: Int get() = ChronoUnit.DAYS.between(arriveDate, departDate).toInt().coerceAtLeast(0)
val Stay.zone: ZoneId get() = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

fun Trip.dates(): List<LocalDate> {
    if (end < start) return listOf(start)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
}

/**
 * The stay a date belongs to: the one whose nights cover it, or on a travel day the one being
 * arrived at. A last day with no stay starting on it belongs to the stay that ends there.
 */
fun Trip.stayFor(date: LocalDate): Stay? =
    stays.lastOrNull { date >= it.arriveDate && date < it.departDate }
        ?: stays.lastOrNull { it.arriveDate == date }
        ?: stays.lastOrNull { it.departDate == date }
        ?: stays.lastOrNull { it.arriveDate <= date }
        ?: stays.firstOrNull()

fun Trip.stay(id: String?): Stay? = stays.firstOrNull { it.id == id }

/**
 * The home airport: a zero-night stay that opens or closes the trip, there so the flights out and
 * back have somewhere to start and end. It is never a destination: no card, no map pin, no count.
 */
fun Trip.isHome(s: Stay): Boolean = s.nights == 0 && stays.size > 1 && (s.id == stays.first().id || s.id == stays.last().id)

/** The stays worth showing, in visit order: every stay but the home airport. */
val Trip.destinations: List<Stay> get() = stays.filterNot { isHome(it) }

fun Trip.day(date: String): Day? = days.firstOrNull { it.date == date }

fun Trip.stayOf(day: Day): Stay? = stay(day.stayId) ?: day.date.toDate()?.let { stayFor(it) }

/** Dates of a stay, arrival day through the day before departure (the last day too when it ends the trip). */
fun Trip.datesOf(stay: Stay): List<LocalDate> = dates().filter { d ->
    val dayStay = day(d.toString())?.stayId?.let { stay(it) } ?: stayFor(d)
    dayStay?.id == stay.id
}

/**
 * Every date of the trip gets a Day, in order, each with its stay set. Days an assistant left
 * out come back empty; [compact] removes them again before export.
 */
fun Trip.normalized(): Trip {
    val byDate = days.associateBy { it.date }
    val all = dates().map { d ->
        val key = d.toString()
        val existing = byDate[key]
        val stayId = existing?.stayId ?: stayFor(d)?.id
        existing?.copy(stayId = stayId, plan = Edits.chronological(existing.plan)) ?: Day(date = key, stayId = stayId)
    }
    val outside = days.filter { it.date.toDate()?.let { d -> d < start || d > end } ?: true }
    return copy(days = (all + outside).sortedBy { it.date })
}

/** Drops the empty days [normalized] added, so an exported file stays as small as it came in. */
fun Trip.compact(): Trip = copy(days = days.filterNot {
    it.note == null && it.plan.isEmpty() && it.kind == null && it.title == null && it.userEdited.isEmpty()
})

/** Groups a long stay's dates into weeks for readability; a short stay is one group. */
fun weeks(dates: List<LocalDate>): List<List<LocalDate>> =
    if (dates.size <= 8) listOf(dates) else dates.chunked(7)

enum class TripPhase { UPCOMING, ACTIVE, PAST }

fun Trip.phase(today: LocalDate = LocalDate.now()): TripPhase = when {
    today < start -> TripPhase.UPCOMING
    today > end -> TripPhase.PAST
    else -> TripPhase.ACTIVE
}

/** Clock window a slot stands for, used only to detect overlaps with fixed times. */
fun Slot.window(): ClosedRange<LocalTime> = when (this) {
    Slot.MORNING -> LocalTime.of(6, 0)..LocalTime.of(12, 0)
    Slot.AFTERNOON -> LocalTime.of(12, 0)..LocalTime.of(17, 0)
    Slot.EVENING -> LocalTime.of(17, 0)..LocalTime.of(23, 59)
    Slot.ALLDAY -> LocalTime.of(6, 0)..LocalTime.of(23, 59)
}

fun slotForTime(t: LocalTime): Slot = when {
    t < LocalTime.NOON -> Slot.MORNING
    t < LocalTime.of(17, 0) -> Slot.AFTERNOON
    else -> Slot.EVENING
}

private val weekdayKeys = mapOf(
    DayOfWeek.MONDAY to "mon", DayOfWeek.TUESDAY to "tue", DayOfWeek.WEDNESDAY to "wed",
    DayOfWeek.THURSDAY to "thu", DayOfWeek.FRIDAY to "fri", DayOfWeek.SATURDAY to "sat", DayOfWeek.SUNDAY to "sun",
)

fun LocalDate.weekdayKey(): String = weekdayKeys.getValue(dayOfWeek)

/** Work hours on one date, in the stay's local time. */
data class WorkHours(val start: LocalTime, val end: LocalTime, val sourceLabel: String?, val note: String?) {
    val label get() = "${start.hhmm()}–${end.hhmm()}"
}

/**
 * Converts the stay's work rhythm to local clock time for [date]. A rhythm kept in a home zone
 * that changes its clocks lands on different local hours either side of the change, which is
 * why this is per date rather than once per stay.
 */
fun workHoursOn(stay: Stay, date: LocalDate): WorkHours? {
    val w: WorkRhythm = stay.workRhythm ?: return null
    val days = w.days.ifEmpty { listOf("mon", "tue", "wed", "thu", "fri") }
    val s = w.start.toTime() ?: return null
    val e = w.end.toTime() ?: return null
    val workZone = w.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: stay.zone
    // The rhythm's weekdays are the worker's weekdays, so take the work-zone date at local noon.
    val workDate = ZonedDateTime.of(date, LocalTime.NOON, stay.zone).withZoneSameInstant(workZone).toLocalDate()
    if (workDate.weekdayKey() !in days) return null
    val start = ZonedDateTime.of(workDate, s, workZone).withZoneSameInstant(stay.zone)
    val end = ZonedDateTime.of(workDate, e, workZone).withZoneSameInstant(stay.zone)
    val clampedStart = if (start.toLocalDate() < date) LocalTime.MIN else start.toLocalTime()
    val clampedEnd = if (end.toLocalDate() > date) LocalTime.MAX else end.toLocalTime()
    val source = if (workZone != stay.zone) "${w.start}–${w.end} ${shortZone(workZone, date)}" else null
    return WorkHours(clampedStart, clampedEnd, source, w.note)
}

fun LocalTime.hhmm(): String = format(DateTimeFormatter.ofPattern("HH:mm"))

fun shortZone(zone: ZoneId, date: LocalDate): String {
    val zdt = ZonedDateTime.of(date, LocalTime.NOON, zone)
    val city = zone.id.substringAfterLast('/').replace('_', ' ')
    val offset = zdt.offset.id.let { if (it == "Z") "UTC" else "UTC$it" }.replace(":00", "")
    return "$city time ($offset)"
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val longDayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

fun LocalDate.label(): String = format(dayFmt)
fun LocalDate.longLabel(): String = format(longDayFmt)
fun LocalDate.shortLabel(): String = format(shortFmt)
fun LocalDate.weekdayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun dateRangeLabel(a: LocalDate, b: LocalDate): String =
    if (a.year == b.year) "${a.shortLabel()} – ${b.shortLabel()} ${b.year}" else "${a.shortLabel()} ${a.year} – ${b.shortLabel()} ${b.year}"
