package dev.tlong.traveler.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tlong.traveler.domain.DayLoad
import dev.tlong.traveler.domain.calendarRows
import dev.tlong.traveler.domain.NightPair
import dev.tlong.traveler.domain.dayLoads
import dev.tlong.traveler.domain.destinations
import dev.tlong.traveler.domain.end
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.nightPairs
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.start
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.theme.LocalStayColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Each destination's calendar tint, in visit order; the same order as the map's numbers. */
@Composable
fun rememberStayTints(trip: Trip): Map<String, Color> {
    val palette = LocalStayColors.current
    val dests = trip.destinations
    return remember(dests, palette) { dests.withIndex().associate { (i, s) -> s.id to palette[i % palette.size] } }
}

/**
 * The trip as weeks: each date tinted by the stay slept in that night, and a move split
 * corner to corner, the stay left above the stay arrived at, so transition weekdays stand out.
 */
@Composable
fun StayCalendar(trip: Trip, tint: Map<String, Color>, today: LocalDate, modifier: Modifier = Modifier, onDay: (LocalDate) -> Unit) {
    val pairs = remember(trip.stays, trip.startDate, trip.endDate) { trip.nightPairs().associateBy { it.date } }
    val loads = remember(trip) { trip.dayLoads() }
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weeks = remember(trip.start, trip.end, firstDay) { calendarRows(trip.start, trip.end, firstDay) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            (0L..6L).map { firstDay.plus(it) }.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        weeks.forEachIndexed { i, week ->
            // A hairline above each new month, which a month starting on the week's first day needs most.
            if (i > 0 && week.firstNotNullOf { it }.month != weeks[i - 1].firstNotNullOf { it }.month) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { d ->
                    val pair = d?.let { pairs[it] }
                    if (pair == null) Box(Modifier.weight(1f).aspectRatio(1f))
                    else DayCell(pair, tint[pair.before?.id], tint[pair.after?.id], d == today, d == trip.start || d.dayOfMonth == 1, loads[d.toString()], Modifier.weight(1f)) { onDay(d) }
                }
            }
        }
    }
}

/** The one key for the map's numbers and the calendar's tints: every destination in visit order, with its nights. */
@Composable
fun StayKey(trip: Trip, tint: Map<String, Color>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        trip.destinations.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(tint.getValue(s.id)))
                Text("${i + 1} ${s.name} · ${s.nights}n", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DayCell(pair: NightPair, before: Color?, after: Color?, today: Boolean, showMonth: Boolean, load: DayLoad?, modifier: Modifier, onClick: () -> Unit) {
    val d = pair.date
    val description = d.label() + when {
        !pair.transition -> pair.after?.let { ", ${it.name}" } ?: ""
        pair.before == null -> ", arrive ${pair.after!!.name}"
        pair.after == null -> ", leave ${pair.before.name}"
        else -> ", ${pair.before.name} to ${pair.after.name}"
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .drawBehind {
                // Upper-left triangle for the night before, lower-right for this night; one colour when they match.
                before?.let { c -> drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }, c) }
                after?.let { c -> drawPath(Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, c) }
            }
            .then(if (today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClickLabel = "Open the day", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (showMonth) "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" else "${d.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.clearAndSetSemantics {},
        )
        // Booked of planned, so a busy day shows at a glance.
        load?.let {
            Text(
                "${it.booked}/${it.planned}", Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
            )
        }
    }
}
