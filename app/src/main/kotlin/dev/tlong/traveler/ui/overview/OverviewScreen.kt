package dev.tlong.traveler.ui.overview

import dev.tlong.traveler.ui.common.EarlierRow
import dev.tlong.traveler.domain.departDate
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import dev.tlong.traveler.domain.Bookable
import dev.tlong.traveler.domain.dueToBook
import dev.tlong.traveler.ui.bookings.BookingsList
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.tlong.traveler.domain.destinations
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.bookables
import dev.tlong.traveler.domain.totals
import dev.tlong.traveler.ui.bookings.glyph
import dev.tlong.traveler.ui.bookings.tallyLabel
import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.TripPhase
import dev.tlong.traveler.domain.tallyByStay
import dev.tlong.traveler.domain.arriveDate
import dev.tlong.traveler.domain.dateRangeLabel
import dev.tlong.traveler.domain.departDate
import dev.tlong.traveler.domain.end
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.phase
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.domain.start
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.stayFor
import dev.tlong.traveler.model.Link
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Transfer
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.activity.dayName
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.ClimateLabel
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.copyToClipboard
import dev.tlong.traveler.ui.common.modeEmoji
import dev.tlong.traveler.ui.common.modeLabel
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.ui.common.rememberCachedImage
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.common.shareTextFile
import dev.tlong.traveler.ui.common.writeToUri
import dev.tlong.traveler.ui.trips.RenameDialog
import dev.tlong.traveler.ui.trips.TRIP_MIME_TYPES
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(tripId: String, navigator: Navigator, initialTab: Int = 0) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    Overview(session, navigator, initialTab)
}

/** The empty frame shown for the moment a trip takes to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScaffold(navigator: Navigator) {
    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(navigator) }) }) { Box(Modifier.padding(it)) }
}

@Composable
fun BackButton(navigator: Navigator) {
    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Overview(session: TripSession, navigator: Navigator, initialTab: Int) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val trip by session.trip.collectAsStateWithLifecycle()
    val base by session.base.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val checklist by session.checklist.collectAsStateWithLifecycle()
    var editingLink by remember { mutableStateOf<LinkTarget?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    var showPastStays by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    // A stay card's 🧳 tally opens the Bookings tab filtered to that stay.
    var focusStay by rememberSaveable { mutableStateOf<String?>(null) }
    val due = remember(trip) { trip.dueToBook().any { it.group == Bookable.Group.LOGISTICS } }
    val today = LocalDate.now()
    val changes = remember(trip, base) { session.localChangeCount() }

    val openRevision = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { container.openUri(uri) }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = writeToUri(context, uri, Export.text(base, trip))
            if (ok) session.markExported()
            snackbar.showSnackbar(if (ok) "Trip file saved" else "Could not write the file")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    SaveIndicator(saveState, session::retrySave)
                    Box {
                        IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.Share, "Export trip") }
                        DropdownMenu(exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(text = { Text("Share trip file") }, onClick = {
                                exportMenu = false
                                if (shareTextFile(context, Export.fileName(base, trip), Export.text(base, trip), "Send trip file")) {
                                    scope.launch { session.markExported() }
                                }
                            })
                            DropdownMenuItem(text = { Text("Save trip file…") }, onClick = { exportMenu = false; saveAs.launch(Export.fileName(base, trip)) })
                            DropdownMenuItem(text = { Text("Copy trip JSON") }, onClick = {
                                exportMenu = false
                                copyToClipboard(context, trip.title, Export.text(base, trip))
                                scope.launch { session.markExported(); snackbar.showSnackbar("Trip JSON copied — paste it into your planning chat") }
                            })
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Import a revision…") }, onClick = { menu = false; openRevision.launch(TRIP_MIME_TYPES) })
                            DropdownMenuItem(text = { Text("History and undo import") }, onClick = { menu = false; navigator.history(trip.id) })
                            DropdownMenuItem(text = { Text("Trip links (${trip.links.size})…") }, onClick = { menu = false; showLinks = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Stays") })
            // Red while lodging or travel is due to book this week; events have their own page.
            Tab(tab == 1, onClick = { tab = 1; focusStay = null }, text = { Text("Bookings", color = if (due) MaterialTheme.colorScheme.error else Color.Unspecified) })
            Tab(tab == 2, onClick = { tab = 2 }, text = { Text("Trip info") })
        }
        if (tab == 1) key(focusStay) { BookingsList(session, snackbar, group = Bookable.Group.LOGISTICS, focusStay = focusStay) }
        else LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (tab == 2) {
            item("dates") {
                Text(
                    "${dateRangeLabel(trip.start, trip.end)} · ${trip.destinations.size} stays · rev ${base.revision}" +
                        if (changes > 0) " + $changes edit${if (changes == 1) "" else "s"}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item("shortcuts") {
                TripShortcuts(
                    trip, checklist,
                    onEvents = { navigator.events(trip.id) },
                    onChecklist = { navigator.checklist(trip.id, it) },
                    onNotes = { navigator.notes(trip.id) },
                )
            }
            item("calendar") { TripInfoCard(trip, today) { navigator.day(trip.id, it.toString()) } }
          } else {
            if (trip.phase(today) == TripPhase.ACTIVE) item("today") {
                val stay = trip.stayFor(today)
                Card(onClick = { navigator.day(trip.id, today.toString()) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Today · ${today.label()}", style = MaterialTheme.typography.titleMedium)
                            stay?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                        }
                        Text("Open plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Every stay, so the flights out and back still show, but no card for the home airport.
            val shown = trip.destinations.map { it.id }.toSet()
            val tallies = trip.bookables().tallyByStay()
            // During the trip, finished stays and the travel between them fold into one row.
            val active = trip.phase(today) == TripPhase.ACTIVE
            val finished = if (active) trip.stays.count { it.id in shown && it.departDate < today } else 0
            if (finished > 0) item("past-stays") { EarlierRow(finished, "stay", showPastStays) { showPastStays = !showPastStays } }
            val folded = active && !showPastStays
            trip.stays.forEachIndexed { i, stay ->
                if (folded && stay.departDate < today) return@forEachIndexed
                val next = trip.stays.getOrNull(i + 1)
                val legs = next?.let { n -> trip.transfers.filter { it.from == stay.id && it.to == n.id } }.orEmpty()
                // Travel rides on the card it leaves; only the flight out from home, which has no card, is a line.
                val between = stay.id in shown
                if (stay.id in shown) {
                    item("stay-${stay.id}") {
                        StayCard(
                            stay, if (between) legs else emptyList(), tallies[stay.id].orEmpty(), onBookings = { g -> if (g == Bookable.Group.EVENTS) navigator.events(trip.id, stay.id) else { focusStay = stay.id; tab = 1 } },
                        ) { navigator.stay(trip.id, stay.id) }
                    }
                }
                if (!between) legs.forEach { t ->
                        item("t-${t.id}") {
                            Text(
                                "${modeEmoji(t.shownMode)}  ${modeLabel(t.shownMode)} · ${dayName(t.date)}" +
                                    listOfNotNull(t.depart, t.arrive).joinToString("–").let { if (it.isEmpty()) "" else " · $it" },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                }
            }
          }
        }
        }
    }

    if (renaming) RenameDialog(trip.title, onDismiss = { renaming = false }) { name ->
        renaming = false
        session.edit("Rename trip") { Edits.rename(it, name) }
    }
    // Trip-wide documents (a planning sheet, a map, the planning chat); a place's link lives on its activity.
    if (showLinks) AlertDialog(
        onDismissRequest = { showLinks = false },
        title = { Text("Trip links") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trip.links.isEmpty()) Text("No links yet", style = MaterialTheme.typography.bodyMedium)
                trip.links.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinkChip(l, Modifier.weight(1f, fill = false)) { if (!openUrl(context, l.url)) scope.launch { snackbar.showSnackbar("Cannot open ${l.url}") } }
                        IconButton(onClick = { editingLink = LinkTarget(l) }) { Icon(Icons.Default.Edit, "Edit ${l.label ?: "link"}") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showLinks = false }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { editingLink = LinkTarget(null) }) { Text("+ Add link") } },
    )
    editingLink?.let { target ->
        LinkDialog(target.link, onDismiss = { editingLink = null }) { link ->
            editingLink = null
            session.edit(when { target.link == null -> "Add link"; link == null -> "Delete link"; else -> "Edit link" }) { Edits.setLink(it, target.link, link) }
        }
    }
}

/** The link being edited on the overview; a null [link] means a new one. */
private class LinkTarget(val link: Link?)

/** Guesses a link's kind from its address, for the icon. */
fun linkKind(url: String): String = when {
    "docs.google.com/spreadsheets" in url || "sheets.google" in url -> "spreadsheet"
    "google.com/maps" in url || "maps.app.goo.gl" in url || "goo.gl/maps" in url -> "map"
    "docs.google.com/document" in url -> "doc"
    "chatgpt.com" in url || "chat.openai.com" in url || "claude.ai" in url -> "chat"
    else -> "other"
}

@Composable
private fun LinkDialog(current: Link?, onDismiss: () -> Unit, onSave: (Link?) -> Unit) {
    var label by remember { mutableStateOf(current?.label.orEmpty()) }
    var url by remember { mutableStateOf(current?.url.orEmpty()) }
    val clean = url.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Add link" else "Edit link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Link") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it }, singleLine = true, label = { Text("Name (optional)") },
                    placeholder = { Text("Planning sheet") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = clean.isNotEmpty(), onClick = {
                val kind = if (current != null && current.url == clean) current.kind else linkKind(clean)
                onSave(Link(label.trim().ifEmpty { null }, clean, kind))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TripInfoCard(trip: Trip, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val image by rememberCachedImage(trip.tripMap?.imageUrl)
    val tint = rememberStayTints(trip)
    Card {
        Column {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StayCalendar(trip, tint, today, onDay = onDay)
                StayKey(trip, tint)
                image?.let { bmp ->
                    Image(bmp, "Supplied overview image of the trip", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    trip.tripMap?.attribution?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun StayCard(
    stay: Stay, leaving: List<Transfer>, tally: Map<Bookable.Group, Pair<Int, Int>>,
    onBookings: (Bookable.Group) -> Unit, onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stay.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Booked out of planned, per group; each opens Bookings or Events filtered to this stay.
                    Bookable.Group.entries.forEach { g -> tally[g]?.let { (booked, planned) -> BookingTally(g, booked, planned) { onBookings(g) } } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stay.arriveDate.shortLabel()} – ${stay.departDate.shortLabel()}" +
                            leaving.joinToString("") { t -> " ${modeEmoji(t.shownMode)}" + (t.depart?.let { " $it" } ?: "") },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    stay.climate?.let { ClimateLabel(it, Modifier.padding(start = 8.dp)) }
                }
            }
        }
    }
}

/** A glyph and "1/3". Compose widens a small clickable's touch area to 48dp, so it stays easy to tap without padding the row. */
@Composable
private fun BookingTally(group: Bookable.Group, booked: Int, planned: Int, onClick: () -> Unit) {
    val done = booked == planned
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${group.label}: $booked of $planned booked" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(group.glyph(), style = MaterialTheme.typography.labelMedium)
        Text(
            if (done) "✓" else "$booked/$planned", style = MaterialTheme.typography.labelMedium,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkChip(link: Link, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = when (link.kind ?: linkKind(link.url)) { "spreadsheet" -> "📊"; "chat" -> "💬"; "map" -> "🗺️"; "doc" -> "📄"; "booking" -> "🎫"; else -> "🔗" }
    AssistChip(onClick = onClick, modifier = modifier, label = { Text("$icon ${link.label ?: link.url}", maxLines = 1, overflow = TextOverflow.Ellipsis) })
}

@Composable
fun UrlDialog(title: String, hint: String, current: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var url by remember { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Link") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url.trim().ifEmpty { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
