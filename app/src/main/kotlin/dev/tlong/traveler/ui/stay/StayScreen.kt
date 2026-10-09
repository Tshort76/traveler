package dev.tlong.traveler.ui.stay

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.tlong.traveler.ui.common.EarlierRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.tlong.traveler.ui.common.FilterList
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import dev.tlong.traveler.domain.WorkPlan
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tlong.traveler.ui.map.MapPoint
import dev.tlong.traveler.ui.map.AreaMap
import dev.tlong.traveler.ui.theme.LocalMapColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.datesOf
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.weekdayShort
import dev.tlong.traveler.domain.weeks
import dev.tlong.traveler.domain.workHoursOn
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.DayKind
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.activity.OverlayHost
import dev.tlong.traveler.ui.activity.Overlays
import dev.tlong.traveler.ui.activity.NewEntryRequest
import dev.tlong.traveler.ui.activity.PlaceRequest
import dev.tlong.traveler.ui.activity.rememberOverlays
import dev.tlong.traveler.ui.common.byRecommendation
import dev.tlong.traveler.ui.common.marked
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.conditionLabel
import dev.tlong.traveler.ui.common.factsLine
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayScreen(tripId: String, stayId: String, initialTab: Int, navigator: Navigator) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    val trip by session.trip.collectAsStateWithLifecycle()
    val stay = trip.stay(stayId) ?: run { navigator.back(); return }
    StayContent(session, trip, stay, initialTab, navigator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StayContent(session: TripSession, trip: Trip, stay: Stay, initialTab: Int, navigator: Navigator) {
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val work by session.work.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val overlays = rememberOverlays()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val pool = remember(trip, stay.id) { trip.activities.filter { it.stayId == stay.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stay.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(navigator) },
                actions = { SaveIndicator(saveState, session::retrySave) },
            )
        },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(
                onClick = { overlays.newEntry = NewEntryRequest(stay.id, null, null) },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New entry") },
                // The extended button's label does not reach accessibility services on its own.
                modifier = Modifier.semantics { contentDescription = "New entry" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Itinerary") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Activities (${pool.size})") })
            }
            when (tab) {
                0 -> ItineraryTab(trip, stay, work, onDay = { navigator.day(trip.id, it) }, onActivity = { overlays.detail = it })
                else -> PoolTab(trip, stay, pool, overlays)
            }
        }
    }
    OverlayHost(session, trip, overlays, snackbar, onGoToDay = { navigator.day(trip.id, it) })
}

@Composable
private fun ItineraryTab(trip: Trip, stay: Stay, work: WorkPlan, onDay: (String) -> Unit, onActivity: (String) -> Unit) {
    val dates = remember(trip, stay.id) { trip.datesOf(stay) }
    val today = LocalDate.now()
    // While the stay is under way its past days fold into one row, so the list opens at today.
    val past = if (today in dates) dates.filter { it < today } else emptyList()
    var showPast by rememberSaveable(stay.id) { mutableStateOf(false) }
    val groups = weeks(dates)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (past.isNotEmpty()) item("past") { EarlierRow(past.size, "day", showPast) { showPast = !showPast } }
        groups.forEachIndexed { w, week ->
            val shown = if (showPast) week else week.filter { it !in past }
            if (shown.isEmpty()) return@forEachIndexed
            if (groups.size > 1) item("w$w") {
                SectionTitle("Week ${w + 1} · ${week.first().shortLabel()} – ${week.last().shortLabel()}", Modifier.padding(top = 8.dp))
            }
            items(shown, key = { it.toString() }) { d -> DayCard(trip, stay, work, d, d == today, onClick = { onDay(d.toString()) }, onActivity = onActivity) }
        }
    }
}

@Composable
private fun DayCard(trip: Trip, stay: Stay, workPlan: WorkPlan, date: LocalDate, isToday: Boolean, onClick: () -> Unit, onActivity: (String) -> Unit) {
    val day = trip.day(date.toString())
    val kind = DayKind.of(day?.kind)
    val booked = trip.commitments.filter { it.date == date.toString() }
    // The day's actual work blocks, first start to last end.
    val blocks = workPlan.blocksOn(date.toString(), workHoursOn(stay, date))
    val work = blocks.takeIf { it.isNotEmpty() }?.let { "${it.first().start}–${it.maxOf { b -> b.end }}" }
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.label() + if (isToday) " · Today" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // One pill: work hours say "work day" on their own.
                if (work != null) Pill("💻 $work") else if (kind != DayKind.PLAN) Pill(kind.label)
            }
            booked.forEach { c -> Text("🔒 ${listOfNotNull(c.start, c.end).joinToString("–")} ${c.title}".trim(), style = MaterialTheme.typography.bodySmall) }
            day?.plan?.forEach { item ->
                val a = trip.activities.firstOrNull { it.id == item.activityId } ?: return@forEach
                val status = when (ItemStatus.of(item.status)) { ItemStatus.DONE -> "✓ "; ItemStatus.SKIPPED -> "⨯ "; else -> "" }
                TextButton(onClick = { onActivity(a.id) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(
                        "$status${item.time ?: Slot.of(item.slot).label}  " + "${a.tag ?: "•"} ${a.marked(trip.isBooked(a))}",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolTab(trip: Trip, stay: Stay, pool: List<Activity>, overlays: Overlays) {
    var standouts by rememberSaveable { mutableStateOf(false) }
    var planned by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var fits by rememberSaveable { mutableStateOf(setOf<String>()) }
    var efforts by rememberSaveable { mutableStateOf(setOf<String>()) }
    var conditions by rememberSaveable { mutableStateOf(setOf<String>()) }
    var tags by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scheduled = remember(trip) { trip.days.flatMap { d -> d.plan.map { it.activityId to d.date } }.groupBy({ it.first }, { it.second }) }
    // Numbered in recommendation order over the whole list, so a number stays put while filtering.
    val numbers = remember(pool) {
        pool.sortedWith(byRecommendation).filter { it.place?.hasCoordinates == true }.mapIndexed { i, a -> a.id to i + 1 }.toMap()
    }
    // Only the options this stay's activities actually have.
    val fitOptions = remember(pool) { FITS.filter { (k, _) -> pool.any { it.fit == k } } }
    val effortOptions = remember(pool) { EFFORTS.filter { (k, _) -> pool.any { it.effort == k } } }
    val conditionOptions = remember(pool) { pool.flatMap { it.conditions }.distinct().sorted() }
    val tagOptions = remember(pool) { pool.mapNotNull { it.tag }.distinct() }

    val list = pool.filter { a ->
        (!standouts || a.stars == 3) && (planned == null || (a.id in scheduled) == planned) &&
            (fits.isEmpty() || a.fit in fits) && (efforts.isEmpty() || a.effort in efforts) &&
            (conditions.isEmpty() || a.conditions.any { it in conditions }) && (tags.isEmpty() || a.tag in tags)
    }.sortedWith(byRecommendation)
    val more = fits.size + efforts.size + conditions.size + tags.size
    var advanced by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        if (numbers.isNotEmpty()) item("map") { StayMap(stay, list, numbers) }
        item("filters") {
            // Quick toggles up front; the rarely needed ones fold away behind the filter icon.
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pool.any { it.stars == 3 }) Toggle("✨", "Standouts", standouts) { standouts = !standouts }
                // One button, three states: all, then only the unplanned, then only the planned.
                FilterChip(
                    planned != null,
                    onClick = { planned = when (planned) { null -> false; false -> true; true -> null }; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    label = { PlanBox(planned) },
                    modifier = Modifier.semantics { contentDescription = when (planned) { null -> "Showing planned and unplanned"; false -> "Showing unplanned only"; true -> "Showing planned only" } },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { advanced = !advanced }, modifier = Modifier.semantics {
                    contentDescription = (if (advanced) "Hide filters" else "More filters") + if (more > 0) ", $more on" else ""
                }) {
                    BadgedBox(badge = { if (more > 0) Badge { Text("$more") } }) {
                        Icon(FilterList, null, tint = if (advanced || more > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (advanced) item("advanced") {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (fitOptions.isNotEmpty()) FilterGroup("Time available", fitOptions, fits) { fits = it }
                if (effortOptions.isNotEmpty()) FilterGroup("Effort", effortOptions, efforts) { efforts = it }
                if (conditionOptions.isNotEmpty()) FilterGroup("Good for", conditionOptions.map { it to conditionLabel(it) }, conditions) { conditions = it }
                if (tagOptions.isNotEmpty()) FilterGroup("Type", tagOptions.map { it to it }, tags) { tags = it }
                if (more > 0) TextButton(onClick = { fits = emptySet(); efforts = emptySet(); conditions = emptySet(); tags = emptySet() }) { Text("Clear filters") }
            }
        }
        if (list.isEmpty()) item("empty") { Text("Nothing matches", style = MaterialTheme.typography.bodyMedium) }
        items(list, key = { it.id }) { a ->
            PoolRow(a, numbers[a.id], scheduled[a.id].orEmpty(), trip.isBooked(a), onOpen = { overlays.detail = a.id }, onAdd = { overlays.place = PlaceRequest(a.id, null, null, null) })
        }
    }
}

private val FITS = listOf("short" to "Short", "half-day" to "Half day", "full-day" to "Full day", "evening" to "Evening")
private val EFFORTS = listOf("easy" to "Easy", "moderate" to "Moderate", "hard" to "Demanding")

/** The planned filter's box: a dim outline for all, a firm empty box for unplanned only, a ticked box for planned only. */
@Composable
private fun PlanBox(planned: Boolean?) {
    val c = MaterialTheme.colorScheme
    Canvas(Modifier.size(20.dp)) {
        val inset = 2.5.dp.toPx()
        val r = CornerRadius(2.5.dp.toPx())
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        val at = Offset(inset, inset)
        when (planned) {
            null -> drawRoundRect(c.outlineVariant, at, box, r, style = Stroke(2.dp.toPx()))
            false -> drawRoundRect(c.primary, at, box, r, style = Stroke(2.4.dp.toPx()))
            true -> {
                drawRoundRect(c.primary, at, box, r)
                val tick = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                drawPath(tick, c.onPrimary, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            FilterChip(key in selected, onClick = { onChange(if (key in selected) selected - key else selected + key) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Toggle(label: String, description: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(on, onClick = onClick, label = { Text(label) }, modifier = Modifier.semantics { contentDescription = description })
}

@Composable
private fun PoolRow(a: Activity, number: Int?, plannedOn: List<String>, booked: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    // A list row, not a card: the details line says where it is planned, then the facts.
    val planned = plannedOn.mapNotNull { it.toDate()?.let { d -> "${d.weekdayShort()} ${d.dayOfMonth}" } }
    val details = listOfNotNull(planned.takeIf { it.isNotEmpty() }?.let { "🗓️ " + it.joinToString(", ") }, a.factsLine().ifEmpty { null }, "Yours".takeIf { a.isCustom })
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MapNumber(number, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(listOfNotNull(a.tag, a.marked(booked)).joinToString("  "), style = MaterialTheme.typography.titleSmall)
                a.short?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add ${a.name} to a day" }) {
                Icon(Icons.Default.Add, null)
            }
        }
        HorizontalDivider()
    }
}

/** The activities shown in the list, numbered as on the map, with the lodging as ⌂: what is near what. */
@Composable
private fun StayMap(stay: Stay, shown: List<Activity>, numbers: Map<String, Int>) {
    var full by remember { mutableStateOf(false) }
    val points = remember(stay, shown, numbers) {
        val home = stay.lodging?.place?.takeIf { it.hasCoordinates }?.let { MapPoint(it.lat!!, it.lng!!, "⌂", stay.lodging.name ?: "Lodging") }
        listOfNotNull(home) + shown.mapNotNull { a -> numbers[a.id]?.let { MapPoint(a.place!!.lat!!, a.place.lng!!, "$it", a.name) } }.sortedBy { it.badge.toInt() }
    }
    if (points.isEmpty()) return
    val description = "Map of ${points.size} places in ${stay.name}"
    Card {
        AreaMap(points, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)), description, onClick = { full = true })
    }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            AreaMap(points, Modifier.fillMaxSize(), description)
            Button(onClick = { full = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("Close map") }
        }
    }
}

/** The activity's number on the stay map, drawn like its marker; a blank of the same width when it has no place. */
@Composable
private fun MapNumber(number: Int?, modifier: Modifier = Modifier) {
    val colors = LocalMapColors.current
    Box(modifier.size(24.dp).then(if (number != null) Modifier.background(colors.marker, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (number != null) Text("$number", color = colors.onMarker, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
