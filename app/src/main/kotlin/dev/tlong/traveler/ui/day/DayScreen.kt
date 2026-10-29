package dev.tlong.traveler.ui.day

import dev.tlong.traveler.ui.common.bookedColor
import android.Manifest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import dev.tlong.traveler.domain.dayEvents
import dev.tlong.traveler.ui.common.LocalContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import dev.tlong.traveler.domain.DayLayout
import dev.tlong.traveler.domain.Span
import dev.tlong.traveler.domain.WorkBlock
import dev.tlong.traveler.domain.WorkPlan
import dev.tlong.traveler.domain.hhmm
import dev.tlong.traveler.domain.slotForTime
import dev.tlong.traveler.model.Stay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.bookablesOn
import dev.tlong.traveler.domain.PlacementIssue
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.checkPlacement
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.longLabel
import dev.tlong.traveler.domain.placementsOf
import dev.tlong.traveler.domain.scheduledIds
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.shortZone
import dev.tlong.traveler.domain.stayOf
import dev.tlong.traveler.domain.datesOf
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.toTime
import dev.tlong.traveler.domain.SlotFit
import dev.tlong.traveler.domain.calendarEntry
import dev.tlong.traveler.ui.common.addToCalendar
import dev.tlong.traveler.domain.suggestFor
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.ui.activity.TAGS
import dev.tlong.traveler.ui.common.SectionTitle
import androidx.compose.foundation.horizontalScroll
import dev.tlong.traveler.domain.window
import dev.tlong.traveler.domain.workHoursOn
import dev.tlong.traveler.domain.zone
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Commitment
import dev.tlong.traveler.domain.MapsLinks
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.model.Day
import dev.tlong.traveler.model.DayKind
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.PlanItem
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.activity.BookingDialog
import dev.tlong.traveler.ui.activity.saveBooking
import dev.tlong.traveler.ui.activity.ConflictDialog
import dev.tlong.traveler.ui.activity.NewEntryRequest
import dev.tlong.traveler.ui.activity.OverlayHost
import dev.tlong.traveler.ui.activity.PlaceRequest
import dev.tlong.traveler.ui.activity.dayName
import dev.tlong.traveler.ui.activity.normalizeTime
import dev.tlong.traveler.ui.activity.rememberOverlays
import dev.tlong.traveler.ui.common.byRecommendation
import dev.tlong.traveler.ui.common.marked
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.factsLine
import dev.tlong.traveler.ui.common.modeEmoji
import dev.tlong.traveler.ui.common.modeLabel
import dev.tlong.traveler.ui.common.offerUndo
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.common.rich
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import java.time.ZoneId
import kotlinx.coroutines.launch

private data class PendingDrop(val plan: List<PlanItem>, val activity: Activity, val issues: List<PlacementIssue>)

/** Where the activity picker adds: a part of the day, and a time when empty time was tapped. */
/** Where a picked activity goes; [freeMinutes] is how long until the next thing on the day. */
private data class AddAt(val slot: Slot, val time: String?, val freeMinutes: Int? = null)

private sealed interface Selected {
    data class Item(val index: Int) : Selected
    data class Work(val index: Int) : Selected
}

private fun minutesOf(t: String?) = t?.toTime()?.let { it.hour * 60 + it.minute }

private fun durationLabel(m: Int) = if (m < 60) "$m min" else "${m / 60} h" + (if (m % 60 > 0) " ${m % 60}" else "")

/** Everything on the day as calendar blocks, and the hours to draw: 08:00–22:00 at least, wider when something falls outside. */
private fun calendarOf(trip: Trip, day: Day, blocks: List<WorkBlock>): Pair<List<CalEntry>, Span> {
    val timed = mutableListOf<CalEntry>()
    blocks.forEachIndexed { i, b ->
        val s = minutesOf(b.start) ?: return@forEachIndexed
        timed += CalEntry("w-$i", Span(s, (minutesOf(b.end) ?: END).takeIf { it > s } ?: END), EntryKind.WORK, "💻 Work", b.label, movable = true, tag = i)
    }
    trip.transfers.filter { it.date == day.date }.forEach { t ->
        val s = minutesOf(t.depart) ?: return@forEach
        val e = minutesOf(t.arrive)?.takeIf { it > s } ?: minOf(s + 60, END)
        timed += CalEntry("t-${t.id}", Span(s, e), EntryKind.TRANSFER, "${modeEmoji(t.shownMode)} ${modeLabel(t.shownMode)}",
            listOfNotNull(listOfNotNull(t.depart, t.arrive).joinToString("–"), t.details).joinToString(" · "))
    }
    // An overnight flight in, on the stay's first day: the hour before it lands.
    trip.stayOf(day)?.takeIf { trip.datesOf(it).firstOrNull()?.toString() == day.date }?.let { stay ->
        trip.transfers.filter { it.to == stay.id && it.date < day.date }.forEach { t ->
            val e = minutesOf(t.arrive) ?: return@forEach
            timed += CalEntry("ta-${t.id}", Span(maxOf(e - 60, 0), e), EntryKind.TRANSFER, "${modeEmoji(t.shownMode)} ${modeLabel(t.shownMode)}",
                listOfNotNull("arrives ${t.arrive}", t.details).joinToString(" · "))
        }
    }
    // A booking for a planned activity is drawn as that activity, at the booked time.
    val linked = linkedBookings(trip, day)
    val looseBookings = mutableListOf<Commitment>()
    trip.commitments.filter { it.date == day.date && it !in linked.values }.forEach { c ->
        val s = minutesOf(c.start) ?: return@forEach run { looseBookings += c }
        val e = minutesOf(c.end)?.takeIf { it > s } ?: minOf(s + 60, END)
        timed += CalEntry("c-${c.id}", Span(s, e), EntryKind.BOOKING, (if (c.isBooked) "🔒 " else "") + c.title, listOfNotNull(c.start, c.end).joinToString("–"), tag = c)
    }
    val loose = mutableListOf<Pair<CalEntry, Slot>>()
    day.plan.forEachIndexed { i, item ->
        val a = trip.activity(item.activityId)
        val minutes = (a?.duration?.minutes ?: 60).coerceAtLeast(30)
        val status = ItemStatus.of(item.status)
        val pill = when (status) { ItemStatus.DONE -> "✅ Done"; ItemStatus.SKIPPED -> "Skipped"; else -> null }
        val booked = a != null && trip.isBooked(a)
        val title = listOfNotNull(a?.tag, a?.marked(booked) ?: "(removed activity)").joinToString(" ")
        val c = linked[item.activityId]
        val s = minutesOf(c?.start ?: item.time)
        val e = minutesOf(c?.end)?.takeIf { s != null && it > s } ?: s?.let { minOf(it + minutes, END) }
        if (s != null && e != null) {
            val label = if (c?.end != null) "${c.start}–${c.end}" else "${c?.start ?: item.time} · ${durationLabel(minutes)}"
            timed += CalEntry("i-$i-${item.activityId}", Span(s, e), EntryKind.ACTIVITY, title, label, pill = pill, movable = c == null, booked = booked, tag = i)
        } else loose += CalEntry("i-$i-${item.activityId}", Span(0, minutes), EntryKind.ACTIVITY, title, "anytime · ${durationLabel(minutes)}", loose = true, pill = pill, movable = c == null, booked = booked, tag = i) to Slot.of(item.slot)
    }
    looseBookings.forEach { c -> loose += CalEntry("c-${c.id}", Span(0, 60), EntryKind.BOOKING, (if (c.isBooked) "🔒 " else "") + c.title, "no time set", loose = true, tag = c) to Slot.ALLDAY }

    val range = Span(
        minOf(8 * 60, timed.minOfOrNull { it.span.start / 60 * 60 } ?: 8 * 60),
        maxOf(22 * 60, timed.maxOfOrNull { (it.span.end + 59) / 60 * 60 } ?: 0).coerceAtMost(END),
    )
    fun window(s: Slot) = when (s) {
        Slot.MORNING -> Span(range.start, 12 * 60)
        Slot.AFTERNOON -> Span(12 * 60, 17 * 60)
        Slot.EVENING -> Span(17 * 60, range.end)
        Slot.ALLDAY -> range
    }
    val placed = DayLayout.placeLoose(timed.map { it.span }, loose.map { (e, s) -> window(s) to e.span.minutes.coerceAtMost(window(s).minutes) })
    return timed + loose.mapIndexed { i, (e, _) -> e.copy(span = placed[i]) } to range
}

/** The day's bookings linked to an activity on its plan, by activity id. */
private fun linkedBookings(trip: Trip, day: Day): Map<String, Commitment> {
    val planned = day.plan.map { it.activityId }.toSet()
    return trip.commitments.filter { it.date == day.date && it.activityId in planned }.associateBy { it.activityId!! }
}

private const val END = 24 * 60 - 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(tripId: String, date: String, navigator: Navigator) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    DayContent(session, date, navigator)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayContent(session: TripSession, date: String, navigator: Navigator) {
    val trip by session.trip.collectAsStateWithLifecycle()
    val work by session.work.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val overlays = rememberOverlays()
    val local = date.toDate()
    val dayIndex = trip.days.indexOfFirst { it.date == date }
    val day = trip.day(date)
    val stay = day?.let { trip.stayOf(it) }
    var editingNote by remember(date) { mutableStateOf(false) }
    var picker by remember { mutableStateOf<AddAt?>(null) }
    var timeFor by remember { mutableStateOf<Edits.ItemRef?>(null) }
    var workTimeFor by remember { mutableStateOf<Int?>(null) }
    var pendingDrop by remember { mutableStateOf<PendingDrop?>(null) }
    var booking by remember { mutableStateOf<BookingTarget?>(null) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val container = LocalContainer.current
    val context = LocalContext.current
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) exporting = true
        else scope.launch { snackbar.showSnackbar("Calendar access is off") }
    }

    fun undoable(message: String) = scope.launch { snackbar.offerUndo(session, message) }

    val hours = remember(stay, local) { if (stay != null && local != null) workHoursOn(stay, local) else null }
    val blocks = work.blocksOn(date, hours)

    /** Work blocks have their own undo: they live beside the trip file, outside the trip's undo stack. */
    fun setBlocks(next: List<WorkBlock>, message: String) {
        val before = session.work.value
        session.setWork(before.with(date, next, hours))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) session.setWork(before)
        }
    }

    /** Gives a planned item a time, or none; a clash with a booking or work asks first. */
    fun setTime(ref: Edits.ItemRef, time: String?) {
        val t = session.trip.value
        val next = Edits.setTime(t, ref, time)
        val item = next.day(date)?.plan?.getOrNull(ref.index) ?: return
        val a = t.activity(item.activityId)
        val issues = if (a != null && local != null && time != null) {
            checkPlacement(t, a, local, Slot.of(item.slot), time.toTime(), ref, blocks).filter { it.blocking }
        } else emptyList()
        if (a != null && issues.isNotEmpty()) pendingDrop = PendingDrop(next.day(date)!!.plan, a, issues)
        else if (session.edit("Set time") { Edits.setTime(it, ref, time) }) undoable(if (time == null) "“${a?.name}” is anytime" else "“${a?.name}” at $time")
    }

    fun goTo(offset: Int) { trip.days.getOrNull(dayIndex + offset)?.let { navigator.day(trip.id, it.date, replace = true) } }
    // A sideways swipe anywhere on the day moves to the next or previous one, like the arrows.
    var swipe by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        local?.label() ?: date, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { contentDescription = local?.longLabel() ?: date },
                    )
                },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    SaveIndicator(saveState, session::retrySave)
                    IconButton(onClick = { goTo(-1) }, enabled = dayIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    IconButton(onClick = { goTo(1) }, enabled = dayIndex in 0 until trip.days.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (day == null || local == null) {
            Text("This date is not part of the trip.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val bookings = remember(trip, date) { trip.bookablesOn(date) }
        val (entries, range) = remember(trip, day, blocks) { calendarOf(trip, day, blocks) }
        // The day's route: what is still to do, in the calendar's order, from the stay's lodging.
        val stops = remember(entries) {
            entries.sortedBy { it.span.start }.mapNotNull { e ->
                when (e.kind) {
                    EntryKind.ACTIVITY -> day.plan.getOrNull(e.tag as Int)?.takeIf { ItemStatus.of(it.status) == ItemStatus.PROPOSED }
                        ?.let { trip.activity(it.activityId) }?.let { MapsLinks.stop(it.place, it.name, stay?.name) }
                    EntryKind.BOOKING -> (e.tag as Commitment).let { c -> c.place?.let { MapsLinks.stop(it, c.title, stay?.name) } }
                    else -> null
                }
            }
        }
        val now = stay?.zone?.let { z -> java.time.ZonedDateTime.now(z).takeIf { it.toLocalDate() == local }?.let { it.hour * 60 + it.minute } }

        Column(
            Modifier.fillMaxSize().padding(padding)
                .onGloballyPositioned { c -> c.boundsInWindow().let { viewport = it.top..it.bottom } }
                .draggable(
                    rememberDraggableState { swipe += it }, Orientation.Horizontal,
                    onDragStarted = { swipe = 0f },
                    onDragStopped = { v ->
                        when {
                            swipe < -swipeThreshold || v < -1500f -> goTo(1)
                            swipe > swipeThreshold || v > 1500f -> goTo(-1)
                        }
                        swipe = 0f
                    },
                )
                .graphicsLayer { translationX = swipe / 3 }
                .verticalScroll(scroll)
                .padding(bottom = 48.dp),
        ) {
            var addMenu by remember { mutableStateOf(false) }
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(DayKind.of(day.kind)) { k -> session.edit("Change day type") { Edits.setDayKind(it, date, k.key) } }
                // Only when the stay's zone differs from the phone's: the offset alone.
                stay?.zone?.takeIf { it != ZoneId.systemDefault() }?.let {
                    Text("UTC" + it.rules.getOffset(local.atTime(12, 0).atZone(it).toInstant()).id.replace("Z", ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = "Times are ${shortZone(it, local)}" })
                }
                Spacer(Modifier.weight(1f))
                if (stops.isNotEmpty()) IconButton(onClick = {
                    val lodging = stay?.lodging?.place?.let { MapsLinks.stop(it, stay.lodging.name ?: "", stay.name) }
                    scope.launch {
                        when {
                            !openUrl(context, MapsLinks.route(lodging, stops)!!) -> snackbar.showSnackbar("No app can open Google Maps")
                            stops.size > MapsLinks.MAX_STOPS -> snackbar.showSnackbar("Directions cover the first ${MapsLinks.MAX_STOPS} stops")
                        }
                    }
                }) { Text("🧭", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = { navigator.tripCalendar(trip.id) }) { Icon(Icons.Default.DateRange, "Trip calendar") }
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Add to this day") }
                    DropdownMenu(addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("Activity…") }, onClick = { addMenu = false; picker = AddAt(Slot.ALLDAY, null) })
                        DropdownMenuItem(text = { Text("Booking…") }, onClick = { addMenu = false; booking = BookingTarget(null) })
                        DropdownMenuItem(text = { Text("Add to calendar…") }, onClick = {
                            addMenu = false
                            val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            if (perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) exporting = true
                            else askCalendar.launch(perms)
                        })
                        DropdownMenuItem(text = { Text("Work block") }, onClick = {
                            addMenu = false
                            val start = blocks.maxOfOrNull { minutesOf(it.end) ?: 0 } ?: (9 * 60)
                            val b = WorkBlock(hhmm(start), hhmm(minOf(start + 120, END)))
                            setBlocks(blocks + b, "Added work ${b.label}")
                        })
                        if (day.note == null) DropdownMenuItem(text = { Text("Note") }, onClick = { addMenu = false; editingNote = true })
                        if (bookings.isNotEmpty()) DropdownMenuItem(text = { Text("This day's bookings") }, onClick = { addMenu = false; navigator.bookings(trip.id, date) })
                    }
                }
            }
            if (day.note != null || editingNote) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                NoteCard(day.note, editing = editingNote, onEdit = { editingNote = true }, onCancel = { editingNote = false }) { text ->
                    editingNote = false
                    if (session.edit("Edit day note") { Edits.setDayNote(it, date, text) }) undoable("Saved your note for ${dayName(date)}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(end = 12.dp)) {
                DayCalendar(
                    entries, range, scroll, { viewport }, now,
                    onTap = { e ->
                        when (e.kind) {
                            EntryKind.ACTIVITY -> selected = Selected.Item(e.tag as Int)
                            EntryKind.WORK -> selected = Selected.Work(e.tag as Int)
                            EntryKind.BOOKING -> booking = BookingTarget(e.tag as Commitment)
                            EntryKind.TRANSFER -> {}
                        }
                    },
                    onMoved = { e, start ->
                        when (e.kind) {
                            EntryKind.ACTIVITY -> setTime(Edits.ItemRef(date, e.tag as Int), hhmm(start))
                            EntryKind.WORK -> {
                                val i = e.tag as Int
                                val b = WorkBlock(hhmm(start), hhmm(minOf(start + e.span.minutes, END)))
                                setBlocks(blocks.toMutableList().apply { set(i, b) }, "Work moved to ${b.label}")
                            }
                            else -> {}
                        }
                    },
                    onEmpty = { m ->
                        val next = entries.filter { !it.loose && it.span.start > m }.minOfOrNull { it.span.start }
                        picker = AddAt(slotForTime(java.time.LocalTime.of(m / 60 % 24, m % 60)), hhmm(m), next?.let { it - m })
                    },
                )
            }
        }
    }

    when (val s = selected) {
        is Selected.Item -> {
            val item = day?.plan?.getOrNull(s.index)
            if (item == null) selected = null else {
                val a = trip.activity(item.activityId)
                val ref = Edits.ItemRef(date, s.index)
                val status = ItemStatus.of(item.status)
                fun act(f: () -> Unit) { selected = null; f() }
                ActionSheet(
                    listOfNotNull(a?.tag, a?.marked(trip.isBooked(a)) ?: "(removed activity)").joinToString(" "),
                    item.time ?: "Anytime this ${Slot.of(item.slot).label.lowercase()}",
                    onDismiss = { selected = null },
                    buildList {
                        a?.let { add("Details" to { act { overlays.detail = it.id } }) }
                        val bookedAs = day?.let { linkedBookings(trip, it)[item.activityId] }
                        if (bookedAs != null) add("Edit booking…" to { act { booking = BookingTarget(bookedAs) } })
                        else {
                            add((if (item.time == null) "Set a time…" else "Change time…") to { act { timeFor = ref } })
                            if (item.time != null) add("Make it anytime" to { act { setTime(ref, null) } })
                        }
                        if (status != ItemStatus.DONE) add("Mark done" to { act { if (session.edit("Mark done") { Edits.setStatus(it, ref, ItemStatus.DONE) }) undoable("Marked “${a?.name}” done") } })
                        trip.calendarEntry(date, item)?.let { e -> add("Add to calendar" to { act { addToCalendar(context, e) } }) }
                        if (status != ItemStatus.PROPOSED) add("Back to planned" to { act { session.edit("Mark planned") { Edits.setStatus(it, ref, ItemStatus.PROPOSED) } } })
                        add("Move to another day…" to { act { overlays.place = PlaceRequest(item.activityId, ref, date, Slot.of(item.slot)) } })
                        add("Unschedule" to { act { if (session.edit("Unschedule") { Edits.remove(it, ref) }) undoable("“${a?.name}” is back in the activities list") } })
                    },
                )
            }
        }
        is Selected.Work -> {
            val b = blocks.getOrNull(s.index)
            if (b == null) selected = null else {
                fun act(f: () -> Unit) { selected = null; f() }
                ActionSheet(
                    "💻 Work ${b.label}", stay?.let { st -> local?.let { homeLabel(st, it, b) } },
                    onDismiss = { selected = null },
                    buildList {
                        add("Change time…" to { act { workTimeFor = s.index } })
                        add("Cancel this block" to { act { setBlocks(blocks - b, "Cancelled work ${b.label}") } })
                        if (work.isEdited(date)) add("Reset today's work" to { act { setBlocks(WorkPlan.defaultBlocks(hours), "Work reset") } })
                    },
                )
            }
        }
        null -> {}
    }

    if (exporting && local != null) {
        val events = remember(trip, blocks) { dayEvents(trip, date, blocks) }
        CalendarSheet(
            local.label(), events, container.calendar.exported(trip.id, date).size, container.calendar,
            onDismiss = { exporting = false },
        ) { cal, picked ->
            exporting = false
            container.calendar.chosen = cal.id
            scope.launch {
                val n = runCatching { container.calendar.replace(cal.id, trip.id, local, stay?.zone ?: ZoneId.systemDefault(), picked) }
                snackbar.showSnackbar(n.fold({ "Added $it to ${cal.name}" }, { "Could not write to the calendar" }))
            }
        }
    }

    picker?.let { at ->
        PoolPicker(trip, stay?.id, date, at, blocks, onDismiss = { picker = null }, onNew = {
            picker = null
            overlays.newEntry = NewEntryRequest(stay?.id ?: trip.stays.first().id, date, at.slot)
        }) { a ->
            picker = null
            val issues = local?.let { checkPlacement(trip, a, it, at.slot, at.time?.toTime(), work = blocks) }.orEmpty().filter { it.blocking }
            if (issues.isNotEmpty()) {
                overlays.place = PlaceRequest(a.id, null, date, at.slot)
            } else if (session.edit("Add ${a.name}") { Edits.place(it, a.id, date, at.slot, at.time) }) {
                undoable("Added “${a.name}”" + (at.time?.let { " at $it" } ?: ""))
            }
        }
    }

    timeFor?.let { ref ->
        TimeDialog(day?.plan?.getOrNull(ref.index)?.time, onDismiss = { timeFor = null }) { t ->
            timeFor = null
            setTime(ref, t)
        }
    }

    workTimeFor?.let { i ->
        val b = blocks.getOrNull(i)
        if (b == null) workTimeFor = null else TimeDialog(b.start, clearable = false, onDismiss = { workTimeFor = null }) { t ->
            workTimeFor = null
            val s = minutesOf(t) ?: return@TimeDialog
            val length = (minutesOf(b.end) ?: END) - (minutesOf(b.start) ?: 0)
            val moved = WorkBlock(hhmm(s), hhmm(minOf(s + length, END)))
            setBlocks(blocks.toMutableList().apply { set(i, moved) }, "Work moved to ${moved.label}")
        }
    }

    booking?.let { target ->
        val existing = target.existing
        BookingDialog(
            listOf(date), date, existing, suggestions = trip.activities.filter { it.stayId == stay?.id }, travelers = trip.travelers ?: 1,
            onDismiss = { booking = null },
            onDelete = existing?.let { c -> {
                booking = null
                if (session.edit("Delete booking") { Edits.deleteBooking(it, c.id) }) undoable("Deleted “${c.title}”")
            } },
        ) { b ->
            booking = null
            if (saveBooking(session, existing, b)) undoable("Saved “${b.title}”")
        }
    }

    pendingDrop?.let { drop ->
        ConflictDialog(
            "Move “${drop.activity.name}” here?", drop.issues,
            onCancel = { pendingDrop = null },
        ) {
            pendingDrop = null
            if (session.edit("Move ${drop.activity.name}") { Edits.setPlan(it, date, drop.plan) }) undoable("Moved “${drop.activity.name}”")
        }
    }

    OverlayHost(session, trip, overlays, snackbar, onGoToDay = { if (it != date) navigator.day(trip.id, it) })
}

private data class BookingTarget(val existing: Commitment?)

/** A work block in the worker's home zone, when the rhythm is kept in one that differs from the stay's. */
private fun homeLabel(stay: Stay, date: java.time.LocalDate, b: WorkBlock): String? {
    val home = stay.workRhythm?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return null
    val here = stay.zone
    if (home == here) return null
    fun conv(t: java.time.LocalTime) = java.time.ZonedDateTime.of(date, t, here).withZoneSameInstant(home).toLocalTime().hhmm()
    return "${conv(b.startTime)}–${conv(b.endTime)} ${shortZone(home, date)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(title: String, subtitle: String?, onDismiss: () -> Unit, actions: List<Pair<String, () -> Unit>>) {
    // Fully open and scrollable so every action is reachable above the gesture bar.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp)) }
            Spacer(Modifier.height(8.dp))
            actions.forEach { (label, onClick) ->
                Text(label, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 24.dp).wrapContentHeight(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun KindChip(kind: DayKind, onPick: (DayKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = kind != DayKind.PLAN, onClick = { open = true }, label = { Text(kind.label) })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DayKind.entries.forEach { k -> DropdownMenuItem(text = { Text(k.label) }, onClick = { open = false; onPick(k) }) }
        }
    }
}

@Composable
private fun NoteCard(note: String?, editing: Boolean, onEdit: () -> Unit, onCancel: () -> Unit, onSave: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) {
                // Start with the cursor at the end of the existing words, keyboard up.
                var text by remember { mutableStateOf(TextFieldValue(note.orEmpty(), TextRange(note.orEmpty().length))) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSave(text.text) }) { Text("Save") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                Text(rich(note!!), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit the note", onClick = onEdit))
            }
        }
    }
}

/** What to add at a free time: what fits first, then the rest with why not, then what is planned, done or turned down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolPicker(trip: Trip, stayId: String?, date: String, at: AddAt, work: List<WorkBlock>, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Activity) -> Unit) {
    var allStays by remember { mutableStateOf(false) }
    var rainy by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf<String?>(null) }
    val here = trip.activities.filter { allStays || it.stayId == stayId }
    val tags = remember(here) { here.mapNotNull { it.tag }.distinct() }
    val local = date.toDate() ?: return
    val fits = remember(trip, here, at, work, tag, rainy) {
        val pool = here.filter { (tag == null || it.tag == tag) && (!rainy || "rainy-day" in it.conditions) }
        suggestFor(trip, pool, local, at.slot, at.time?.toTime(), at.freeMinutes, work).groupBy { it.group }
    }
    val whenLabel = at.time ?: if (at.slot == Slot.ALLDAY) null else at.slot.label.lowercase()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add to ${dayName(date)}" + (whenLabel?.let { ", $it" } ?: ""), style = MaterialTheme.typography.titleLarge)
            at.freeMinutes?.let { m -> Text("Free until ${hhmm((at.time?.let(::minutesOf) ?: 0) + m)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Glyph chips for the types here, like the Bookings filters.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    FilterChip(tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) },
                        modifier = Modifier.semantics { contentDescription = TAGS[t] ?: t })
                }
                FilterChip(rainy, onClick = { rainy = !rainy }, label = { Text("🌧️") }, modifier = Modifier.semantics { contentDescription = "Good on a rainy day" })
                FilterChip(allStays, onClick = { allStays = !allStays }, label = { Text("Other stays") })
            }
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SlotFit.Group.entries.forEach { g ->
                    val list = fits[g].orEmpty()
                    if (list.isEmpty()) return@forEach
                    item("h-$g") {
                        SectionTitle(when (g) {
                            SlotFit.Group.FITS -> "Fits" + (whenLabel?.let { " $it" } ?: " this day")
                            SlotFit.Group.OTHER -> "Other ideas"
                            SlotFit.Group.PLANNED -> "Planned or done"
                            SlotFit.Group.WONT_DO -> "Won't do"
                        }, Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.activity.id }) { f -> PickRow(trip, f) { onPick(f.activity) } }
                }
                item("new") { TextButton(onClick = onNew, modifier = Modifier.padding(top = 8.dp)) { Text("+ New entry of your own") } }
            }
        }
    }
}

@Composable
private fun PickRow(trip: Trip, f: SlotFit, onClick: () -> Unit) {
    val a = f.activity
    val booked = trip.isBooked(a)
    val done = f.reason == "done"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(listOfNotNull(a.tag, a.marked(booked, done)).joinToString("  "), style = MaterialTheme.typography.titleSmall,
            color = if (a.isWontDo) muted else bookedColor(booked || done))
        // Why it doesn't fit, when it doesn't; otherwise what it is.
        val line = f.reason?.takeIf { !done } ?: listOfNotNull(a.short, a.factsLine().ifEmpty { null }).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider()
}

@Composable
private fun TimeDialog(current: String?, clearable: Boolean = true, onDismiss: () -> Unit, onSet: (String?) -> Unit) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    val t = normalizeTime(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time") },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text("24-hour time, e.g. 1430") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, isError = text.isNotBlank() && t == null)
        },
        confirmButton = { TextButton(onClick = { onSet(t) }, enabled = t != null) { Text("Set") } },
        dismissButton = {
            Row {
                if (current != null && clearable) TextButton(onClick = { onSet(null) }) { Text("Anytime") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
