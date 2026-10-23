package dev.tlong.traveler.ui.stay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.NightsFrom
import dev.tlong.traveler.domain.arriveDate
import dev.tlong.traveler.domain.end
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.nextStay
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.withNights
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip

/** How many nights [stay] lasts, and whether the next stay or the rest of the trip absorbs the change. */
@Composable
fun NightsDialog(trip: Trip, stay: Stay, onDismiss: () -> Unit, onChange: (Int, NightsFrom) -> Unit) {
    val next = remember(trip, stay.id) { trip.nextStay(stay) }
    var nights by remember { mutableIntStateOf(stay.nights) }
    var from by remember { mutableStateOf(if (next != null) NightsFrom.NEXT_STAY else NightsFrom.LATER_STAYS) }
    val change = remember(trip, nights, from) { trip.withNights(stay.id, nights, from) }
    val shift = nights - stay.nights
    fun plural(n: Int) = "$n night${if (n == 1) "" else "s"}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stay.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nights-- }, enabled = nights > 1) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(plural(nights), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { nights++ }) { Icon(Icons.Default.Add, "One night more") }
                }
                Text("${stay.arriveDate.label()} – ${stay.arriveDate.plusDays(nights.toLong()).label()}")
                val later = "Later stays move · trip ends ${trip.end.plusDays(shift.toLong()).label()}"
                if (next != null) {
                    Choice("${next.name} · ${plural(next.nights - shift)}", from == NightsFrom.NEXT_STAY, enabled = next.nights - shift >= 1) { from = NightsFrom.NEXT_STAY }
                    Choice(later, from == NightsFrom.LATER_STAYS) { from = NightsFrom.LATER_STAYS }
                } else if (shift != 0) {
                    Text(later)
                }
                change?.booked?.forEach { Text("🔒 $it", color = MaterialTheme.colorScheme.error) }
                change?.unscheduled?.takeIf { it > 0 }?.let { Text("$it planned item${if (it == 1) "" else "s"} back to the list") }
            }
        },
        confirmButton = { TextButton(onClick = { onChange(nights, from) }, enabled = change != null) { Text("Change") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
