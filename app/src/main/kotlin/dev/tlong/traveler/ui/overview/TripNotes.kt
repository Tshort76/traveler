package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.NoteText

/** The traveler's own notes on the trip: shown with bullets, tapped to edit. Stored outside the trip file. */
@Composable
fun TripNotesCard(note: String?, onEdit: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (note == null) "Add" else "Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (note == null) {
                Text("Anything to remember for this trip. Notes stay on this phone: they are not in exported trip files, and revisions never change them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                NoteBody(note)
            }
        }
    }
}

@Composable
private fun NoteBody(note: String) {
    note.lines().forEach { line ->
        val bullet = NoteText.bulletOf(line)
        when {
            bullet != null -> Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                Text(bullet, style = MaterialTheme.typography.bodyMedium)
            }
            line.isBlank() -> Spacer(Modifier.height(4.dp))
            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun TripNotesDialog(note: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(note.orEmpty(), TextRange(note.orEmpty().length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value,
                    onValueChange = { new ->
                        val (text, cursor) = NoteText.afterTyping(value.text, new.text, new.selection.end)
                        value = if (text == new.text) new else TextFieldValue(text, TextRange(cursor))
                    },
                    placeholder = { Text("- Bring the adapter\n- Ask about late checkout") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                TextButton(onClick = {
                    val (text, cursor) = NoteText.toggleBullet(value.text, value.selection.end)
                    value = TextFieldValue(text, TextRange(cursor))
                }) { Text("• Bullet") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
