package dev.tlong.traveler.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.widget.Toast
import dev.tlong.traveler.domain.CalendarEntry

/** Opens the phone's calendar app (Google Calendar, say) on a new event filled in from [entry]; nothing is saved until the traveler saves it there. */
fun addToCalendar(context: Context, entry: CalendarEntry) {
    val intent = Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
        .putExtra(Events.TITLE, entry.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, entry.allDay)
    entry.location?.let { intent.putExtra(Events.EVENT_LOCATION, it) }
    entry.description?.let { intent.putExtra(Events.DESCRIPTION, it) }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No calendar app on this phone", Toast.LENGTH_SHORT).show()
    }
}
