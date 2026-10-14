package dev.tlong.traveler.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.FilterChip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlin.math.roundToInt
import java.util.Locale
import dev.tlong.traveler.model.Climate
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

/** "▸ 12 earlier days": the folded past of a list during a trip; tap to show or hide it. */
@Composable
fun EarlierRow(count: Int, noun: String, open: Boolean, onToggle: () -> Unit) {
    Text(
        (if (open) "▾ " else "▸ ") + "$count earlier $noun" + if (count == 1) "" else "s",
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle).wrapContentHeight(Alignment.CenterVertically),
    )
}

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

/** A stay's seasonal weather concern as an emoji; windy is drawn as [WindIcon] instead, and an unknown flag shows nothing. */
private fun climateGlyph(flag: String) = when (flag) {
    "dry" -> "☀️"; "rainy" -> "🌧️"; "stormy" -> "⛈️"; "humid" -> "🥵"; "snowy" -> "❄️"; else -> ""
}

private fun shownFlag(flag: String) = flag == "windy" || climateGlyph(flag).isNotEmpty()

/** "Typical high 72°, low 54°, rainy, windy", for TalkBack beside the glyphs. */
private fun climateDescription(c: Climate) =
    (listOf("Typical high ${degrees(c.highC)}, low ${degrees(c.lowC)}") + c.flags.filter(::shownFlag)).joinToString(", ")

/** Material's "air" icon: wind as curled lines. */
val WindIcon: ImageVector by lazy {
    materialIcon("Filled.Air") {
        materialPath {
            moveTo(14.5f, 17f); curveToRelative(0f, 1.65f, -1.35f, 3f, -3f, 3f); reflectiveCurveToRelative(-3f, -1.35f, -3f, -3f)
            horizontalLineToRelative(2f); curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
            reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); horizontalLineTo(2f); verticalLineToRelative(-2f); horizontalLineToRelative(9.5f)
            curveTo(13.15f, 14f, 14.5f, 15.35f, 14.5f, 17f); close()
            moveTo(19f, 6.5f); curveTo(19f, 4.57f, 17.43f, 3f, 15.5f, 3f); reflectiveCurveTo(12f, 4.57f, 12f, 6.5f); horizontalLineToRelative(2f)
            curveTo(14f, 5.67f, 14.67f, 5f, 15.5f, 5f); reflectiveCurveTo(17f, 5.67f, 17f, 6.5f); reflectiveCurveTo(16.33f, 8f, 15.5f, 8f)
            horizontalLineTo(2f); verticalLineToRelative(2f); horizontalLineToRelative(13.5f); curveTo(17.43f, 10f, 19f, 8.43f, 19f, 6.5f); close()
            moveTo(18.5f, 11f); horizontalLineTo(2f); verticalLineToRelative(2f); horizontalLineToRelative(16.5f)
            curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f); reflectiveCurveTo(19.33f, 16f, 18.5f, 16f); verticalLineToRelative(2f)
            curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f); reflectiveCurveTo(20.43f, 11f, 18.5f, 11f); close()
        }
    }
}

/** A stay's seasonal glyphs and its typical high/low, read aloud as one phrase. */
@Composable
fun ClimateLabel(c: Climate, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = climateDescription(c) }, verticalAlignment = Alignment.CenterVertically) {
        c.flags.filter(::shownFlag).forEach { f ->
            if (f == "windy") Icon(WindIcon, null, Modifier.size(14.dp), tint = color)
            else Text(climateGlyph(f), style = style, modifier = Modifier.clearAndSetSemantics {})
        }
        Text(" ${degrees(c.highC)}/${degrees(c.lowC)}", style = style, color = color, modifier = Modifier.clearAndSetSemantics {})
    }
}

private val fahrenheitCountries = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

/** Whole degrees, Fahrenheit where the phone's region uses it. The file is always Celsius. */
private fun degrees(celsius: Double): String {
    val f = Locale.getDefault().country in fahrenheitCountries
    return "${(if (f) celsius * 9 / 5 + 32 else celsius).roundToInt()}°"
}

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

/**
 * One chip, three states, drawn as a box: everything (a faded tick), then only the ones that are
 * [yes] (a ticked box), then only the ones that are not (a light red cross). [describe] says the
 * state to TalkBack.
 */
@Composable
fun TriStateFilter(yes: Boolean?, describe: (Boolean?) -> String, onChange: (Boolean?) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        yes != null,
        onClick = { onChange(when (yes) { null -> true; true -> false; false -> null }); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
        label = { TriStateBox(yes) },
        modifier = Modifier.semantics { contentDescription = describe(yes) },
    )
}

@Composable
private fun TriStateBox(yes: Boolean?) {
    val c = MaterialTheme.colorScheme
    Canvas(Modifier.size(20.dp)) {
        val inset = 2.5.dp.toPx()
        val r = CornerRadius(2.5.dp.toPx())
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        val at = Offset(inset, inset)
        val tick = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.43f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.36f)
        }
        val tickStroke = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (yes) {
            null -> {
                drawRoundRect(c.outline.copy(alpha = 0.45f), at, box, r, style = Stroke(2.dp.toPx()))
                drawPath(tick, c.outline.copy(alpha = 0.45f), style = tickStroke)
            }
            false -> {
                drawRoundRect(c.primary, at, box, r, style = Stroke(2.dp.toPx()))
                // A light red cross: only the ones that are not.
                // Softened in light mode; dark mode's error red is already light.
                val x = if (c.surface.luminance() > 0.5f) c.error.copy(alpha = 0.7f) else c.error
                val a0 = size.width * 0.34f
                val a1 = size.width * 0.66f
                drawLine(x, Offset(a0, a0), Offset(a1, a1), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(x, Offset(a1, a0), Offset(a0, a1), 2.2.dp.toPx(), StrokeCap.Round)
            }
            true -> {
                drawRoundRect(c.primary, at, box, r)
                drawPath(tick, c.onPrimary, style = tickStroke)
            }
        }
    }
}
