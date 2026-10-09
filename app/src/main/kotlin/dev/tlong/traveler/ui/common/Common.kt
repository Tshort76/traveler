package dev.tlong.traveler.ui.common

import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.AppContainer
import dev.tlong.traveler.data.SaveState
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Duration

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }

/** The open trip's session, or null while it loads (or if the trip is gone). */
@Composable
fun rememberSession(tripId: String): State<TripSession?> {
    val container = LocalContainer.current
    return produceState<TripSession?>(null, tripId) { value = container.session(tripId) }
}

/** Opens a link outside the app. Returns false when nothing on the phone can open it. */
fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Live connectivity, so map buttons can say when they will not work rather than failing silently. */
@Composable
fun rememberOnline(): State<Boolean> {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(ConnectivityManager::class.java) }
    val state = remember {
        mutableStateOf(cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            // The default network is gone; if another takes over, onAvailable follows. Asking for
            // the active network here races: a real phone can still report the one being lost.
            override fun onLost(network: Network) { state.value = false }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return state
}

/** Never claims "saved" before the write lands; a failure stays visible with a retry. */
@Composable
fun SaveIndicator(state: SaveState, onRetry: () -> Unit) {
    when (state) {
        // Saved is the normal state, so it shows nothing; only saving and failure are signals.
        SaveState.Saved -> Unit
        // A save normally lands in milliseconds; only a slow one is worth showing.
        SaveState.Saving -> {
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(600); slow = true }
            if (slow) Text(
                "Saving…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        is SaveState.Failed -> TextButton(onClick = onRetry) {
            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
            Text("Not saved — retry", color = MaterialTheme.colorScheme.error)
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** The Material "drag handle" (two bars), which the core icon set lacks. */
val DragHandle: ImageVector = materialIcon(name = "Filled.DragHandle") {
    materialPath {
        moveTo(20f, 9f); horizontalLineTo(4f); verticalLineToRelative(2f); horizontalLineToRelative(16f); close()
        moveTo(4f, 15f); horizontalLineToRelative(16f); verticalLineToRelative(-2f); horizontalLineTo(4f); close()
    }
}

/** The Material "filter list" icon (three shortening bars), which the core icon set lacks. */
val FilterList: ImageVector = materialIcon(name = "Filled.FilterList") {
    materialPath {
        moveTo(10f, 18f); horizontalLineToRelative(4f); verticalLineToRelative(-2f); horizontalLineToRelative(-4f); close()
        moveTo(3f, 6f); verticalLineToRelative(2f); horizontalLineToRelative(18f); verticalLineTo(6f); close()
        moveTo(6f, 13f); horizontalLineToRelative(12f); verticalLineToRelative(-2f); horizontalLineTo(6f); close()
    }
}

private fun pathIcon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

/** Material's checkbox icons, which the core icon set lacks: an empty box and a ticked one. */
val BoxEmpty: ImageVector = pathIcon("CheckBoxOutlineBlank", "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z")
val BoxTicked: ImageVector = pathIcon("CheckBox", "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")

/** Shows [message] with an Undo action. */
suspend fun SnackbarHostState.offerUndo(session: TripSession, message: String) {
    currentSnackbarData?.dismiss()
    val r = showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
    if (r == SnackbarResult.ActionPerformed) session.undo()
}

/** The small subset of markdown the trip format allows in text: **bold** and [label](https://…). */
@Composable
fun rich(text: String): AnnotatedString {
    val link = MaterialTheme.colorScheme.primary
    return remember(text, link) {
        buildAnnotatedString {
            val re = Regex("\\*\\*(.+?)\\*\\*|\\[(.+?)\\]\\((https?://[^)\\s]+)\\)")
            var i = 0
            re.findAll(text).forEach { m ->
                append(text.substring(i, m.range.first))
                if (m.groupValues[1].isNotEmpty()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                } else {
                    withLink(LinkAnnotation.Url(m.groupValues[3], TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) {
                        append(m.groupValues[2])
                    }
                }
                i = m.range.last + 1
            }
            append(text.substring(i))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun LabeledText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(rich(value), style = MaterialTheme.typography.bodyMedium)
    }
}

fun durationLabel(d: Duration?): String? {
    val m = d?.minutes ?: return null
    fun fmt(x: Int) = when {
        x < 60 -> "$x min"
        x % 60 == 0 -> "${x / 60} h"
        else -> "${x / 60} h ${x % 60} min"
    }
    val range = d.maxMinutes?.takeIf { it > m }?.let { "${fmt(m)}–${fmt(it)}" } ?: fmt(m)
    return when (d.includesTravel) {
        true -> "$range incl. travel"
        false -> "$range, plus travel"
        null -> range
    }
}

fun fitLabel(fit: String?) = when (fit) {
    "short" -> "Short outing"
    "half-day" -> "Half day"
    "full-day" -> "Full day"
    "evening" -> "Evening"
    else -> null
}

fun effortLabel(effort: String?) = when (effort) {
    "easy" -> "Easy"
    "moderate" -> "Moderate effort"
    "hard" -> "Demanding"
    else -> null
}

fun conditionLabel(c: String) = c.replace('-', ' ').replaceFirstChar { it.uppercase() }

fun modeEmoji(mode: String?) = when (mode) {
    "flight" -> "✈️"
    "train" -> "🚆"
    "bus" -> "🚌"
    "boat", "ferry" -> "⛴️"
    "car" -> "🚗"
    "taxi" -> "🚕"
    "hike" -> "🥾"
    else -> "➜"
}

/** "Name · ✨ 🎟️": ✨ a standout, ⭐ one the traveler will likely enjoy, 🎟️ needs a ticket or reservation, ✓ booked. */
fun marked(name: String, stars: Int?, ticket: Boolean, booked: Boolean): String {
    val marks = listOfNotNull(when (stars) { 3 -> "✨"; 2 -> "⭐"; else -> null }, if (booked) "✓" else "🎟️".takeIf { ticket })
    return if (marks.isEmpty()) name else "$name · ${marks.joinToString(" ")}"
}

fun Activity.marked(booked: Boolean) = marked(name, stars, booking != null, booked)

fun starLabel(stars: Int?) = when (stars) { 3 -> "✨ Standout"; 2 -> "⭐ You'll likely enjoy it"; else -> null }

/** Most recommended first: stars, then the plan's rank. */
val byRecommendation = compareBy<Activity>({ -(it.stars ?: 0) }, { it.rank ?: Int.MAX_VALUE }, { it.name })

fun modeLabel(mode: String?) = mode?.replaceFirstChar { it.uppercase() } ?: "Transfer"

/** Compact one-line facts for list rows: "Half day · Moderate effort · 2 h". */
fun Activity.factsLine(): String =
    listOfNotNull(fitLabel(fit), effortLabel(effort), durationLabel(duration)?.substringBefore(" incl.")?.substringBefore(", plus"))
        .joinToString(" · ")
