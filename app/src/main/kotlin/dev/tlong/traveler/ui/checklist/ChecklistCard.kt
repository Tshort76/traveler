package dev.tlong.traveler.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.Checklists

/** The trip page's view of the checklist: a count per tab and the next few to-dos, tickable in place. */
@Composable
fun ChecklistCard(list: Checklist, onToggle: (String) -> Unit, onOpen: (tab: Int, pick: Boolean) -> Unit) {
    Card(onClick = { onOpen(0, false) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Checklist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (list.items.isNotEmpty()) Kind.entries.forEach { k ->
                    val (done, total) = Checklists.tally(list, k)
                    if (total > 0) Tally(k, done, total) { onOpen(k.ordinal, false) }
                }
            }
            if (list.items.isEmpty()) {
                Text(
                    "To-dos and a packing list for this trip. Start from your templates, then add or remove items. Kept on this phone, outside the trip file.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilledTonalButton(onClick = { onOpen(0, true) }) { Text("Start from a template") }
                    TextButton(onClick = { onOpen(0, false) }) { Text("Add items") }
                }
            } else {
                val open = Checklists.of(list, Kind.TODO).filterNot { it.done }
                open.take(NEXT).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ticking here changes the list under the card's own click target; the checkbox wins the touch.
                        Checkbox(false, { onToggle(item.id) }, Modifier.padding(end = 2.dp))
                        Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val more = open.size - NEXT
                Text(
                    when {
                        more > 0 -> "$more more to do"
                        open.isEmpty() && Checklists.of(list, Kind.TODO).isNotEmpty() -> "All to-dos done ✓"
                        else -> null
                    } ?: return@Column,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
        }
    }
}

private const val NEXT = 3

@Composable
private fun Tally(kind: Kind, done: Int, total: Int, onClick: () -> Unit) {
    val all = done == total
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${kind.label()}: $done of $total done" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(kind.glyph(), style = MaterialTheme.typography.labelMedium)
        Text(
            if (all) "✓" else "$done/$total", style = MaterialTheme.typography.labelMedium,
            color = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
