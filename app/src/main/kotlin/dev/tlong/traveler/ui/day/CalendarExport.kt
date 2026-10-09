package dev.tlong.traveler.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.data.DeviceCalendar
import dev.tlong.traveler.domain.DayEvent
import dev.tlong.traveler.domain.hhmm

/**
 * Picks which of the day's timed items go to which phone calendar. Work starts unticked: a work
 * calendar usually has it already. [previous] is how many events an earlier export added; this
 * one replaces them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    title: String,
    events: List<DayEvent>,
    previous: Int,
    calendar: DeviceCalendar,
    onDismiss: () -> Unit,
    onExport: (DeviceCalendar.Calendar, List<DayEvent>) -> Unit,
) {
    var calendars by remember { mutableStateOf<List<DeviceCalendar.Calendar>?>(null) }
    var chosen by remember { mutableStateOf<DeviceCalendar.Calendar?>(null) }
    var picking by remember { mutableStateOf(false) }
    var ticked by remember(events) { mutableStateOf(events.filterNot { it.work }.map { it.key }.toSet()) }
    LaunchedEffect(Unit) {
        val all = calendar.calendars()
        calendars = all
        chosen = all.firstOrNull { it.id == calendar.chosen } ?: all.firstOrNull()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Box {
                TextButton(onClick = { picking = true }, enabled = !calendars.isNullOrEmpty(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(
                        when {
                            calendars == null -> "…"
                            calendars!!.isEmpty() -> "No calendar on this phone takes new events"
                            else -> chosen?.let { "📅 ${it.name} · ${it.account}" } ?: "Choose a calendar"
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!calendars.isNullOrEmpty()) Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(picking, onDismissRequest = { picking = false }) {
                    calendars.orEmpty().forEach { c ->
                        DropdownMenuItem(text = { Text("${c.name} · ${c.account}") }, onClick = { chosen = c; picking = false })
                    }
                }
            }
            if (events.isEmpty()) Text("Nothing on this day has a time", style = MaterialTheme.typography.bodyMedium)
            events.forEach { e ->
                val on = e.key in ticked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { ticked = if (on) ticked - e.key else ticked + e.key },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, onCheckedChange = null)
                    Text("${e.start.hhmm()}–${e.end.hhmm()}  ${e.title}", style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
            val picked = events.filter { it.key in ticked }
            Button(
                onClick = { chosen?.let { onExport(it, picked) } },
                enabled = chosen != null && (picked.isNotEmpty() || previous > 0),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (previous > 0) "Update calendar (${picked.size})" else "Add ${picked.size} to calendar") }
        }
    }
}
