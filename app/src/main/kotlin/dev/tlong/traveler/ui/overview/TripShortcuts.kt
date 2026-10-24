package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.Bookable
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.bookables
import dev.tlong.traveler.domain.due
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.checklist.glyph
import dev.tlong.traveler.ui.checklist.label
import java.time.LocalDate

/** One row of the trip's own pages: events, to-dos and packing with their counts, and notes. Scrolls if it ever outgrows the screen. */
@Composable
fun TripShortcuts(
    trip: Trip,
    checklist: Checklist,
    onEvents: () -> Unit,
    onChecklist: (tab: Int) -> Unit,
    onNotes: () -> Unit,
) {
    val events = remember(trip) { trip.bookables().filter { !it.unplanned && it.group == Bookable.Group.EVENTS } }
    val today = LocalDate.now()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Shortcut("🎟️", "Events", events.count { it.booked } to events.size, onEvents, due = events.any { it.due(today) })
        Kind.entries.forEach { k -> Shortcut(k.glyph(), k.label(), Checklists.tally(checklist, k), { onChecklist(k.ordinal) }) }
        Shortcut("📝", "Notes", null, onNotes)
    }
}

/** A glyph, and its count when there is something to count; red background and outline while something in it is [due]. */
@Composable
private fun Shortcut(glyph: String, label: String, tally: Pair<Int, Int>?, onClick: () -> Unit, due: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val counted = tally?.takeIf { it.second > 0 }
    val text = buildAnnotatedString {
        append(glyph)
        counted?.let { (done, total) ->
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                append(if (done == total) " ✓" else " $done/$total")
            }
        }
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        colors = if (due) AssistChipDefaults.assistChipColors(containerColor = colors.errorContainer, labelColor = colors.onErrorContainer) else AssistChipDefaults.assistChipColors(),
        border = if (due) BorderStroke(1.dp, colors.error) else AssistChipDefaults.assistChipBorder(enabled = true),
        modifier = Modifier.semantics {
            contentDescription = label + (counted?.let { (d, t) -> ", $d of $t done" } ?: "")
        },
    )
}
