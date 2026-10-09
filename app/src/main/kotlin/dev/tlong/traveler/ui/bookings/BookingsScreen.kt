package dev.tlong.traveler.ui.bookings

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.ui.common.TriStateFilter
import dev.tlong.traveler.ui.common.marked
import dev.tlong.traveler.domain.arriveDate
import dev.tlong.traveler.domain.departDate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Bookable
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.bookables
import dev.tlong.traveler.domain.dates
import dev.tlong.traveler.domain.datesOf
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.money
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.totals
import dev.tlong.traveler.domain.rateLabel
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.activity.BookingDialog
import dev.tlong.traveler.ui.activity.dayName
import dev.tlong.traveler.ui.activity.normalizeTime
import dev.tlong.traveler.ui.activity.saveBooking
import dev.tlong.traveler.ui.common.PriceField
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.modeLabel
import dev.tlong.traveler.ui.activity.TAGS
import dev.tlong.traveler.ui.common.modeEmoji
import dev.tlong.traveler.ui.common.offerUndo
import dev.tlong.traveler.ui.common.rememberPriceInput
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.ui.common.rich

@Composable
fun BookingsScreen(tripId: String, date: String?, navigator: Navigator, stayId: String? = null, group: String? = null) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    BookingsContent(session, date, navigator, stayId, group)
}

fun Bookable.Group.glyph(): String = when (this) {
    Bookable.Group.LOGISTICS -> "🧳"
    Bookable.Group.EVENTS -> "🎟️"
}

fun Bookable.glyph(): String = when (kind) {
    Bookable.Kind.LODGING -> "🏨"
    Bookable.Kind.TRANSFER -> modeEmoji(glyph)
    Bookable.Kind.ACTIVITY -> glyph ?: "🎟️"
    Bookable.Kind.BOOKING -> glyph ?: "🎫"
}

/** "2/3 booked", or "✅ 3/3 booked" once everything is; null when nothing needs booking. */
fun List<Bookable>.tallyLabel(): String? =
    if (isEmpty()) null else "${if (all { it.booked }) "✅ " else ""}${count { it.booked }}/$size booked"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingsContent(session: TripSession, focusDate: String?, navigator: Navigator, focusStay: String?, focusGroup: String?) {
    val trip by session.trip.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val all = remember(trip) { trip.bookables() }
    // Only what is on the plan: a suggestion needing a booking shows here once it is on a day.
    val items = remember(all) { all.filterNot { it.unplanned } }
    var booked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var group by rememberSaveable { mutableStateOf(focusGroup?.let { g -> Bookable.Group.entries.firstOrNull { it.name == g } }) }
    var onlyStay by rememberSaveable { mutableStateOf(focusStay) }
    var order by rememberSaveable { mutableStateOf(if (focusStay != null) Order.STAY else Order.DATE) }
    val groups = remember(items, order, booked, group, onlyStay) {
        val shown = items
            .filter { (booked == null || it.booked == booked) && (group == null || it.group == group) && (onlyStay == null || it.stayId == onlyStay) }
        when (order) {
            Order.STAY -> shown.groupBy { it.stayId }.toList()
                .sortedBy { (id, _) -> trip.stays.indexOfFirst { it.id == id }.let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .associate { (id, list) -> (trip.stay(id)?.let { "${it.name} · ${it.arriveDate.shortLabel()} – ${it.departDate.shortLabel()}" } ?: "No stay") to list }
            Order.DATE -> shown.groupBy { it.date?.let(::dayName) ?: "Not scheduled yet" }
            Order.PRIORITY -> priorityGroups(shown)
            Order.TYPE -> shown.sortedBy { it.kind }.groupBy { typeName(it) }
        }
    }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<Bookable?>(null) }
    val listState = rememberLazyListState()
    fun undoable(message: String) = scope.launch { snackbar.offerUndo(session, message) }
    fun markNotBooked(b: Bookable) {
        if (session.edit("Mark not booked") { Edits.markNotBooked(it, b) }) undoable("“${b.title}” marked not booked")
    }

    LaunchedEffect(Unit) {
        // Opened from a day: start at that day's bookings (one summary item, then a header and rows per day).
        if (focusDate == null) return@LaunchedEffect
        var index = 2
        for ((title, list) in groups) {
            if (title == dayName(focusDate)) { listState.scrollToItem(index); break }
            index += 1 + list.size
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookings") }, navigationIcon = { BackButton(navigator) }, actions = { SaveIndicator(saveState, session::retrySave) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item("summary") { Summary(items) }
            item("order") {
                var sorting by remember { mutableStateOf(false) }
                // One line: the sort as a menu, then the filters in a row that scrolls sideways.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { sorting = true }) {
                            Text(order.label)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change order")
                        }
                        DropdownMenu(sorting, onDismissRequest = { sorting = false }) {
                            Order.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.label) }, onClick = { order = o; sorting = false },
                                    leadingIcon = { if (order == o) Icon(Icons.Default.Check, contentDescription = null) },
                                )
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onlyStay?.let { id ->
                            InputChip(true, onClick = { onlyStay = null }, label = { Text(trip.stay(id)?.name ?: id) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Show every stay", Modifier.size(18.dp)) })
                        }
                        // Glyphs, like the trip page's row; each says its name to TalkBack.
                        Bookable.Group.entries.forEach { g ->
                            FilterChip(group == g, onClick = { group = if (group == g) null else g }, label = { Text(g.glyph()) },
                                modifier = Modifier.semantics { contentDescription = g.label })
                        }
                        TriStateFilter(booked, { when (it) { null -> "Showing booked and not booked"; true -> "Showing booked only"; false -> "Showing not booked only" } }) { booked = it }
                    }
                }
            }
            groups.forEach { (title, list) ->
                item("h-$title") { SectionTitle(title, Modifier.padding(top = 12.dp)) }
                items(list, key = { it.key }) { b ->
                    BookableRow(
                        trip, b, b.key in expanded,
                        onToggle = { expanded = if (b.key in expanded) expanded - b.key else expanded + b.key },
                        onDetails = { open = b },
                        onLink = { url -> if (!openUrl(context, url)) scope.launch { snackbar.showSnackbar("No app can open $url") } },
                    )
                }
            }
            if (items.isEmpty()) item("empty") {
                Text("Nothing on this trip needs booking.", style = MaterialTheme.typography.bodyMedium)
            } else if (groups.isEmpty()) item("empty") {
                Text(if (booked == false) "Everything here is booked." else "Nothing matches these filters.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    open?.let { b ->
        val close = { open = null }
        when (b.kind) {
            Bookable.Kind.LODGING, Bookable.Kind.TRANSFER -> ReservationDialog(trip, b, close,
                onNotBooked = if (b.booked) ({ close(); markNotBooked(b) }) else null,
            ) { d ->
                close()
                if (session.edit("Mark booked") { Edits.markBooked(it, b, d) }) undoable("Booked “${b.title}”")
            }
            Bookable.Kind.ACTIVITY, Bookable.Kind.BOOKING -> {
                val linked = (if (b.kind == Bookable.Kind.ACTIVITY) b.id else b.record?.activityId)?.let { trip.activity(it) }
                val dates = (linked?.let { trip.stay(it.stayId) }?.let { trip.datesOf(it) } ?: trip.dates()).map { it.toString() }
                BookingDialog(
                    dates, b.date ?: dates.firstOrNull() ?: trip.startDate, b.record,
                    suggestions = trip.activities.filter { linked == null || it.stayId == linked.stayId }, initialLinked = linked, travelers = trip.travelers ?: 1,
                    onDismiss = close,
                    onDelete = b.record?.takeIf { it.origin == "user" }?.let { c -> {
                        close()
                        if (session.edit("Delete booking") { Edits.deleteBooking(it, c.id) }) undoable("Deleted “${c.title}”")
                    } },
                ) { input ->
                    close()
                    if (saveBooking(session, b.record, input)) undoable("Booked “${input.title}” on ${dayName(input.date)}")
                }
            }
        }
    }
}

/** One dense line and a bar: how many are booked, the money, and what is urgent. */
@Composable
private fun Summary(items: List<Bookable>) {
    val booked = items.count { it.booked }
    val open = items.filterNot { it.booked }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (listOf("$booked/${items.size} booked") + items.totals().map { "${money(it.booked, it.currency)} of ${it.estimate}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false),
            )
            listOf(1, 2).forEach { p ->
                open.count { it.priority == p }.takeIf { it > 0 }?.let { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$n priority $p open" }) {
                        PriorityTag(p); Text("$n", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (items.isNotEmpty()) LinearProgressIndicator(progress = { booked.toFloat() / items.size }, modifier = Modifier.fillMaxWidth())
    }
}

private enum class Order(val label: String) { DATE("By date"), STAY("By stay"), PRIORITY("By priority"), TYPE("By type") }

/** The group for "By type": lodging, then each way of travelling, then each kind of activity. */
private fun typeName(b: Bookable): String = when (b.kind) {
    Bookable.Kind.LODGING -> "🏨 Lodging"
    Bookable.Kind.TRANSFER -> "${modeEmoji(b.glyph)} ${modeLabel(b.glyph)}"
    else -> b.glyph?.let { "$it ${TAGS[it] ?: "Other"}" } ?: "🎫 Other bookings"
}

/** Groups for "By priority": what to book now first, then soon, then what can wait, unrated and booked. */
private fun priorityGroups(items: List<Bookable>): Map<String, List<Bookable>> {
    val open = items.filterNot { it.booked }
    return linkedMapOf(
        "P1 · Book now" to open.filter { it.priority == 1 },
        "P2 · Book a week or more ahead" to open.filter { it.priority == 2 },
        "P3 · Can be booked last minute" to open.filter { it.priority == 3 },
        "No priority given" to open.filter { it.priority !in 1..3 },
        "Booked" to items.filter { it.booked },
    ).filterValues { it.isNotEmpty() }
}

/** A P1 (error red), P2 (amber) or P3 (quiet) tag, in scheme colours so dark mode follows. */
@Composable
fun PriorityTag(priority: Int) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (priority) {
        1 -> c.error to c.onError
        2 -> c.tertiary to c.onTertiary
        3 -> c.secondaryContainer to c.onSecondaryContainer
        else -> return
    }
    Text(
        "P$priority", style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics { contentDescription = "Priority $priority" },
    )
}

@Composable
private fun BookableRow(
    trip: Trip, b: Bookable, expanded: Boolean,
    onToggle: () -> Unit, onDetails: () -> Unit, onLink: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Hide how to book" else "Show how to book", onClick = onToggle).padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.glyph(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!b.booked) b.priority?.let { PriorityTag(it) }
                    Text(marked(b.title, null, false, b.booked), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                detailLine(trip, b).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            b.price?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp)) }
        }
        if (expanded && b.booked) BookingDetails(trip, b, onLink, onDetails)
        else if (expanded) Column(Modifier.padding(start = 36.dp, end = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.how?.let { Text(rich(it), style = MaterialTheme.typography.bodyMedium) }
            b.price?.note?.let { Text("Price: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                b.url?.let { url ->
                    Button(onClick = { onLink(url) }) {
                        Text("Book")
                        Icon(OpenInNew, null, Modifier.padding(start = 6.dp).size(18.dp))
                    }
                }
                FilledTonalIconButton(onClick = { onLink(searchUrl(trip, b)) }) { Icon(Icons.Default.Search, "Search the web for ${b.title}") }
                FilledTonalIconButton(onClick = onDetails) { Icon(Icons.Default.Edit, "I booked it: enter the details") }
            }
        }
    }
}

/** What was recorded when it was booked, in place of the buttons; tap it to change the details. */
@Composable
private fun BookingDetails(trip: Trip, b: Bookable, onLink: (String) -> Unit, onEdit: () -> Unit) {
    val notes = b.record?.notes ?: trip.stay(b.stayId)?.lodging?.notes?.takeIf { b.kind == Bookable.Kind.LODGING }
    Card(
        onClick = onEdit, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 4.dp, bottom = 6.dp).semantics { onClick("Edit booking details") { onEdit(); true } },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Booking details", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            listOfNotNull(
                b.ref?.let { "Confirmation" to it },
                b.time?.let { "Time" to it },
                b.price?.let { "Paid" to it.label() },
                notes?.let { "Notes" to it },
            ).forEach { (k, v) ->
                Row { Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp)); Text(v, style = MaterialTheme.typography.bodyMedium) }
            }
            b.url?.let { url ->
                Text("Open booking ↗", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClickLabel = "Open the booking page") { onLink(url) }.padding(vertical = 4.dp))
            }
        }
    }
}

private fun searchUrl(trip: Trip, b: Bookable): String {
    val place = trip.stay(b.stayId)?.name.orEmpty()
    val q = when (b.kind) {
        Bookable.Kind.LODGING -> "${b.title} $place"
        Bookable.Kind.TRANSFER -> "${b.glyph} ${b.title} ${b.date.orEmpty()}"
        else -> "${b.title} $place booking"
    }
    return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q.trim(), "UTF-8")
}

/** What the booking is for: "For Sat 7 – Fri 13 Nov · 6 nights", "For Sun 8 Nov, 14:00 · Puerto Iguazú". */
private fun detailLine(trip: Trip, b: Bookable): String = listOfNotNull(
    when (b.kind) {
        Bookable.Kind.LODGING -> trip.stay(b.stayId)?.let { s -> "For ${dayName(s.arrive)} – ${dayName(s.depart)} · ${s.nights} night${if (s.nights == 1) "" else "s"}" }
        else -> b.date?.let { "For ${dayName(it)}" + (b.time?.let { t -> ", $t" } ?: "") } ?: "Day not chosen yet"
    },
    if (b.kind == Bookable.Kind.LODGING || b.kind == Bookable.Kind.TRANSFER) null else trip.stay(b.stayId)?.name,
    b.rate?.rateLabel(),
    b.ref?.let { "Ref $it" },
).joinToString(" · ")

/** Records a lodging or transfer booking: the details the confirmation gives, and what it cost. */
@Composable
private fun ReservationDialog(trip: Trip, b: Bookable, onDismiss: () -> Unit, onNotBooked: (() -> Unit)?, onSave: (Edits.Booked) -> Unit) {
    val lodging = b.kind == Bookable.Kind.LODGING
    val stay = trip.stay(b.stayId)
    var name by remember { mutableStateOf(stay?.lodging?.name.orEmpty()) }
    var start by remember { mutableStateOf(b.time.orEmpty()) }
    var end by remember { mutableStateOf(trip.transfers.firstOrNull { it.id == b.id }?.arrive.orEmpty()) }
    var ref by remember { mutableStateOf(b.ref.orEmpty()) }
    var url by remember { mutableStateOf(b.url.orEmpty()) }
    var notes by remember { mutableStateOf(stay?.lodging?.notes.orEmpty()) }
    val price = rememberPriceInput(b.price)
    val s = normalizeTime(start)
    val e = normalizeTime(end)
    val ok = (start.isBlank() || s != null) && (end.isBlank() || e != null) && price.ok

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${b.glyph()} ${b.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (b.booked) "Booked. Change the details, or mark it not booked." else "Saving marks it booked.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val number = KeyboardOptions(keyboardType = KeyboardType.Number)
                if (lodging) {
                    OutlinedTextField(name, { name = it }, label = { Text("Where you're staying") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it }, label = { Text("Check-in time (optional)") }, singleLine = true, keyboardOptions = number,
                        isError = start.isNotBlank() && s == null, modifier = Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Departs") }, singleLine = true, keyboardOptions = number,
                        isError = start.isNotBlank() && s == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Arrives") }, singleLine = true, keyboardOptions = number,
                        isError = end.isNotBlank() && e == null, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(ref, { ref = it }, label = { Text("Confirmation number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PriceField(price, if (b.booked) "Price paid (optional)" else "Price paid (estimate shown)")
                OutlinedTextField(url, { url = it }, label = { Text("Link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (lodging) OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                fun clean(v: String) = v.trim().ifEmpty { null }
                onSave(Edits.Booked(clean(name), clean(ref), clean(url), price.value, clean(notes), s, e))
            }) { Text(if (b.booked) "Save" else "Mark booked") }
        },
        dismissButton = {
            Row {
                onNotBooked?.let { TextButton(onClick = it) { Text("Not booked") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Material's "open in new" arrow; the core icon set doesn't include it. */
private val OpenInNew: ImageVector = ImageVector.Builder("OpenInNew", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes("M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"),
    fill = SolidColor(Color.Black),
).build()
