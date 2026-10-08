package dev.tlong.traveler.ui.stay

import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import dev.tlong.traveler.ui.common.FilterList
import dev.tlong.traveler.ui.common.weekdaysLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.bookablesOn
import dev.tlong.traveler.ui.bookings.tallyLabel
import dev.tlong.traveler.domain.MapsLinks
import dev.tlong.traveler.domain.arriveDate
import dev.tlong.traveler.domain.datesOf
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.departDate
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.rateLabel
import dev.tlong.traveler.domain.total
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.observesDst
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.domain.shortZone
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.weekdayShort
import dev.tlong.traveler.domain.weeks
import dev.tlong.traveler.domain.workHoursOn
import dev.tlong.traveler.domain.zone
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
import dev.tlong.traveler.ui.common.LabeledText
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.conditionLabel
import dev.tlong.traveler.ui.common.factsLine
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.common.rich
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import dev.tlong.traveler.ui.overview.UrlDialog
import dev.tlong.traveler.ui.bookings.PriorityTag
import dev.tlong.traveler.ui.activity.dayName
import kotlinx.coroutines.launch
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
    val snackbar = remember { SnackbarHostState() }
    val overlays = rememberOverlays()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val pool = remember(trip, stay.id) { trip.activities.filter { it.stayId == stay.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stay.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${stay.arriveDate.shortLabel()} – ${stay.departDate.shortLabel()} · ${stay.nights} night${if (stay.nights == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
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
                Tab(tab == 2, onClick = { tab = 2 }, text = { Text("Info") })
            }
            when (tab) {
                0 -> ItineraryTab(trip, stay, onDay = { navigator.day(trip.id, it) }, onActivity = { overlays.detail = it })
                1 -> PoolTab(trip, stay, pool, overlays)
                else -> InfoTab(session, trip, stay, pool, overlays, snackbar)
            }
        }
    }
    OverlayHost(session, trip, overlays, snackbar, onGoToDay = { navigator.day(trip.id, it) })
}

@Composable
private fun ItineraryTab(trip: Trip, stay: Stay, onDay: (String) -> Unit, onActivity: (String) -> Unit) {
    val dates = remember(trip, stay.id) { trip.datesOf(stay) }
    val today = LocalDate.now()
    val groups = weeks(dates)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEachIndexed { w, week ->
            if (groups.size > 1) item("w$w") {
                SectionTitle("Week ${w + 1} · ${week.first().shortLabel()} – ${week.last().shortLabel()}", Modifier.padding(top = 8.dp))
            }
            items(week, key = { it.toString() }) { d -> DayCard(trip, stay, d, d == today, onClick = { onDay(d.toString()) }, onActivity = onActivity) }
        }
    }
}

@Composable
private fun DayCard(trip: Trip, stay: Stay, date: LocalDate, isToday: Boolean, onClick: () -> Unit, onActivity: (String) -> Unit) {
    val day = trip.day(date.toString())
    val kind = DayKind.of(day?.kind)
    val booked = trip.commitments.filter { it.date == date.toString() }
    val work = workHoursOn(stay, date)
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.label() + if (isToday) " · Today" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // One pill: work hours say "work day" on their own.
                if (work != null) Pill("💻 ${work.label}") else if (kind != DayKind.PLAN) Pill(kind.label)
            }
            day?.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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

private enum class Show(val label: String) { ALL("All"), OPEN("Not planned"), PLANNED("Planned") }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PoolTab(trip: Trip, stay: Stay, pool: List<Activity>, overlays: Overlays) {
    var show by rememberSaveable { mutableStateOf(Show.ALL) }
    var fits by rememberSaveable { mutableStateOf(setOf<String>()) }
    var efforts by rememberSaveable { mutableStateOf(setOf<String>()) }
    var conditions by rememberSaveable { mutableStateOf(setOf<String>()) }
    var tags by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val allConditions = remember(pool) { pool.flatMap { it.conditions }.distinct().sorted() }
    val allTags = remember(pool) { pool.mapNotNull { it.tag }.distinct() }
    val scheduled = remember(trip) { trip.days.flatMap { d -> d.plan.map { it.activityId to d.date } }.groupBy({ it.first }, { it.second }) }
    // Numbered in recommendation order over the whole list, so a number stays put while filtering.
    val numbers = remember(pool) {
        pool.sortedWith(byRecommendation).filter { it.place?.hasCoordinates == true }.mapIndexed { i, a -> a.id to i + 1 }.toMap()
    }

    val list = pool.filter { a ->
        val planned = a.id in scheduled
        (show == Show.ALL || (show == Show.OPEN && !planned) || (show == Show.PLANNED && planned)) &&
            (fits.isEmpty() || a.fit in fits) && (efforts.isEmpty() || a.effort in efforts) &&
            (conditions.isEmpty() || a.conditions.any { it in conditions }) && (tags.isEmpty() || a.tag in tags)
    }.sortedWith(byRecommendation)
    val activeFilters = fits.size + efforts.size + conditions.size + tags.size

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (numbers.isNotEmpty()) item("map") { StayMap(stay, list, numbers) }
        else if (pool.isNotEmpty()) item("map") {
            Text(
                "No map: these places have no coordinates.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item("show") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    Show.entries.forEachIndexed { i, s ->
                        SegmentedButton(show == s, onClick = { show = s }, shape = SegmentedButtonDefaults.itemShape(i, Show.entries.size)) { Text(s.label) }
                    }
                }
                IconButton(onClick = { showFilters = !showFilters }, modifier = Modifier.semantics {
                    contentDescription = (if (showFilters) "Hide filters" else "Filters") + if (activeFilters > 0) ", $activeFilters on" else ""
                }) {
                    BadgedBox(badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                        Icon(FilterList, null, tint = if (showFilters || activeFilters > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (showFilters) item("filters") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                run {
                    FilterGroup("Time available", listOf("short" to "Short", "half-day" to "Half day", "full-day" to "Full day", "evening" to "Evening"), fits) { fits = it }
                    FilterGroup("Effort", listOf("easy" to "Easy", "moderate" to "Moderate", "hard" to "Demanding"), efforts) { efforts = it }
                    if (allConditions.isNotEmpty()) FilterGroup("Good for", allConditions.map { it to conditionLabel(it) }, conditions) { conditions = it }
                    if (allTags.isNotEmpty()) FilterGroup("Type", allTags.map { it to it }, tags) { tags = it }
                    if (activeFilters > 0) TextButton(onClick = { fits = emptySet(); efforts = emptySet(); conditions = emptySet(); tags = emptySet() }) { Text("Clear filters") }
                }
            }
        }
        if (list.isEmpty()) item("empty") { Text("Nothing matches", style = MaterialTheme.typography.bodyMedium) }
        items(list, key = { it.id }) { a ->
            PoolRow(a, numbers[a.id], scheduled[a.id].orEmpty(), trip.isBooked(a), onOpen = { overlays.detail = a.id }, onAdd = { overlays.place = PlaceRequest(a.id, null, null, null) })
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
private fun PoolRow(a: Activity, number: Int?, plannedOn: List<String>, booked: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MapNumber(number, Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOfNotNull(a.tag, a.marked(booked)).joinToString("  "), style = MaterialTheme.typography.titleSmall)
                a.short?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                a.factsLine().takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (plannedOn.isNotEmpty()) Pill("Planned " + plannedOn.mapNotNull { it.toDate()?.let { d -> "${d.weekdayShort()} ${d.dayOfMonth}" } }.joinToString(", "))
                    if (a.isCustom) Pill("Yours")
                }
            }
            IconButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add ${a.name} to a day" }) {
                Icon(Icons.Default.Add, null)
            }
        }
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

@Composable
private fun InfoTab(session: TripSession, trip: Trip, stay: Stay, pool: List<Activity>, overlays: Overlays, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingMap by remember { mutableStateOf(false) }
    val year = stay.arriveDate.year

    // The stay's standouts, or the plan's priorities when nothing has three stars.
    val standouts = pool.filter { it.stars == 3 }.sortedWith(byRecommendation)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (standouts.isNotEmpty() || stay.priorities.isNotEmpty()) item("standouts") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(if (standouts.isNotEmpty()) "✨ Standouts" else "Prioritize")
                standouts.forEach { a ->
                    Text("• ${marked(a.name, null, a.booking != null, trip.isBooked(a))}", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${a.name}") { overlays.detail = a.id }.padding(vertical = 4.dp))
                }
                if (standouts.isEmpty()) stay.priorities.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            }
        }
        item("lodging") {
            LodgingCard(stay, trip.travelers ?: 1, onLink = { url -> if (!openUrl(context, url)) scope.launch { snackbar.showSnackbar("No app can open $url") } })
        }
        stay.workRhythm?.let { w ->
            item("work") {
                val sample = trip.datesOf(stay).firstNotNullOfOrNull { workHoursOn(stay, it) }
                LabeledText("Work rhythm", buildString {
                    append(weekdaysLabel(w.days)).append(" · ")
                    append(sample?.label ?: "${w.start}–${w.end}")
                    sample?.sourceLabel?.let { append("\n").append(it) }
                    w.note?.let { append("\n").append(it) }
                })
            }
        }
        item("tz") {
            val z = stay.zone
            LabeledText(
                "Time zone",
                shortZone(z, stay.arriveDate) + if (z.observesDst(year)) " · changes clocks during the year" else "",
            )
        }
        stay.transport?.let { item("transport") { LabeledText("Getting around", it) } }
        if (stay.notes.isNotEmpty()) item("notes") { LabeledText("Notes", stay.notes.joinToString("\n")) }
        item("stay-map") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stay.mapUrl?.let { url ->
                    OutlinedButton(onClick = { if (!openUrl(context, url)) scope.launch { snackbar.showSnackbar("Cannot open the map link") } }) { Text("Open stay map") }
                }
                TextButton(onClick = { editingMap = true }) { Text(if (stay.mapUrl == null) "Add a custom map link" else "Change map link") }
            }
        }
    }
    if (editingMap) UrlDialog("Map for ${stay.name}", "Paste a Google My Maps link for this stay.", stay.mapUrl, onDismiss = { editingMap = false }) { url ->
        editingMap = false
        session.edit("Change stay map link") { Edits.setStayMapUrl(it, stay.id, url) }
    }
}

/** Where the stay sleeps: the booking's details once booked; before that, what to book and how. */
@Composable
private fun LodgingCard(stay: Stay, travelers: Int, onLink: (String) -> Unit) {
    val l = stay.lodging
    val booked = l?.isBooked == true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🏨", style = MaterialTheme.typography.titleMedium)
                Text(if (booked) l?.name ?: "Lodging" else "Lodging not chosen yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (booked) Text("✓ Booked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else l?.priority?.let { PriorityTag(it) }
            }
            Text("${dayName(stay.arrive)} – ${dayName(stay.depart)} · ${stay.nights} night${if (stay.nights == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOfNotNull(
                l?.checkIn?.takeIf { booked }?.let { "Check-in" to it },
                l?.checkOut?.takeIf { booked }?.let { "Check-out" to it },
                l?.ref?.takeIf { booked }?.let { "Confirmation" to it },
                l?.price?.let { p ->
                    val total = p.total(stay.nights, travelers)
                    val rate = p.rateLabel()?.takeIf { total != p.copy(unit = null) }
                    (if (booked) "Paid" else "Estimate") to listOfNotNull(rate?.let { "${total.label()} ($it)" } ?: total.label(), p.note).joinToString(" · ")
                },
                l?.notes?.takeIf { booked }?.let { "Notes" to it },
            ).forEach { (k, v) ->
                Row { Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp)); Text(v, style = MaterialTheme.typography.bodyMedium) }
            }
            if (!booked) l?.how?.let { Text(rich(it), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (booked && l?.name != null) OutlinedButton(onClick = { onLink(MapsLinks.open(l.place, l.name, stay.name)) }) { Text("Maps") }
                l?.url?.let { url -> OutlinedButton(onClick = { onLink(url) }) { Text(if (booked) "Booking ↗" else "Book ↗") } }
            }
        }
    }
}
