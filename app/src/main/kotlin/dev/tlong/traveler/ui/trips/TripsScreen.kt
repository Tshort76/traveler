package dev.tlong.traveler.ui.trips

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSummary
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.TripPhase
import dev.tlong.traveler.domain.dateRangeLabel
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalDate

val TRIP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")

private fun TripSummary.phase(today: LocalDate): TripPhase {
    val s = startDate.toDate() ?: return TripPhase.UPCOMING
    val e = endDate.toDate() ?: s
    return when {
        today < s -> TripPhase.UPCOMING
        today > e -> TripPhase.PAST
        else -> TripPhase.ACTIVE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(navigator: Navigator) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all by container.store.summaries.collectAsStateWithLifecycle(initialValue = null)
    val today = LocalDate.now()
    var importMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TripSummary?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { container.openUri(uri) }
    }
    val paste: () -> Unit = {
        val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) scope.launch { snackbar.showSnackbar("The clipboard is empty. Copy the trip JSON from your chat first.") }
        else scope.launch { container.openText(text, "pasted text") }
    }
    val loadExample: () -> Unit = {
        scope.launch {
            val text = context.assets.open("iguazu-short.trip.json").bufferedReader().use { it.readText() }
            container.openText(text, "example trip")
        }
    }

    fun delete(t: TripSummary) = scope.launch {
        container.store.moveToDeleted(t.id)
        val r = snackbar.showSnackbar("“${t.title}” moved to Recently deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) container.store.undelete(t.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traveler") },
                actions = { IconButton(onClick = navigator::settings) { Icon(Icons.Default.Settings, "Settings, backup and recently deleted") } },
            )
        },
        floatingActionButton = {
            if (all?.any { it.deletedAt == null } == true) Box {
                ExtendedFloatingActionButton(
                    onClick = { importMenu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Import trip") },
                    // The extended button's label does not reach accessibility services on its own.
                    modifier = Modifier.semantics { contentDescription = "Import trip" },
                )
                DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                    DropdownMenuItem(text = { Text("Open a trip file") }, onClick = { importMenu = false; openFile.launch(TRIP_MIME_TYPES) })
                    DropdownMenuItem(text = { Text("Paste trip JSON") }, onClick = { importMenu = false; paste() })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = all ?: return@Scaffold
        val live = list.filter { it.deletedAt == null }
        if (live.isEmpty()) {
            EmptyState(Modifier.padding(padding), onOpen = { openFile.launch(TRIP_MIME_TYPES) }, onPaste = paste, onExample = loadExample)
            return@Scaffold
        }
        val active = live.filter { !it.archived && it.phase(today) == TripPhase.ACTIVE }
        // Upcoming starts within 30 days; anything later is Planned. Both earliest first.
        val (upcoming, planned) = live.filter { !it.archived && it.phase(today) == TripPhase.UPCOMING }.sortedBy { it.startDate }
            .partition { t -> t.startDate.toDate()?.let { it <= today.plusDays(30) } ?: false }
        val past = live.filter { !it.archived && it.phase(today) == TripPhase.PAST }.sortedByDescending { it.startDate }
        val archived = live.filter { it.archived }
        // The trip under way goes on top, with its day's plan a tap away.
        val current = active.firstOrNull()

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current != null) {
                item(key = "current") {
                    CurrentTripCard(current,
                        onOpen = { navigator.overview(current.id) }, onToday = { navigator.day(current.id, today.toString()) }) {
                        TripMenu(current, onRename = { renaming = current },
                            onArchive = { scope.launch { container.store.setArchived(current.id, true) } }, onDelete = { delete(current) })
                    }
                }
            }
            fun section(title: String, trips: List<TripSummary>) {
                val rest = trips.filter { it.id != current?.id }
                if (rest.isEmpty()) return
                item(key = "h-$title") { SectionTitle(title, Modifier.padding(top = 12.dp)) }
                items(rest, key = { it.id }) { t ->
                    TripRow(t, today, onOpen = { navigator.overview(t.id) }, onRename = { renaming = t },
                        onArchive = { scope.launch { container.store.setArchived(t.id, !t.archived) } }, onDelete = { delete(t) })
                }
            }
            section("Under way", active)
            section("Upcoming", upcoming)
            section("Planned", planned)
            section("Past", past)
            if (archived.isNotEmpty()) {
                item(key = "archived-toggle") {
                    TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Archived (${archived.size})")
                        Icon(if (showArchived) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                }
                if (showArchived) items(archived, key = { it.id }) { t ->
                    TripRow(t, today, onOpen = { navigator.overview(t.id) }, onRename = { renaming = t },
                        onArchive = { scope.launch { container.store.setArchived(t.id, false) } }, onDelete = { delete(t) })
                }
            }
        }
    }

    renaming?.let { t ->
        RenameDialog(t.title, onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch { container.session(t.id)?.edit("Rename trip") { Edits.rename(it, name) } }
        }
    }
}

@Composable
private fun CurrentTripCard(t: TripSummary, onOpen: () -> Unit, onToday: () -> Unit, menu: @Composable () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Under way", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                menu()
            }
            Text(t.title, style = MaterialTheme.typography.headlineSmall)
            Text(range(t), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onToday, modifier = Modifier.padding(top = 4.dp)) { Text("Today's plan") }
        }
    }
}

/** "in 13 days" for a trip starting within the Upcoming window; null otherwise. */
private fun startsIn(t: TripSummary, today: LocalDate): String? {
    val days = t.startDate.toDate()?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) } ?: return null
    return when (days) {
        0L -> "starts today"
        1L -> "tomorrow"
        in 2..30 -> "in $days days"
        else -> null
    }
}

private fun range(t: TripSummary): String {
    val s = t.startDate.toDate()
    val e = t.endDate.toDate()
    return if (s != null && e != null) dateRangeLabel(s, e) else "${t.startDate} – ${t.endDate}"
}

@Composable
private fun TripRow(t: TripSummary, today: LocalDate, onOpen: () -> Unit, onRename: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(range(t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Pill("rev ${t.revision}")
                    startsIn(t, today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            TripMenu(t, onRename, onArchive, onDelete)
        }
    }
}

@Composable
private fun TripMenu(t: TripSummary, onRename: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More for ${t.title}" }) {
            Icon(Icons.Default.MoreVert, null)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(if (t.archived) "Unarchive" else "Archive") }, onClick = { menu = false; onArchive() })
            DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onOpen: () -> Unit, onPaste: () -> Unit, onExample: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No trips yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Plan the trip with your assistant and ask it for a Traveler trip file. Open the file here, share it to Traveler, or copy the JSON and paste it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open a trip file") }
        OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) { Text("Paste trip JSON") }
        TextButton(onClick = onExample) { Text("Try the example trip") }
        Text(
            "Settings has the instructions to give your assistant.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename trip") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
