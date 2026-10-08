package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
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

/** One row of the trip's own pages, each with its progress: bookings, to-dos, packing, notes. Scrolls if it ever outgrows the screen. */
@Composable
fun TripShortcuts(
    trip: Trip,
    checklist: Checklist,
    hasNotes: Boolean,
    onBookings: () -> Unit,
    onChecklist: (tab: Int) -> Unit,
    onNotes: () -> Unit,
) {
    val bookings = remember(trip) { trip.bookables().filterNot { it.unplanned } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Shortcut("🎫", "Bookings", bookings.count { it.booked } to bookings.size, onBookings)
        Kind.entries.forEach { k -> Shortcut(k.glyph(), k.label(), Checklists.tally(checklist, k), { onChecklist(k.ordinal) }) }
        Shortcut("📝", "Notes", null, onNotes, dot = hasNotes)
    }
}

@Composable
private fun Shortcut(glyph: String, label: String, tally: Pair<Int, Int>?, onClick: () -> Unit, dot: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        val counted = tally?.takeIf { it.second > 0 }
        // A glyph and its count say enough; the name shows only when there is nothing to count.
        append(glyph)
        if (counted == null) append("\u2009$label") else withStyle(SpanStyle(color = muted)) {
            append(if (counted.first == counted.second) " ✓" else " ${counted.first}/${counted.second}")
        }
        if (dot) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(" •") }
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        modifier = Modifier.semantics {
            contentDescription = label + (tally?.let { (d, t) -> ", $d of $t done" } ?: "") + if (dot) ", has notes" else ""
        },
    )
}
