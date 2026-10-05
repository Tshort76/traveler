package dev.tlong.traveler.ui.overview

import androidx.compose.material3.HorizontalDivider
import dev.tlong.traveler.domain.isBooked
import dev.tlong.traveler.ui.common.byRecommendation
import dev.tlong.traveler.ui.common.marked
import androidx.compose.foundation.layout.width
import dev.tlong.traveler.ui.bookings.PriorityTag
import dev.tlong.traveler.domain.Bookable
import dev.tlong.traveler.domain.bookAhead
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.bookables
import dev.tlong.traveler.domain.totals
import dev.tlong.traveler.ui.bookings.glyph
import dev.tlong.traveler.ui.bookings.tallyLabel
import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.TripPhase
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
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.activity.dayName
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.copyToClipboard
import dev.tlong.traveler.ui.common.modeEmoji
import dev.tlong.traveler.ui.common.modeLabel
import dev.tlong.traveler.ui.common.openUrl
import dev.tlong.traveler.ui.common.rememberCachedImage
import dev.tlong.traveler.ui.common.rememberOnline
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.common.shareTextFile
import dev.tlong.traveler.ui.common.writeToUri
import dev.tlong.traveler.ui.map.MapPoint
import dev.tlong.traveler.ui.map.MapSegment
import dev.tlong.traveler.ui.map.OfflineMapsDialog
import dev.tlong.traveler.ui.map.TripMap
import dev.tlong.traveler.ui.map.distanceKm
import dev.tlong.traveler.ui.trips.RenameDialog
import dev.tlong.traveler.ui.trips.TRIP_MIME_TYPES
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(tripId: String, navigator: Navigator) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    Overview(session, navigator)
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

/** Points and transfer segments for the overview map, numbered in visit order; home is left off. */
fun Trip.mapModel(): Pair<List<MapPoint>, List<MapSegment>> {
    val stays = destinations
    val withCoords = stays.withIndex().filter { it.value.place?.hasCoordinates == true }
    val points = withCoords.map { (i, s) -> MapPoint(s.place!!.lat!!, s.place.lng!!, "${i + 1}", s.name) }
    val index = withCoords.mapIndexed { pi, iv -> iv.value.id to pi }.toMap()
    val segments = stays.zipWithNext().mapNotNull { (a, b) ->
        val from = index[a.id] ?: return@mapNotNull null
        val to = index[b.id] ?: return@mapNotNull null
        MapSegment(from, to)
    }
    return points to segments
}

fun mapDescription(trip: Trip, points: List<MapPoint>): String {
    if (points.isEmpty()) return "No destinations have coordinates, so there is no map."
    val legs = points.zipWithNext().joinToString("; ") { (a, b) -> "${a.name} to ${b.name}, about ${distanceKm(a, b).roundToInt()} km" }
    return "Map of ${trip.destinations.size} destinations in visit order: " + points.joinToString(", ") { "${it.badge} ${it.name}" } +
        if (legs.isNotEmpty()) ". Legs: $legs." else "."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Overview(session: TripSession, navigator: Navigator) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val trip by session.trip.collectAsStateWithLifecycle()
    val base by session.base.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val exportedHash by session.exportedHash.collectAsStateWithLifecycle()
    val online by rememberOnline()
    var menu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf<LinkTarget?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    var offlineMaps by remember { mutableStateOf(false) }
    var fullMap by remember { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showBookAhead by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val (points, segments) = remember(trip.stays) { trip.mapModel() }
    val changes = remember(trip, base) { session.localChangeCount() }
    val unexported = exportedHash != Export.hash(trip)

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
                            DropdownMenuItem(enabled = false, onClick = {}, text = {
                                Column {
                                    Text("Revision ${base.revision}" + if (changes > 0) " + $changes change${if (changes == 1) "" else "s"} of yours" else ", unchanged")
                                    if (unexported && changes > 0) Text("Not exported since your last change", style = MaterialTheme.typography.bodySmall)
                                }
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Export…") }, onClick = { menu = false; exportMenu = true })
                            DropdownMenuItem(text = { Text("Import a revision…") }, onClick = { menu = false; openRevision.launch(TRIP_MIME_TYPES) })
                            DropdownMenuItem(text = { Text("History and undo import") }, onClick = { menu = false; navigator.history(trip.id) })
                            DropdownMenuItem(text = { Text("Trip links (${trip.links.size})…") }, onClick = { menu = false; showLinks = true })
                            DropdownMenuItem(text = { Text("Offline maps…") }, onClick = { menu = false; offlineMaps = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("dates") {
                Text(
                    "${dateRangeLabel(trip.start, trip.end)} · ${trip.destinations.size} stays",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item("bookings") { BookingsCard(trip) { navigator.bookings(trip.id) } }
            val bookAhead = trip.bookAhead()
            if (bookAhead.isNotEmpty()) item("book-ahead") {
                BookAhead(bookAhead, expanded = showBookAhead, onToggle = { showBookAhead = !showBookAhead }) { navigator.bookings(trip.id) }
            }
            item("map") { MapCard(trip, points, segments, online, onExpand = { fullMap = true }) }
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
            item("stays-title") { SectionTitle("Stays", Modifier.padding(top = 6.dp)) }
            // Every stay, so the flights out and back still show, but no card for the home airport.
            val numbers = trip.destinations.withIndex().associate { (i, s) -> s.id to i + 1 }
            trip.stays.forEachIndexed { i, stay ->
                numbers[stay.id]?.let { n -> item("stay-${stay.id}") { StayCard(n, stay, trip.activities.filter { it.stayId == stay.id && it.stars == 3 }.sortedWith(byRecommendation).map { it.marked(trip.isBooked(it)) }) { navigator.stay(trip.id, stay.id) } } }
                trip.stays.getOrNull(i + 1)?.let { next ->
                    trip.transfers.filter { it.from == stay.id && it.to == next.id }.forEach { t ->
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
            val notes = trip.warnings + trip.notes
            if (notes.isNotEmpty()) item("notes") {
                Column {
                    TextButton(onClick = { showNotes = !showNotes }, contentPadding = PaddingValues(0.dp)) {
                        Text("Notes from the plan (${notes.size})", style = MaterialTheme.typography.titleSmall)
                        Icon(if (showNotes) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                    if (showNotes) notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }

    if (fullMap) {
        Dialog(onDismissRequest = { fullMap = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                TripMap(trip.id, points, segments, online, mapDescription(trip, points), Modifier.fillMaxSize(), interactive = true)
                Button(onClick = { fullMap = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("Close map") }
            }
        }
    }
    if (offlineMaps) OfflineMapsDialog(trip, online) { offlineMaps = false }
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
                if (trip.links.isEmpty()) Text("A planning sheet, a Google map or the planning chat. A tour or restaurant link belongs on its activity.", style = MaterialTheme.typography.bodyMedium)
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
                Text("Exports keep the link, so your assistant sees it too.", style = MaterialTheme.typography.bodySmall)
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
private fun MapCard(
    trip: Trip, points: List<MapPoint>, segments: List<MapSegment>, online: Boolean,
    onExpand: () -> Unit,
) {
    val image by rememberCachedImage(trip.tripMap?.imageUrl)
    Card {
        Column {
            if (points.isNotEmpty()) {
                TripMap(
                    trip.id, points, segments, online, mapDescription(trip, points),
                    Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).clickable(onClickLabel = "Open the map full screen", onClick = onExpand),
                )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    trip.destinations.mapIndexed { i, s -> "${i + 1} ${s.name}" }.joinToString("  →  "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                image?.let { bmp ->
                    Image(bmp, "Supplied overview image of the trip", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    trip.tripMap?.attribution?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun StayCard(number: Int, stay: Stay, standouts: List<String>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("$number", container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stay.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${stay.arriveDate.shortLabel()} – ${stay.departDate.shortLabel()} · ${stay.nights} night${if (stay.nights == 1) "" else "s"}" +
                        (stay.region?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Bullets, not prose: the must-dos, or the plan's priorities when nothing has three stars.
                (standouts.map { "• $it" }.ifEmpty { stay.priorities.map { "• $it" } }).take(3).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LinkChip(link: Link, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = when (link.kind ?: linkKind(link.url)) { "spreadsheet" -> "📊"; "chat" -> "💬"; "map" -> "🗺️"; "doc" -> "📄"; "booking" -> "🎫"; else -> "🔗" }
    AssistChip(onClick = onClick, modifier = modifier, label = { Text("$icon ${link.label ?: link.url}", maxLines = 1, overflow = TextOverflow.Ellipsis) })
}

@Composable
private fun BookingsCard(trip: Trip, onOpen: () -> Unit) {
    val items = remember(trip) { trip.bookables().filterNot { it.unplanned } }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bookings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(
                    items.tallyLabel() ?: "Nothing to book",
                    items.totals().joinToString(" + ") { "est. " + it.estimate }.ifEmpty { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            items.firstOrNull { !it.booked }?.let { Text("Next to book: ${it.glyph()} ${it.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The P1s still open, collapsed to a count: things that sell out or jump in price if left. */
@Composable
private fun BookAhead(items: List<Bookable>, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PriorityTag(1)
                Text("Advance bookings required (${items.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            if (expanded) items.forEach { b ->
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open bookings", onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.glyph(), modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.bodyMedium)
                        Text(b.date?.let { "For ${dayName(it)}" } ?: "Not planned on a day yet", style = MaterialTheme.typography.bodySmall)
                    }
                    b.price?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
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
