package dev.tlong.traveler.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.PlacementIssue
import dev.tlong.traveler.domain.checkPlacement
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.stayOf
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.toTime
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.DayKind
import dev.tlong.traveler.model.Duration
import dev.tlong.traveler.model.Place
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip

/** The category tags and their names, as the prompt lists them. */
val TAGS = linkedMapOf(
    "🏛️" to "Architecture", "🌿" to "Nature", "☕" to "Coffee", "🍽️" to "Food", "🎨" to "Art", "🛍️" to "Shopping",
    "📸" to "Viewpoints", "🏃" to "Runs and hikes", "🧗" to "Climbing", "🚶" to "Walks", "🏡" to "Neighborhoods", "💻" to "Work",
    "🚗" to "Driving", "🎭" to "Culture", "🌊" to "Water", "🌲" to "Forest", "🏔️" to "Mountains", "📚" to "Books", "🎵" to "Music",
    "🍸" to "Bars", "💃" to "Dance", "🏊" to "Swimming",
)

private val timeRe = Regex("^([01]?\\d|2[0-3])[:.h ]?([0-5]\\d)$")

/** Accepts 14:30, 1430, 9:05, 905 or 14.30 — a phone's number pad has no colon. Returns HH:MM or null. */
fun normalizeTime(s: String): String? = timeRe.matchEntire(s.trim())?.let { m ->
    "${m.groupValues[1].toInt().toString().padStart(2, '0')}:${m.groupValues[2]}"
}

/**
 * The tap path for every drag action: pick a day and a slot (and optionally a time). Conflicts
 * with bookings, work hours and opening days are explained here, before anything changes; the
 * button then reads "anyway" so the choice is deliberate rather than blocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    trip: Trip,
    activity: Activity,
    verb: String,
    initialDate: String?,
    initialSlot: Slot?,
    ignoreRef: Edits.ItemRef?,
    onDismiss: () -> Unit,
    onConfirm: (date: String, slot: Slot, time: String?) -> Unit,
) {
    val days = trip.days
    var date by remember { mutableStateOf(initialDate ?: days.firstOrNull { trip.stayOf(it)?.id == activity.stayId }?.date ?: days.first().date) }
    var slot by remember { mutableStateOf(initialSlot ?: activity.bestTime.firstOrNull()?.let { Slot.of(it) } ?: Slot.AFTERNOON) }
    var timeText by remember { mutableStateOf("") }
    val time = normalizeTime(timeText)
    val timeInvalid = timeText.isNotBlank() && time == null
    val issues = remember(date, slot, time) {
        date.toDate()?.let { checkPlacement(trip, activity, it, slot, time?.toTime(), ignoreRef) } ?: emptyList()
    }
    val blocking = issues.any { it.blocking }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((days.indexOfFirst { it.date == date } - 2).coerceAtLeast(0)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$verb “${activity.name}”", style = MaterialTheme.typography.titleLarge)
            Text("Day", style = MaterialTheme.typography.labelLarge)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state = listState) {
                var lastStay: String? = null
                days.forEach { d ->
                    val stay = trip.stayOf(d)
                    if (stay?.id != lastStay) {
                        lastStay = stay?.id
                        item("h-${d.date}") {
                            Text(stay?.name ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                    }
                    item(d.date) {
                        Row(
                            Modifier.fillMaxWidth().selectable(d.date == date, role = Role.RadioButton) { date = d.date }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(d.date == date, onClick = null)
                            Text(dayName(d.date), Modifier.padding(start = 8.dp).weight(1f))
                            val kind = DayKind.of(d.kind)
                            val n = d.plan.size
                            Text(
                                listOfNotNull(kind.takeIf { it != DayKind.PLAN }?.label, if (n > 0) "$n planned" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text("Time of day", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Slot.entries.forEachIndexed { i, s ->
                    SegmentedButton(selected = slot == s, onClick = { slot = s }, shape = SegmentedButtonDefaults.itemShape(i, Slot.entries.size)) {
                        Text(s.label, maxLines = 1)
                    }
                }
            }
            OutlinedTextField(
                timeText, { timeText = it }, label = { Text("Time") }, placeholder = { Text("1430") }, singleLine = true,
                isError = timeInvalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = if (timeInvalid) { { Text("Use a 24-hour time like 1430") } } else null, modifier = Modifier.fillMaxWidth(),
            )
            issues.forEach { IssueLine(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfirm(date, slot, time) }, enabled = !timeInvalid) {
                    Text(if (blocking) "$verb anyway" else verb)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun IssueLine(issue: PlacementIssue) {
    val (icon, color) = if (issue.blocking) "⚠" to MaterialTheme.colorScheme.error else "ℹ" to MaterialTheme.colorScheme.onSurfaceVariant
    Text("$icon ${issue.message}", style = MaterialTheme.typography.bodyMedium, color = color)
}

/** Shown when a drag lands somewhere with a real conflict; the booking itself is never moved. */
@Composable
fun ConflictDialog(title: String, issues: List<PlacementIssue>, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                issues.forEach { IssueLine(it) }
                Text("Booked commitments stay where they are.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Keep the move") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Undo the move") } },
    )
}

/** A personal entry: a title is enough; everything else is optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewEntryDialog(where: String, onDismiss: () -> Unit, onCreate: (name: String, note: String?, place: String?, tag: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("What") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text("Place for Google Maps (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceChips(TAGS.keys.map { it to it }, tag) { tag = it }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, note.ifBlank { null }, place.ifBlank { null }, tag) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Edits an activity in place. The traveler's wording is stored exactly as typed; on a suggestion
 * the changed fields are recorded as theirs so a revision will not overwrite them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditActivityDialog(activity: Activity, onDismiss: () -> Unit, onSave: (Activity) -> Unit) {
    var name by remember { mutableStateOf(activity.name) }
    var short by remember { mutableStateOf(activity.short.orEmpty()) }
    var detail by remember { mutableStateOf(activity.detail.joinToString("\n\n")) }
    var why by remember { mutableStateOf(activity.why.orEmpty()) }
    var place by remember { mutableStateOf(activity.place?.query ?: activity.place?.name.orEmpty()) }
    var minutes by remember { mutableStateOf(activity.duration?.minutes?.toString().orEmpty()) }
    var tag by remember { mutableStateOf(activity.tag) }
    var fit by remember { mutableStateOf(activity.fit) }
    var effort by remember { mutableStateOf(activity.effort) }

    fun build(): Activity {
        val placeChanged = place != (activity.place?.query ?: activity.place?.name.orEmpty())
        val newPlace = when {
            !placeChanged -> activity.place
            place.isBlank() -> null
            else -> (activity.place ?: Place()).copy(query = place.trim(), lat = null, lng = null, placeId = null, mapsUrl = null)
        }
        val mins = minutes.toIntOrNull()
        val duration = if (mins == activity.duration?.minutes) activity.duration else mins?.let { (activity.duration ?: Duration()).copy(minutes = it) }
        return activity.copy(
            name = name, short = short.ifEmpty { null }, why = why.ifEmpty { null },
            detail = if (detail == activity.detail.joinToString("\n\n")) activity.detail else detail.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() },
            place = newPlace, duration = duration, tag = tag, fit = fit, effort = effort,
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text("Edit") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel editing") } },
                    actions = { TextButton(onClick = { onSave(build()) }, enabled = name.isNotBlank()) { Text("Save") } },
                )
            }) { padding ->
                Column(
                    Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(short, { short = it }, label = { Text("In a few words") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(detail, { detail = it }, label = { Text("What you do (blank line between paragraphs)") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(why, { why = it }, label = { Text("Why") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(place, { place = it }, label = { Text("Place for Google Maps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes it takes (estimate)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Suits", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf("short" to "Short", "half-day" to "Half day", "full-day" to "Full day", "evening" to "Evening"), fit) { fit = it }
                    Text("Effort", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf("easy" to "Easy", "moderate" to "Moderate", "hard" to "Demanding"), effort) { effort = it }
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(TAGS.keys.map { it to it }, tag) { tag = it }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selected: String?, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onPick(if (selected == key) null else key) }, label = { Text(label) })
        }
    }
}

