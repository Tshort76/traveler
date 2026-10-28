package dev.tlong.traveler.ui.stay

import dev.tlong.traveler.ui.common.TriStateFilter
import dev.tlong.traveler.ui.common.EarlierRow
import dev.tlong.traveler.ui.common.FilterList
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.tlong.traveler.ui.activity.TAGS
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.dates
import dev.tlong.traveler.domain.datesOf
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.departDate
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.toTime
import dev.tlong.traveler.domain.weekdayShort
import dev.tlong.traveler.domain.weeks
import dev.tlong.traveler.domain.withNights
import dev.tlong.traveler.domain.workHoursOn
import dev.tlong.traveler.domain.WorkPlan
import dev.tlong.traveler.ui.common.modeEmoji
import dev.tlong.traveler.ui.common.modeLabel
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
import dev.tlong.traveler.ui.common.bookedColor
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.offerUndo
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.conditionLabel
import dev.tlong.traveler.ui.common.factsLine
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import java.time.LocalDate
import kotlinx.coroutines.launch

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
    var editingNights by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pool = remember(trip, stay.id) { trip.activities.filter { it.stayId == stay.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stay.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    SaveIndicator(saveState, session::retrySave)
                    IconButton(onClick = { editingNights = true }) { Icon(Icons.Default.DateRange, "Change nights") }
                },
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
    if (editingNights) NightsDialog(trip, stay, onDismiss = { editingNights = false }) { nights, from ->
        editingNights = false
        if (session.edit("Change nights") { it.withNights(stay.id, nights, from)?.trip ?: it }) {
            scope.launch { snackbar.offerUndo(session, "${stay.name}: $nights night${if (nights == 1) "" else "s"}") }
        }
    }
}

@Composable
private fun ItineraryTab(trip: Trip, stay: Stay, work: WorkPlan, onDay: (String) -> Unit, onActivity: (String) -> Unit) {
    // The stay's days, then the day it is left, which belongs to the next stay.
    val own = remember(trip, stay.id) { trip.datesOf(stay) }
    val leaving = stay.departDate.takeIf { it !in own && it in trip.dates() }
    val dates = own + listOfNotNull(leaving)
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
            items(shown, key = { it.toString() }) { d -> DayCard(trip, stay, work, d, d == today, leaving = d == leaving, onClick = { onDay(d.toString()) }, onActivity = onActivity) }
        }
    }
}

@Composable
private fun DayCard(trip: Trip, stay: Stay, workPlan: WorkPlan, date: LocalDate, isToday: Boolean, leaving: Boolean, onClick: () -> Unit, onActivity: (String) -> Unit) {
    val day = trip.day(date.toString())
    val kind = DayKind.of(day?.kind)
    // A booking for a planned activity shows as that activity's row, not a second line.
    // The day it is left shows only this stay's part of it: what is planned here and the way out.
    val plan = day?.plan.orEmpty().filter { !leaving || trip.activity(it.activityId)?.stayId == stay.id }
    val planned = plan.map { it.activityId }.toSet()
    val booked = trip.commitments.filter { it.date == date.toString() && it.activityId !in planned && (!leaving || it.stayId == stay.id) }
    // The day's actual work blocks, first start to last end.
    val blocks = if (leaving) emptyList() else workPlan.blocksOn(date.toString(), workHoursOn(stay, date))
    val work = blocks.takeIf { it.isNotEmpty() }?.let { "${it.first().start}–${it.maxOf { b -> b.end }}" }
    val rows = buildList {
        plan.forEach { item ->
            val a = trip.activities.firstOrNull { it.id == item.activityId } ?: return@forEach
            val status = when (ItemStatus.of(item.status)) { ItemStatus.DONE -> "✓ "; ItemStatus.SKIPPED -> "⨯ "; else -> "" }
            add(ItineraryRow(minuteOf(item.time) ?: Slot.of(item.slot).minute, "$status${item.time ?: Slot.of(item.slot).label}  ${a.tag ?: "•"} ${a.marked(trip.isBooked(a))}", trip.isBooked(a)) { onActivity(a.id) })
        }
        // Flights and other transfers leaving today; on the stay's first day, also one that left the day before (an overnight flight in).
        trip.transfers.filter { it.date == date.toString() && (!leaving || it.from == stay.id) }.forEach { t ->
            add(ItineraryRow(minuteOf(t.depart) ?: 0, "${t.depart ?: "Travel"}  ${modeEmoji(t.shownMode)} ${modeLabel(t.shownMode)} to ${trip.stay(t.to)?.name ?: t.to}", onClick = onClick))
        }
        if (date == trip.datesOf(stay).firstOrNull()) trip.transfers.filter { it.to == stay.id && it.date < date.toString() }.forEach { t ->
            add(ItineraryRow(minuteOf(t.arrive) ?: 0, "${t.arrive ?: "Travel"}  ${modeEmoji(t.shownMode)} ${modeLabel(t.shownMode)} from ${trip.stay(t.from)?.name ?: t.from}", onClick = onClick))
        }
    }
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
            rows.sortedBy { it.minute }.forEach { r ->
                TextButton(onClick = r.onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(r.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = bookedColor(r.booked, MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
}

/** One line on a day card, ordered by [minute]: its time, or where its part of the day starts. */
private class ItineraryRow(val minute: Int, val text: String, val booked: Boolean = false, val onClick: () -> Unit)

private fun minuteOf(time: String?) = time?.toTime()?.let { it.hour * 60 + it.minute }

private val Slot.minute get() = when (this) { Slot.MORNING -> 9 * 60; Slot.AFTERNOON -> 13 * 60; Slot.EVENING -> 18 * 60; Slot.ALLDAY -> 12 * 60 }

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
    val done = remember(trip) { trip.days.flatMap { d -> d.plan.filter { ItemStatus.of(it.status) == ItemStatus.DONE }.map { it.activityId } }.toSet() }
    var order by rememberSaveable { mutableStateOf(PoolOrder.BEST) }
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
    // By type: the types in the order of their best activity, untagged last.
    val groups = when (order) {
        PoolOrder.BEST -> listOf(null to list)
        PoolOrder.TYPE -> list.groupBy { it.tag }.toList().sortedBy { it.first == null }
            .map { (tag, l) -> (tag?.let { "$it ${TAGS[it] ?: ""}".trim() } ?: "Other") to l }
    }
    val more = fits.size + efforts.size + conditions.size + tags.size
    var advanced by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item("filters") {
            // Quick toggles up front; the rarely needed ones fold away behind the filter icon.
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OrderMenu(order) { order = it }
                if (pool.any { it.stars == 3 }) Toggle("✨", "Standouts", standouts) { standouts = !standouts }
                TriStateFilter(planned, { when (it) { null -> "Showing planned and unplanned"; true -> "Showing planned only"; false -> "Showing unplanned only" } }) { planned = it }
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
        groups.forEach { (title, members) ->
            title?.let { item("h-$it") { SectionTitle(it, Modifier.padding(top = 12.dp)) } }
            items(members, key = { it.id }) { a ->
                val add = { overlays.place = PlaceRequest(a.id, null, null, null) }
                PoolRow(a, scheduled[a.id].orEmpty(), trip.isBooked(a), a.id in done, showTag = title == null, onOpen = { overlays.detail = a.id }, onAdd = add)
            }
        }
    }
}

private enum class PoolOrder(val label: String) { BEST("Best first"), TYPE("By type") }

/** The order as a menu, like the Bookings list's. */
@Composable
private fun OrderMenu(order: PoolOrder, onChange: (PoolOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(start = 0.dp, end = 4.dp)) {
            Text(order.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change order")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            PoolOrder.entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) }, onClick = { onChange(o); open = false },
                    leadingIcon = { if (order == o) Icon(Icons.Default.Check, contentDescription = null) },
                )
            }
        }
    }
}

private val FITS = listOf("short" to "Short", "half-day" to "Half day", "full-day" to "Full day", "evening" to "Evening")
private val EFFORTS = listOf("easy" to "Easy", "moderate" to "Moderate", "hard" to "Demanding")

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
private fun PoolRow(a: Activity, plannedOn: List<String>, booked: Boolean, done: Boolean, showTag: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    // A list row, not a card: the details line says where it is planned, then the facts.
    val planned = plannedOn.mapNotNull { it.toDate()?.let { d -> "${d.weekdayShort()} ${d.dayOfMonth}" } }
    val details = listOfNotNull(planned.takeIf { it.isNotEmpty() }?.let { "🗓️ " + it.joinToString(", ") }, a.factsLine().ifEmpty { null }, "Yours".takeIf { a.isCustom })
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // Done reads like booked: a ✓ and green.
                Text(listOfNotNull(a.tag.takeIf { showTag }, a.marked(booked || done)).joinToString("  "), style = MaterialTheme.typography.titleSmall, color = bookedColor(booked || done))
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

