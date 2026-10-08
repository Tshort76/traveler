package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import dev.tlong.traveler.domain.bookAhead
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
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.bookables
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.checklist.glyph
import dev.tlong.traveler.ui.checklist.label

/** One row of the trip's own pages: bookings, to-dos and packing with their counts, and notes. Scrolls if it ever outgrows the screen. */
@Composable
fun TripShortcuts(
    trip: Trip,
    checklist: Checklist,
    onBookings: () -> Unit,
    onChecklist: (tab: Int) -> Unit,
    onNotes: () -> Unit,
) {
    val bookings = remember(trip) { trip.bookables().filterNot { it.unplanned } }
    val urgent = remember(trip) { trip.bookAhead().size }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Shortcut("🎫", "Bookings", bookings.count { it.booked } to bookings.size, onBookings, urgent = urgent)
        Kind.entries.forEach { k -> Shortcut(k.glyph(), k.label(), Checklists.tally(checklist, k), { onChecklist(k.ordinal) }) }
        Shortcut("📝", "Notes", null, onNotes)
    }
}

/**
 * A glyph, and its count when there is something to count. [urgent] open priority-1 bookings turn
 * the chip red: the trip page's only signal that something must be booked now.
 */
@Composable
private fun Shortcut(glyph: String, label: String, tally: Pair<Int, Int>?, onClick: () -> Unit, urgent: Int = 0) {
    val colors = MaterialTheme.colorScheme
    val counted = tally?.takeIf { it.second > 0 }
    val text = buildAnnotatedString {
        append(glyph)
        counted?.let { (done, total) ->
            withStyle(SpanStyle(color = if (urgent > 0) colors.onErrorContainer else colors.onSurfaceVariant)) {
                append(if (done == total) " ✓" else " $done/$total")
            }
        }
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        colors = if (urgent > 0) AssistChipDefaults.assistChipColors(containerColor = colors.errorContainer) else AssistChipDefaults.assistChipColors(),
        border = if (urgent > 0) null else AssistChipDefaults.assistChipBorder(enabled = true),
        modifier = Modifier.semantics {
            contentDescription = label + (counted?.let { (d, t) -> ", $d of $t done" } ?: "") +
                if (urgent > 0) ", $urgent to book now" else ""
        },
    )
}
