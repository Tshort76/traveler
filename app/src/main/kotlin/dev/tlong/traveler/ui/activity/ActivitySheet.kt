package dev.tlong.traveler.ui.activity

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.tlong.traveler.model.Commitment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.MapsLinks
import dev.tlong.traveler.domain.MapsLocation
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.placementsOf
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Place
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.common.starLabel
import dev.tlong.traveler.ui.overview.UrlDialog
import dev.tlong.traveler.ui.common.LabeledText
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.conditionLabel
import dev.tlong.traveler.ui.common.durationLabel
import dev.tlong.traveler.ui.common.effortLabel
import dev.tlong.traveler.ui.common.fitLabel
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.ui.common.rememberOnline
import dev.tlong.traveler.ui.common.resolveMapsLink
import dev.tlong.traveler.ui.common.rich

/** Everything about one activity, in a sheet over the current screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivitySheet(
    session: TripSession,
    trip: Trip,
    activityId: String,
    onDismiss: () -> Unit,
    onGoToDay: (String) -> Unit,
    onPlace: (Activity) -> Unit,
    onEdit: (Activity) -> Unit,
    onDeleted: (Activity) -> Unit,
    onMessage: (String) -> Unit,
    onBook: (Activity, Commitment?) -> Unit,
) {
    val a = trip.activity(activityId) ?: return onDismiss()
    val context = LocalContext.current
    val online by rememberOnline()
    val stay = trip.stay(a.stayId)
    var note by remember(activityId) { mutableStateOf(a.userNote.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf(false) }
    val currentNote by rememberUpdatedState(note)
    // The note is saved when the sheet closes, as one undoable edit rather than one per keystroke.
    DisposableEffect(activityId) {
        onDispose {
            if (currentNote != (session.trip.value.activity(activityId)?.userNote.orEmpty())) {
                session.edit("Edit note") { Edits.setUserNote(it, activityId, currentNote) }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var more by remember { mutableStateOf(false) }
            // Secondary actions live behind ⋮ so the sheet reads as the activity, not a form.
            Row(verticalAlignment = Alignment.Top) {
                Text(listOfNotNull(a.tag, a.name).joinToString("  "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "More for ${a.name}") }
                    DropdownMenu(more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { more = false; onEdit(a) })
                        DropdownMenuItem(text = { Text("I booked this…") }, onClick = { more = false; onBook(a, null) })
                        DropdownMenuItem(text = { Text(if (a.place?.hasCoordinates == true) "Fix location" else "Set location") }, onClick = { more = false; editingPlace = true })
                        DropdownMenuItem(text = { Text(if (a.url == null) "Add link" else "Edit link") }, onClick = { more = false; editingLink = true })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { more = false; confirmDelete = true })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.isCustom) Pill("Your entry")
                starLabel(a.stars)?.let { Pill(it) }
                if (a.booking != null) Pill("🎟️ Ticket or reservation needed")
                if (a.confidence == "check") Pill("⚠ Check before going", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            a.short?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

            val hasPlace = a.place != null || !a.isCustom
            if (hasPlace) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!openUrl(context, MapsLinks.open(a.place, a.name, stay?.name))) onMessage("No app can open Google Maps links")
                    }) { Text("Open in Maps") }
                    OutlinedButton(onClick = {
                        if (!openUrl(context, MapsLinks.directions(a.place, a.name, stay?.name))) onMessage("No app can open Google Maps links")
                    }) { Text("Directions") }
                }
                if (!online) Text(
                    "Offline: Maps shows only areas you downloaded.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editingPlace) LocationDialog(a.name, onDismiss = { editingPlace = false }) { at, link ->
                editingPlace = false
                session.edit("Set location") {
                    Edits.updateActivity(it, activityId) { x -> x.copy(place = (x.place ?: Place()).copy(
                        lat = at.lat, lng = at.lng, placeId = null, query = null,
                        mapsUrl = link ?: "https://www.google.com/maps/search/?api=1&query=${at.lat},${at.lng}",
                    )) }
                }
            }

            a.detail.forEach { Text(rich(it), style = MaterialTheme.typography.bodyLarge) }
            a.why?.let { LabeledText("Why this one", it) }

            val facts = listOfNotNull(
                durationLabel(a.duration)?.let { "Time" to it },
                fitLabel(a.fit)?.let { "Suits" to (it + (if (a.bestTime.isNotEmpty()) ", best in the " + a.bestTime.joinToString("/") else "")) },
                effortLabel(a.effort)?.let { "Effort" to it },
                a.hours?.let { "Hours" to it },
                a.availability.takeIf { it.isNotEmpty() }?.let { w ->
                    "Open" to w.joinToString("; ") { listOfNotNull(it.days.joinToString(" ").ifEmpty { null }, listOfNotNull(it.start, it.end).joinToString("–").ifEmpty { null }, it.note).joinToString(" · ") }
                },
                a.conditions.takeIf { it.isNotEmpty() }?.let { "Good for" to it.joinToString(", ") { c -> conditionLabel(c) } },
                a.practical?.transport?.let { "Getting there" to it },
                a.practical?.booking?.let { "Booking" to it },
                a.practical?.cost?.let { "Cost" to it },
                a.practical?.weather?.let { "Weather" to it },
                a.practical?.access?.let { "Access" to it },
                a.practical?.tips?.let { "Tips" to it },
                a.place?.address?.let { "Address" to it },
            )
            if (facts.isNotEmpty()) {
                HorizontalDivider()
                facts.forEach { (k, v) -> LabeledText(k, v) }
            }
            a.url?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text("Website ↗") } }
            if (editingLink) UrlDialog("Link for ${a.name}", "The venue, tour or booking page.", a.url, onDismiss = { editingLink = false }) { url ->
                editingLink = false
                session.edit("Edit link") { Edits.updateActivity(it, activityId) { x -> x.copy(url = url) } }
            }

            HorizontalDivider()
            val placements = trip.placementsOf(a.id)
            if (placements.isNotEmpty()) {
                Text("Planned", style = MaterialTheme.typography.labelLarge)
                placements.forEach { p ->
                    val status = ItemStatus.of(p.item.status).takeIf { it != ItemStatus.PROPOSED }?.let { " · ${it.label}" }.orEmpty()
                    TextButton(onClick = { onGoToDay(p.date) }) {
                        Text("${dayName(p.date)} · ${Slot.of(p.item.slot).label}${p.item.time?.let { " $it" } ?: ""}$status")
                    }
                }
            }
            val bookings = trip.commitments.filter { it.activityId == a.id }
            bookings.forEach { c ->
                TextButton(onClick = { onBook(a, c) }) {
                    Text("🔒 Booked ${dayName(c.date)}${c.start?.let { " $it" } ?: ""}${c.ref?.let { " · $it" } ?: ""}")
                }
            }
            Button(onClick = { onPlace(a) }) { Text(if (placements.isEmpty()) "Add to a day…" else "Add again…") }

            OutlinedTextField(
                note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
    }

    if (confirmDelete) {
        val count = trip.placementsOf(a.id).size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${a.name}”?") },
            text = {
                Text(
                    (if (count > 0) "It is planned on $count day${if (count == 1) "" else "s"}; those placements go too. " else "") +
                        "To take it off a day but keep it, use “Unschedule” on the day instead. You can undo this.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleted(a) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Takes what Google Maps lets you copy (a shared link or a pin's coordinates) and finds the coordinates in it. */
@Composable
private fun LocationDialog(name: String, onDismiss: () -> Unit, onSave: (MapsLocation.LatLng, String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location of $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "In Google Maps, open the place and tap Share, then Copy link. Or press and hold a spot and copy the coordinates at the top. Paste either here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(text, { text = it; error = null }, label = { Text("Link or coordinates") }, modifier = Modifier.fillMaxWidth())
                if (busy) Text("Looking up the link…", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                val link = MapsLocation.firstUrl(text)
                MapsLocation.coordinates(text)?.let { return@TextButton onSave(it, link) }
                if (link == null) { error = "No coordinates or link in that. Paste a Google Maps link, or coordinates like -34.6037, -58.3816."; return@TextButton }
                busy = true
                scope.launch {
                    val at = resolveMapsLink(link)
                    busy = false
                    if (at != null) onSave(at, link)
                    else error = "Couldn't find coordinates in that link (or you're offline). In Google Maps, press and hold the spot and copy its coordinates instead."
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
