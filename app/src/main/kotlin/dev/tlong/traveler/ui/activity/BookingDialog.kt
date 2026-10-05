package dev.tlong.traveler.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.hhmm
import dev.tlong.traveler.domain.toTime
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Commitment
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.ui.common.PriceField
import dev.tlong.traveler.ui.common.rememberPriceInput

/** What the booking dialog hands back; times are already normalized to HH:MM. */
data class BookingInput(
    val date: String, val title: String, val start: String?, val end: String?, val ref: String?, val notes: String?, val activityId: String?,
    val url: String?, val price: Price?,
)

/** Saves what [BookingDialog] returned, as a new booking or over [existing]; false if nothing changed. */
fun saveBooking(session: TripSession, existing: Commitment?, b: BookingInput): Boolean = if (existing == null) {
    session.edit("Add booking") { Edits.addBooking(it, b.date, b.title, b.start, b.end, b.ref, b.notes, b.activityId, b.url, b.price).first }
} else {
    session.edit("Edit booking") { t ->
        Edits.updateBooking(t, existing.id) {
            it.copy(
                date = b.date, title = b.title, start = b.start, end = b.end, ref = b.ref?.ifBlank { null }, notes = b.notes?.ifBlank { null },
                activityId = b.activityId, url = b.url?.ifBlank { null }, price = b.price, booked = true,
            )
        }
    }
}

/**
 * Adds or edits a booking. Linking a suggestion schedules it at the booked time; an empty end is
 * filled from the suggestion's duration, so conflict checks know how long it runs. With more than
 * one of [dates] (opened from a suggestion rather than a day), the day is chosen here too.
 */
@Composable
fun BookingDialog(
    dates: List<String>, initialDate: String, existing: Commitment?, suggestions: List<Activity>,
    onDismiss: () -> Unit, onDelete: (() -> Unit)?, initialLinked: Activity? = null, onSave: (BookingInput) -> Unit,
) {
    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var dayMenu by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(existing?.title ?: initialLinked?.name.orEmpty()) }
    var start by remember { mutableStateOf(existing?.start.orEmpty()) }
    var end by remember { mutableStateOf(existing?.end.orEmpty()) }
    var ref by remember { mutableStateOf(existing?.ref.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    val price = rememberPriceInput(existing?.price ?: initialLinked?.booking?.price)
    var linked by remember { mutableStateOf(suggestions.firstOrNull { it.id == existing?.activityId } ?: initialLinked) }
    var menu by remember { mutableStateOf(false) }
    val s = normalizeTime(start)
    val e = normalizeTime(end)
    val timesOk = (start.isBlank() || s != null) && (end.isBlank() || e != null) && (s == null || e == null || e > s)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (existing == null) "Add a booking" else "Booking") + if (dates.size == 1) " · ${dayName(date)}" else "") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dates.size > 1) Box {
                    OutlinedButton(onClick = { dayMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Day: ${dayName(date)}") }
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        dates.forEach { d -> DropdownMenuItem(text = { Text(dayName(d)) }, onClick = { date = d; dayMenu = false }) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("What (e.g. Louvre guided tour)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
                    OutlinedTextField(start, { start = it }, label = { Text("Starts (1200)") }, singleLine = true, keyboardOptions = number,
                        isError = start.isNotBlank() && s == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Ends (optional)") }, singleLine = true, keyboardOptions = number,
                        isError = end.isNotBlank() && (e == null || (s != null && e <= s)), modifier = Modifier.weight(1f))
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(linked?.let { "Suggestion: ${it.name}" } ?: "Link to a suggestion (optional)")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { linked = null; menu = false })
                        suggestions.forEach { a ->
                            DropdownMenuItem(text = { Text(listOfNotNull(a.tag, a.name).joinToString("  ")) }, onClick = {
                                linked = a
                                if (title.isBlank()) title = a.name
                                menu = false
                            })
                        }
                    }
                }
                if (linked != null) Text("It will be scheduled at the booked time on this day.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(ref, { ref = it }, label = { Text("Confirmation number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PriceField(price)
                OutlinedTextField(url, { url = it }, label = { Text("Link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && timesOk && price.ok, onClick = {
                val endOrDuration = e ?: linked?.duration?.minutes?.let { m -> s?.toTime()?.plusMinutes(m.toLong())?.takeIf { it.hhmm() > s }?.hhmm() }
                onSave(BookingInput(date, title.trim(), s, endOrDuration, ref, notes, linked?.id, url.trim(), price.value))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
