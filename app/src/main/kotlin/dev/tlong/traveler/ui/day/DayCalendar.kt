package dev.tlong.traveler.ui.day

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.tlong.traveler.domain.DayLayout
import dev.tlong.traveler.domain.Span
import dev.tlong.traveler.ui.common.Pill
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class EntryKind { ACTIVITY, WORK, BOOKING, TRANSFER }

/** One block on the day calendar. A [loose] one has no time yet: it is drawn, dashed, where it would fit. */
data class CalEntry(
    val key: String,
    val span: Span,
    val kind: EntryKind,
    val title: String,
    val detail: String,
    val loose: Boolean = false,
    val pill: String? = null,
    val movable: Boolean = false,
    /** What the caller needs to act on it: a plan or work-block index, or the booking. */
    val tag: Any? = null,
)

private val HOUR = 56.dp
private val GUTTER = 68.dp
private val MIN_BLOCK = 48.dp

fun hhmm(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)

/**
 * The day drawn to scale: hour lines, the parts of the day in the gutter, and a block per entry.
 * Tap a block to act on it, tap empty time to add something there, and hold a movable block to
 * drag it to a new time (snapped to 15 minutes). [viewport] is the scrolling area in window
 * coordinates, so a drag near its edge scrolls.
 */
@Composable
fun DayCalendar(
    entries: List<CalEntry>,
    range: Span,
    scroll: ScrollState,
    viewport: () -> ClosedFloatingPointRange<Float>?,
    now: Int?,
    onTap: (CalEntry) -> Unit,
    onMoved: (CalEntry, Int) -> Unit,
    onEmpty: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val perMin = with(density) { HOUR.toPx() } / 60f
    val minBlockMin = with(density) { MIN_BLOCK.toPx() } / perMin
    fun y(m: Int) = (m - range.start) * perMin

    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    var pointerY by remember { mutableStateOf(0f) }
    val moved by rememberUpdatedState(onMoved)

    // Hold a block near the top or bottom edge and the day scrolls under it.
    LaunchedEffect(dragKey) {
        if (dragKey == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        while (dragKey != null) {
            val v = viewport()
            val speed = when {
                v == null -> 0f
                pointerY < v.start + edge -> -14f
                pointerY > v.endInclusive - edge -> 14f
                else -> 0f
            }
            if (speed != 0f) dragDy += scroll.scrollBy(speed)
            delay(16)
        }
    }

    // Overlap is judged on what is drawn, so a short block's minimum height counts.
    val drawn = entries.map { Span(it.span.start, maxOf(it.span.end, it.span.start + minBlockMin.roundToInt())) }
    val cols = DayLayout.columns(drawn)
    val total = HOUR * ((range.end - range.start) / 60f)

    BoxWithConstraints(Modifier.fillMaxWidth().height(total)) {
        val width = maxWidth - GUTTER
        // The grid: the afternoon tinted so the parts of the day read as bands, then the hour lines.
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    val left = GUTTER.toPx()
                    drawRect(colors.surfaceContainer, Offset(left, y(12 * 60)), androidx.compose.ui.geometry.Size(size.width - left, 5 * 60 * perMin))
                    var h = (range.start + 59) / 60 * 60
                    while (h <= range.end) {
                        drawLine(colors.outlineVariant, Offset(left, y(h)), Offset(size.width, y(h)), 1.dp.toPx())
                        h += 60
                    }
                    now?.takeIf { it in range.start..range.end }?.let {
                        drawLine(colors.error, Offset(left, y(it)), Offset(size.width, y(it)), 2.dp.toPx())
                    }
                }
                .pointerInput(range) { detectTapGestures { p -> onEmpty(DayLayout.snap(p.y / perMin + range.start, 30)) } },
        )
        val firstHour = (range.start + 59) / 60 * 60
        for (h in firstHour until range.end step 60) {
            // Each part of the day is named where it starts, and at the top for the one already under way.
            val band = when {
                h == 12 * 60 -> "Afternoon"
                h == 17 * 60 -> "Evening"
                h == firstHour -> if (h < 12 * 60) "Morning" else if (h < 17 * 60) "Afternoon" else "Evening"
                else -> null
            }
            Column(Modifier.offset { IntOffset(4.dp.roundToPx(), (y(h) - 7.dp.toPx()).roundToInt()) }.width(GUTTER - 6.dp)) {
                Text(hhmm(h), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                band?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary, maxLines = 1, softWrap = false) }
            }
        }

        entries.forEachIndexed { i, e ->
            val (col, of) = cols[i]
            val lifted = dragKey == e.key
            val colW = width / of
            val top = y(e.span.start)
            val height = with(density) { maxOf(e.span.minutes * perMin, MIN_BLOCK.toPx()).toDp() } - 2.dp
            if (lifted) {
                // Where it was, while it is held.
                Box(Modifier.offset { IntOffset((GUTTER + colW * col).roundToPx(), top.roundToInt()) }.width(colW - 2.dp).height(height).dashed(colors.outline))
            }
            val x = if (lifted) GUTTER else GUTTER + colW * col
            val w = if (lifted) width else colW - 2.dp
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val entry by rememberUpdatedState(e)
            Block(
                e, lifted,
                Modifier.offset { IntOffset(x.roundToPx(), (top + if (lifted) dragDy else 0f).roundToInt()) }
                    .width(w).height(height).zIndex(if (lifted) 1f else 0f)
                    .onGloballyPositioned { coords = it }
                    .then(
                        if (!e.movable) Modifier else Modifier.pointerInput(e.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { p ->
                                    dragKey = entry.key; dragDy = 0f
                                    pointerY = (coords?.positionInWindow()?.y ?: 0f) + p.y
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                    pointerY = (coords?.positionInWindow()?.y ?: 0f) + change.position.y
                                },
                                onDragEnd = {
                                    val start = startAfterDrag(entry.span, dragDy / perMin, range)
                                    dragKey = null; dragDy = 0f
                                    if (start != entry.span.start || entry.loose) moved(entry, start)
                                },
                                onDragCancel = { dragKey = null; dragDy = 0f },
                            )
                        },
                    ),
                onTap = { onTap(e) },
            )
            if (lifted) {
                val start = startAfterDrag(e.span, dragDy / perMin, range)
                Surface(
                    color = colors.inverseSurface, contentColor = colors.inverseOnSurface, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.zIndex(2f).offset { IntOffset((GUTTER + 8.dp).roundToPx(), (top + dragDy - 30.dp.toPx()).roundToInt()) },
                ) {
                    Text("${hhmm(start)}–${hhmm(start + e.span.minutes)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

private fun startAfterDrag(span: Span, deltaMin: Float, range: Span): Int =
    DayLayout.snap(span.start + deltaMin).coerceIn(range.start, maxOf(range.start, range.end - span.minutes))

@Composable
private fun Block(e: CalEntry, lifted: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (bg, fg, bar) = when (e.kind) {
        EntryKind.WORK -> Triple(c.secondaryContainer, c.onSecondaryContainer, c.secondary)
        EntryKind.ACTIVITY -> Triple(c.primaryContainer, c.onPrimaryContainer, c.primary)
        EntryKind.BOOKING -> Triple(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary)
        EntryKind.TRANSFER -> Triple(c.surfaceVariant, c.onSurface, c.outline)
    }
    val loose = e.loose && !lifted
    Surface(
        color = if (loose) c.surfaceContainerLowest else bg, contentColor = if (loose) c.onSurface else fg,
        shape = MaterialTheme.shapes.small, shadowElevation = if (lifted) 6.dp else 0.dp,
        modifier = modifier.semantics { contentDescription = "${e.title}, ${e.detail}" },
    ) {
        Row(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).then(if (loose) Modifier.dashed(c.outline) else Modifier).clickable(onClick = onTap)) {
            if (!loose) Box(Modifier.width(4.dp).fillMaxSize().background(bar))
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    e.pill?.let { Pill(it, Modifier.padding(start = 6.dp), container = c.surfaceVariant, content = c.onSurfaceVariant) }
                }
                Text(e.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Modifier.dashed(color: Color) = drawBehind {
    drawRoundRect(color, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))), cornerRadius = CornerRadius(8.dp.toPx()))
}
