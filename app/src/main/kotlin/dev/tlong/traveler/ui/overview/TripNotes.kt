package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.NoteText
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.rememberSession
import kotlinx.coroutines.delay
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Draws a line's leading "- " or "* " as "• ". Same length, so the cursor maps one to one. */
private val BulletDots = VisualTransformation { text ->
    TransformedText(AnnotatedString(text.text.replace(Regex("""(?m)^(\s*)[-*] """), "$1• ")), OffsetMapping.Identity)
}

@Composable
fun NotesScreen(tripId: String, navigator: Navigator) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    Notes(session, navigator)
}

/** The trip's free-text notes as a page of their own; saved as you type, stored outside the trip file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Notes(session: TripSession, navigator: Navigator) {
    val trip by session.trip.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    var value by remember { session.notes.value.orEmpty().let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    val latest by rememberUpdatedState(value.text)
    LaunchedEffect(value.text) {
        delay(400)
        session.setNotes(value.text)
    }
    DisposableEffect(Unit) { onDispose { session.setNotes(latest) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes")
                        Text(trip.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(navigator) },
                actions = { SaveIndicator(saveState, session::retrySave) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            TextField(
                value,
                onValueChange = { new ->
                    val (text, cursor) = NoteText.afterTyping(value.text, new.text, new.selection.end)
                    value = if (text == new.text) new else TextFieldValue(text, TextRange(cursor))
                },
                placeholder = { Text("Notes for this trip") },
                visualTransformation = BulletDots,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            HorizontalDivider()
            TextButton(
                onClick = {
                    val (text, cursor) = NoteText.toggleBullet(value.text, value.selection.end)
                    value = TextFieldValue(text, TextRange(cursor))
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("• Bullet") }
        }
    }
}
