package dev.tlong.traveler.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.ui.common.DragHandle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.bookablesOn
import dev.tlong.traveler.ui.bookings.tallyLabel
import dev.tlong.traveler.domain.PlacementIssue
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.checkPlacement
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.dayWarnings
import dev.tlong.traveler.domain.longLabel
import dev.tlong.traveler.domain.placementsOf
import dev.tlong.traveler.domain.scheduledIds
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.shortZone
import dev.tlong.traveler.domain.stayOf
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.domain.toTime
import dev.tlong.traveler.domain.window
import dev.tlong.traveler.domain.workHoursOn
import dev.tlong.traveler.domain.zone
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Commitment
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
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.SectionTitle
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One row of the day list: a slot header, or a plan item with its index in the stored plan. */
private sealed interface Row {
    val key: String

    data class Header(val slot: Slot) : Row { override val key = "h-${slot.key}" }

    data class Item(val index: Int, val item: PlanItem) : Row { override val key = "i-$index-${item.activityId}" }
}

private fun rowsOf(day: Day?): List<Row> {
    val plan = day?.plan.orEmpty().withIndex()
    return Slot.entries.flatMap { s ->
        listOf(Row.Header(s)) + plan.filter { Slot.of(it.value.slot) == s }.map { Row.Item(it.index, it.value) }
    }
}

/** Reads the plan back out of the rows after a drag: each item takes the slot of the header above it. */
private fun planOf(rows: List<Row>): List<PlanItem> {
    var slot = Slot.MORNING
    return rows.mapNotNull { r ->
        when (r) {
            is Row.Header -> { slot = r.slot; null }
            is Row.Item -> {
                val changed = Slot.of(r.item.slot) != slot
                val keepTime = r.item.time?.toTime()?.let { it in slot.window() } == true
                if (changed) r.item.copy(slot = slot.key, time = r.item.time.takeIf { keepTime }) else r.item
            }
        }
    }
}

private data class PendingDrop(val plan: List<PlanItem>, val activity: Activity, val issues: List<PlacementIssue>)

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
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val overlays = rememberOverlays()
    val local = date.toDate()
    val dayIndex = trip.days.indexOfFirst { it.date == date }
    val day = trip.day(date)
    val stay = day?.let { trip.stayOf(it) }
    var editingNote by remember(date) { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Slot?>(null) }
    var timeFor by remember { mutableStateOf<Edits.ItemRef?>(null) }
    var pendingDrop by remember { mutableStateOf<PendingDrop?>(null) }
    var booking by remember { mutableStateOf<BookingTarget?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }

    fun undoable(message: String) = scope.launch { snackbar.offerUndo(session, message) }

    // One state object for the screen's life: the drag handle keeps the callbacks it was created
    // with, so a state re-created per plan would leave a later drag editing a stale copy.
    var rows by remember { mutableStateOf(rowsOf(day)) }
    LaunchedEffect(day?.plan) { rows = rowsOf(day) }
    val listState = rememberLazyListState()
    val headerCount = 4 // items before the slot rows: header card, fixed, note, warnings
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - headerCount
        val t = to.index - headerCount
        if (f in rows.indices && t in rows.indices && t > 0) {
            rows = rows.toMutableList().apply { add(t, removeAt(f)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    fun commitDrag() {
        val trip = session.trip.value
        val newPlan = planOf(rows)
        val current = trip.day(date)?.plan.orEmpty()
        if (newPlan == current) return
        // The dragged item is the one whose slot changed, if any; check it before committing.
        val moved = newPlan.firstOrNull { n -> current.none { it.activityId == n.activityId && it.slot == n.slot } }
        val activity = moved?.let { trip.activity(it.activityId) }
        val issues = if (moved != null && activity != null && local != null) {
            checkPlacement(trip, activity, local, Slot.of(moved.slot), moved.time?.toTime()).filter { it.blocking }
        } else emptyList()
        if (issues.isNotEmpty() && activity != null) {
            pendingDrop = PendingDrop(newPlan, activity, issues)
        } else {
            if (session.edit("Reorder ${dayName(date)}") { Edits.setPlan(it, date, newPlan) }) {
                undoable(if (moved != null && activity != null) "Moved “${activity.name}” to ${Slot.of(moved.slot).label.lowercase()}" else "Reordered")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            local?.label() ?: date, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { contentDescription = local?.longLabel() ?: date },
                        )
                        Text(stay?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    SaveIndicator(saveState, session::retrySave)
                    IconButton(onClick = { trip.days.getOrNull(dayIndex - 1)?.let { navigator.day(trip.id, it.date, replace = true) } }, enabled = dayIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    IconButton(onClick = { trip.days.getOrNull(dayIndex + 1)?.let { navigator.day(trip.id, it.date, replace = true) } }, enabled = dayIndex in 0 until trip.days.lastIndex) {
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
        val work = stay?.let { workHoursOn(it, local) }
        val fixed = trip.commitments.filter { it.date == date }.sortedBy { it.start ?: "" }
        val transfers = trip.transfers.filter { it.date == date }
        val bookings = remember(trip, date) { trip.bookablesOn(date) }
        val warnings = remember(trip, date) { dayWarnings(trip, local) }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item("head") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(DayKind.of(day.kind)) { k -> session.edit("Change day type") { Edits.setDayKind(it, date, k.key) } }
                    stay?.zone?.takeIf { it != ZoneId.systemDefault() }?.let {
                        Text("Times are ${shortZone(it, local)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item("fixed") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Fixed", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                bookings.tallyLabel()?.let { TextButton(onClick = { navigator.bookings(trip.id, date) }) { Text(it) } }
                                TextButton(onClick = { booking = BookingTarget(null) }) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Add booking")
                                }
                            }
                            work?.let { Text("💻 Work ${it.label}" + (it.sourceLabel?.let { s -> " ($s)" } ?: ""), style = MaterialTheme.typography.bodyMedium) }
                            transfers.forEach { t ->
                                Text("${modeEmoji(t.shownMode)} ${modeLabel(t.shownMode)} ${listOfNotNull(t.depart, t.arrive).joinToString("–")} ${t.details ?: ""}".trim(), style = MaterialTheme.typography.bodyMedium)
                            }
                            fixed.forEach { c ->
                                Text(
                                    (if (c.isBooked) "🔒 " else "• ") + listOfNotNull(c.start, c.end).joinToString("–").let { if (it.isEmpty()) "" else "$it " } + c.title +
                                        (if (c.isBooked) " · booked" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit booking") { booking = BookingTarget(c) }.padding(vertical = 4.dp)
                                        .semantics { contentDescription = (if (c.isBooked) "Booked: " else "") + "${c.title} ${c.start ?: ""} to ${c.end ?: ""}" },
                                )
                            }
                        }
                    }
            }
            item("note") {
                if (day.note == null && !editingNote) TextButton(onClick = { editingNote = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Add a note") }
                else NoteCard(day.note, editing = editingNote, onEdit = { editingNote = true }, onCancel = { editingNote = false }) { text ->
                    editingNote = false
                    if (session.edit("Edit day note") { Edits.setDayNote(it, date, text) }) undoable("Saved your note for ${dayName(date)}")
                }
            }
            item("warnings") {
                if (warnings.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) { warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium) } }
                }
            }
            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorder, key = row.key) { isDragging ->
                    when (row) {
                        is Row.Header -> SlotHeader(row.slot, empty = rows.dropWhile { it != row }.drop(1).firstOrNull() !is Row.Item) { picker = row.slot }
                        is Row.Item -> {
                            val a = trip.activity(row.item.activityId)
                            val ref = Edits.ItemRef(date, row.index)
                            val itemRows = rows.filterIsInstance<Row.Item>()
                            val pos = itemRows.indexOf(row)
                            ItemRow(
                                a, a != null && trip.isBooked(a), row.item, isDragging || dragging == row.key,
                                handle = Modifier.longPressDraggableHandle(
                                    onDragStarted = { dragging = row.key; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { dragging = null; commitDrag() },
                                ),
                                onOpen = { a?.let { overlays.detail = it.id } },
                                onMove = { overlays.place = PlaceRequest(row.item.activityId, ref, date, Slot.of(row.item.slot)) },
                                onStatus = { s -> if (session.edit("Mark ${s.label}") { Edits.setStatus(it, ref, s) }) undoable("Marked “${a?.name}” ${s.label.lowercase()}") },
                                onTime = { timeFor = ref },
                                onPool = { if (session.edit("Unschedule") { Edits.remove(it, ref) }) undoable("“${a?.name}” is back in the activities list") },
                                onUp = if (pos > 0) ({ moveBy(session, date, rows, row, -1) }) else null,
                                onDown = if (pos < itemRows.lastIndex) ({ moveBy(session, date, rows, row, +1) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    picker?.let { slot ->
        PoolPicker(trip, stay?.id, date, slot, onDismiss = { picker = null }, onNew = {
            picker = null
            overlays.newEntry = NewEntryRequest(stay?.id ?: trip.stays.first().id, date, slot)
        }) { a ->
            picker = null
            val issues = local?.let { checkPlacement(trip, a, it, slot, null) }.orEmpty().filter { it.blocking }
            if (issues.isNotEmpty()) {
                overlays.place = PlaceRequest(a.id, null, date, slot)
            } else if (session.edit("Add ${a.name}") { Edits.place(it, a.id, date, slot) }) {
                undoable("Added “${a.name}” to the ${slot.label.lowercase()}")
            }
        }
    }

    timeFor?.let { ref ->
        TimeDialog(day?.plan?.getOrNull(ref.index)?.time, onDismiss = { timeFor = null }) { t ->
            timeFor = null
            if (session.edit("Set time") { Edits.setTime(it, ref, t) }) undoable(if (t == null) "Time cleared" else "Set to $t")
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
            onCancel = { pendingDrop = null; rows = rowsOf(day) },
        ) {
            pendingDrop = null
            if (session.edit("Move ${drop.activity.name}") { Edits.setPlan(it, date, drop.plan) }) undoable("Moved “${drop.activity.name}”")
        }
    }

    OverlayHost(session, trip, overlays, snackbar, onGoToDay = { if (it != date) navigator.day(trip.id, it) })
}

private data class BookingTarget(val existing: Commitment?)

/** "Move up/down" for screen readers and anyone who would rather not drag: steps past slot headers too. */
private fun moveBy(session: TripSession, date: String, rows: List<Row>, row: Row.Item, delta: Int) {
    val i = rows.indexOf(row)
    val j = (i + delta).coerceIn(1, rows.lastIndex)
    val next = rows.toMutableList().apply { add(j, removeAt(i)) }
    session.edit("Reorder") { Edits.setPlan(it, date, planOf(next)) }
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

@Composable
private fun SlotHeader(slot: Slot, empty: Boolean, onAdd: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(slot.label, Modifier.weight(1f))
            TextButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add to ${slot.label.lowercase()}" }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Add")
            }
        }
        HorizontalDivider()
        if (empty) Text("Nothing planned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun ItemRow(
    a: Activity?, booked: Boolean, item: PlanItem, lifted: Boolean, handle: Modifier,
    onOpen: () -> Unit, onMove: () -> Unit, onStatus: (ItemStatus) -> Unit, onTime: () -> Unit, onPool: () -> Unit,
    onUp: (() -> Unit)?, onDown: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val status = ItemStatus.of(item.status)
    val name = a?.name ?: "(removed activity)"
    val actions = buildList {
        add(CustomAccessibilityAction("Move to another day or time") { onMove(); true })
        onUp?.let { add(CustomAccessibilityAction("Move up") { it(); true }) }
        onDown?.let { add(CustomAccessibilityAction("Move down") { it(); true }) }
        add(CustomAccessibilityAction("Unschedule") { onPool(); true })
    }
    Surface(
        tonalElevation = if (lifted) 8.dp else 1.dp, shadowElevation = if (lifted) 6.dp else 0.dp,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { customActions = actions },
    ) {
        Row(Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handle.size(44.dp).semantics { contentDescription = "Hold and drag to reorder $name" }, contentAlignment = Alignment.Center) {
                Icon(DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).clickable(onClickLabel = "Open details", onClick = onOpen).padding(vertical = 6.dp)) {
                Text(
                    listOfNotNull(item.time, a?.tag, a?.marked(booked) ?: name).joinToString("  "),
                    style = MaterialTheme.typography.titleSmall,
                )
                a?.short?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val facts = a?.factsLine().orEmpty()
                if (status != ItemStatus.PROPOSED || facts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (status == ItemStatus.DONE) Pill("✓ Done")
                    if (status == ItemStatus.SKIPPED) Pill("Skipped")
                    if (a?.isCustom == true) Pill("Yours")
                    Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Actions for $name" }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Move to…") }, onClick = { menu = false; onMove() })
                    if (status != ItemStatus.DONE) DropdownMenuItem(text = { Text("Mark done") }, onClick = { menu = false; onStatus(ItemStatus.DONE) })
                    if (status != ItemStatus.SKIPPED) DropdownMenuItem(text = { Text("Mark skipped") }, onClick = { menu = false; onStatus(ItemStatus.SKIPPED) })
                    if (status != ItemStatus.PROPOSED) DropdownMenuItem(text = { Text("Back to planned") }, onClick = { menu = false; onStatus(ItemStatus.PROPOSED) })
                    DropdownMenuItem(text = { Text(if (item.time == null) "Set a time…" else "Change time…") }, onClick = { menu = false; onTime() })
                    onUp?.let { DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; it() }) }
                    onDown?.let { DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text("Unschedule") }, onClick = { menu = false; onPool() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PoolPicker(trip: Trip, stayId: String?, date: String, slot: Slot, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Activity) -> Unit) {
    var allStays by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf<String?>(null) }
    val scheduled = remember(trip) { trip.scheduledIds() }
    val list = trip.activities
        .filter { allStays || it.stayId == stayId }
        .filter { fit == null || it.fit == fit || (fit == "rainy-day" && "rainy-day" in it.conditions) }
        .sortedWith(compareBy<Activity> { it.id in scheduled }.then(byRecommendation))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add to ${dayName(date)}, ${slot.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New entry of your own") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("short" to "Short", "half-day" to "Half day", "evening" to "Evening", "rainy-day" to "Rainy day").forEach { (k, l) ->
                    FilterChip(fit == k, onClick = { fit = if (fit == k) null else k }, label = { Text(l) })
                }
                FilterChip(allStays, onClick = { allStays = !allStays }, label = { Text("Other stays") })
            }
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(list, key = { it.id }) { a ->
                    val planned = trip.placementsOf(a.id)
                    Column(Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp)) {
                        Text(listOfNotNull(a.tag, a.marked(trip.isBooked(a))).joinToString("  "), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(a.short, a.factsLine().ifEmpty { null }, if (planned.isNotEmpty()) "planned ${planned.joinToString { dayName(it.date) }}" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TimeDialog(current: String?, onDismiss: () -> Unit, onSet: (String?) -> Unit) {
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
                if (current != null) TextButton(onClick = { onSet(null) }) { Text("No fixed time") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
