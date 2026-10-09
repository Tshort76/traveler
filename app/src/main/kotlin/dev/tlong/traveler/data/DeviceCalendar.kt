package dev.tlong.traveler.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import dev.tlong.traveler.domain.DayEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Writes a day's events into a calendar on the phone; the calendar's account (Google, say) syncs
 * them on. The app remembers the ids it created per trip and date, so exporting a day again
 * replaces exactly those events and never touches anything else. Needs READ_ and WRITE_CALENDAR.
 */
class DeviceCalendar(private val context: Context) {

    data class Calendar(val id: Long, val name: String, val account: String)

    private val prefs get() = context.getSharedPreferences("traveler", Context.MODE_PRIVATE)

    /** Calendars the phone lets this app add events to. */
    suspend fun calendars(): List<Calendar> = withContext(Dispatchers.IO) {
        val cols = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)
        val where = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.VISIBLE} = 1"
        context.contentResolver.query(Calendars.CONTENT_URI, cols, where, null, "${Calendars.IS_PRIMARY} DESC, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
            buildList { while (c.moveToNext()) add(Calendar(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "")) }
        }.orEmpty()
    }

    var chosen: Long?
        get() = prefs.getLong(CHOSEN, -1).takeIf { it >= 0 }
        set(v) { prefs.edit().putLong(CHOSEN, v ?: -1).apply() }

    fun exported(tripId: String, date: String): List<Long> =
        prefs.getString(key(tripId, date), null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()

    /** Deletes what this app added for the day before, then adds [events]; returns how many were added. */
    suspend fun replace(calendarId: Long, tripId: String, date: LocalDate, zone: ZoneId, events: List<DayEvent>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        exported(tripId, date.toString()).forEach { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null) }
        val ids = events.mapNotNull { e ->
            val start = ZonedDateTime.of(date, e.start, zone)
            val end = ZonedDateTime.of(if (e.end < e.start) date.plusDays(1) else date, e.end, zone)
            val values = ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, e.title)
                put(Events.DTSTART, start.toInstant().toEpochMilli())
                put(Events.DTEND, end.toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, zone.id)
                e.location?.let { put(Events.EVENT_LOCATION, it) }
                e.description?.let { put(Events.DESCRIPTION, it) }
            }
            resolver.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        }
        prefs.edit().putString(key(tripId, date.toString()), ids.joinToString(",").ifEmpty { null }).apply()
        ids.size
    }

    private fun key(tripId: String, date: String) = "calendarEvents/$tripId/$date"

    private companion object { const val CHOSEN = "calendarId" }
}
