package dev.tlong.traveler.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripStore
import dev.tlong.traveler.data.TripSummary
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.copyToClipboard
import dev.tlong.traveler.ui.common.shareTextFile
import dev.tlong.traveler.ui.common.writeToUri
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.trips.TRIP_MIME_TYPES
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: Navigator) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all by container.store.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val deleted = all.filter { it.deletedAt != null }
    var purging by remember { mutableStateOf<TripSummary?>(null) }
    val backupName = "traveler-backup-${LocalDate.now()}.json"

    suspend fun backupText() = container.store.backupText(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = writeToUri(context, uri, backupText())
            snackbar.showSnackbar(if (ok) "Backup saved" else "Could not write the backup")
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { container.openUri(uri) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(navigator) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Backup")
                    Text(
                        "One file with every trip, your edits and the version each was last imported from. Keep it somewhere outside this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveBackup.launch(backupName) }) { Text("Save backup…") }
                        OutlinedButton(onClick = { scope.launch { shareTextFile(context, backupName, backupText(), "Send backup") } }) { Text("Share") }
                    }
                    OutlinedButton(onClick = { openBackup.launch(TRIP_MIME_TYPES) }) { Text("Restore from a backup…") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Your assistant")
                    Text(
                        "Give these instructions to any assistant (Claude, ChatGPT, Gemini…) so it writes trip files this app can load, and revises them without losing your changes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    fun copyPrompt(asset: String, label: String) {
                        val text = context.assets.open(asset).bufferedReader().use { it.readText() }.substringAfter("-->").trim()
                        copyToClipboard(context, label, text)
                        scope.launch { snackbar.showSnackbar("$label copied") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { copyPrompt("itinerary-instructions.md", "Trip file instructions") }) { Text("Copy instructions") }
                        OutlinedButton(onClick = {
                            val text = context.assets.open("demo.trip.json").bufferedReader().use { it.readText() }
                            shareTextFile(context, "example.trip.json", text, "Send example trip")
                        }) { Text("Share an example") }
                    }
                    Text(
                        "To draft the route first (the stays, their dates and the travel between them) as a spreadsheet for Google Sheets, with a map file for Google My Maps:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { copyPrompt("workbook-instructions.md", "Spreadsheet instructions") }) { Text("Copy spreadsheet instructions") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Demo trip")
                    Text(
                        "A short Buenos Aires and Patagonia trip that uses every feature: bookings in every state, two hotels in one city, work hours, done and skipped items, your own entries. To start it fresh, delete it, then load it again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        scope.launch { container.openText(context.assets.open("demo.trip.json").bufferedReader().use { it.readText() }, "demo trip") }
                    }) { Text("Load the demo trip") }
                }
            }
            item {
                SectionTitle("Recently deleted")
                Text(
                    if (deleted.isEmpty()) "Nothing here." else "Deleted trips stay here for ${TripStore.DELETE_AFTER_MS / 86_400_000} days.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(deleted, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { container.store.undelete(t.id) } }) { Text("Restore") }
                            TextButton(onClick = { purging = t }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 12.dp)) {
                    SectionTitle("Privacy")
                    Text(
                        "Trips and notes live only on this phone. Nothing is uploaded and no account is needed; a trip leaves the phone only when you export, share or back it up. On its own, the app goes online only to fetch map tiles for the area on screen (OpenFreeMap) and a trip's overview image, if the trip names one; no trip details are sent.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    purging?.let { t ->
        AlertDialog(
            onDismissRequest = { purging = null },
            title = { Text("Delete “${t.title}” forever?") },
            text = { Text("This removes the trip and its history from this phone. It cannot be undone; a backup or an exported file can still be imported.") },
            confirmButton = { TextButton(onClick = { purging = null; scope.launch { container.store.purge(t.id) } }) { Text("Delete forever") } },
            dismissButton = { TextButton(onClick = { purging = null }) { Text("Cancel") } },
        )
    }
}
