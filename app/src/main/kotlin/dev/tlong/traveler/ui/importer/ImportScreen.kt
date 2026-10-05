package dev.tlong.traveler.ui.importer

import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import dev.tlong.traveler.ui.common.copyToClipboard
import dev.tlong.traveler.model.Stamp
import dev.tlong.traveler.data.FileCheck
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.PendingImport
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.domain.arriveDate
import dev.tlong.traveler.domain.dateRangeLabel
import dev.tlong.traveler.domain.destinations
import dev.tlong.traveler.domain.dates
import dev.tlong.traveler.domain.dayWarnings
import dev.tlong.traveler.domain.departDate
import dev.tlong.traveler.domain.end
import dev.tlong.traveler.domain.nights
import dev.tlong.traveler.domain.shortLabel
import dev.tlong.traveler.domain.start
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.Pill
import dev.tlong.traveler.ui.common.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(navigator: Navigator) {
    val container = LocalContainer.current
    val pending by container.pendingImport.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val close = { container.dismissImport(); navigator.back() }
    val p = pending

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (p) {
                            is PendingImport.Invalid -> "Can't import"
                            is PendingImport.NewTrip -> "Import trip"
                            is PendingImport.AlreadyImported -> "Already imported"
                            is PendingImport.Revision -> "Update trip"
                            is PendingImport.Backup -> "Restore backup"
                            null -> ""
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, "Cancel import") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (p) {
                null -> WithActions(actions = {}) { Text("Nothing to import.") }
                is PendingImport.Invalid -> WithActions(actions = { Button(onClick = close) { Text("Close") } }) { Invalid(p) }
                is PendingImport.NewTrip -> WithActions(
                    actions = {
                        Button(onClick = { scope.launch { navigator.overview(container.acceptNew(p.trip), replaceStack = true) } }) { Text("Import") }
                        OutlinedButton(onClick = close) { Text("Cancel") }
                    },
                ) {
                    TripPreview(p.trip, p.source)
                    AssistantCheck(p.check)
                    Warnings(p.warnings)
                }
                is PendingImport.AlreadyImported -> WithActions(
                    actions = {
                        if (p.deleted) {
                            Button(onClick = { scope.launch { container.undelete(p.trip.id); navigator.overview(p.trip.id, replaceStack = true) } }) { Text("Restore it") }
                        } else {
                            Button(onClick = { container.dismissImport(); navigator.overview(p.trip.id, replaceStack = true) }) { Text("Open the trip") }
                        }
                    },
                ) {
                    Text("“${p.trip.title}” revision ${p.trip.revision} is already on this phone with the same content. Nothing was added, so there is no duplicate.")
                    if (p.deleted) Text("It is in Recently deleted.", style = MaterialTheme.typography.bodyMedium)
                    AssistantCheck(p.check)
                }
                is PendingImport.Revision -> RevisionReview(p, onCancel = close) { decisions ->
                    scope.launch { navigator.overview(container.acceptRevision(p, decisions), replaceStack = true) }
                }
                is PendingImport.Backup -> BackupReview(p, onCancel = close) { chosen ->
                    scope.launch { container.restoreBackup(chosen); navigator.trips() }
                }
            }
        }
    }
}

/** Scrolling content with the decision buttons pinned below it, so Import is reachable on a long trip. */
@Composable
private fun WithActions(actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

@Composable
private fun Invalid(p: PendingImport.Invalid) {
    Text("“${p.source}” could not be imported. Nothing on this phone was changed.", style = MaterialTheme.typography.bodyLarge)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.errors.take(30).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            if (p.errors.size > 30) Text("…and ${p.errors.size - 30} more.")
        }
    }
    Text(
        "If an assistant wrote this file, send it these problems and ask for a corrected trip file.",
        style = MaterialTheme.typography.bodyMedium,
    )
    CopyFixRequest(p.errors)
    Warnings(p.warnings)
}

@Composable
private fun TripPreview(trip: Trip, source: String) {
    Text(trip.title, style = MaterialTheme.typography.headlineSmall)
    Text("${dateRangeLabel(trip.start, trip.end)} · ${trip.dates().size} days · revision ${trip.revision}", style = MaterialTheme.typography.bodyMedium)
    Text("From $source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    trip.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    SectionTitle("Destinations")
    trip.destinations.forEachIndexed { i, s ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${i + 1}")
            Text("${s.name} · ${s.arriveDate.shortLabel()} – ${s.departDate.shortLabel()} · ${s.nights} night${if (s.nights == 1) "" else "s"}")
        }
    }
    val planned = trip.days.sumOf { it.plan.size }
    Text(
        "${trip.activities.size} activities (${planned} placed on days) · ${trip.commitments.size} fixed commitments · ${trip.transfers.size} transfers",
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Whether the assistant ran the validator on this exact file (its stamp matches), and what the
 * strict checks found anyway. Nothing here blocks the import; it is for sending back.
 */
@Composable
private fun AssistantCheck(check: FileCheck) {
    when (check.stamp) {
        Stamp.Check.MATCHES -> Text("✓ Checked by the validator", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Stamp.Check.CHANGED -> Text("⚠ The validator's stamp doesn't match: the file changed after it was checked.", style = MaterialTheme.typography.bodyMedium)
        Stamp.Check.NONE -> Text("Not checked by the validator", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val problems = check.forAssistant
    if (problems.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${problems.size} problem${if (problems.size == 1) "" else "s"} for the assistant to fix — you can still import", style = MaterialTheme.typography.titleSmall)
            (if (open) problems else problems.take(4)).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (problems.size > 4) TextButton(onClick = { open = !open }) { Text(if (open) "Show fewer" else "Show all") }
            CopyFixRequest(problems)
        }
    }
}

/** Copies a message to paste back into the assistant's chat: what failed, and how to hand the file back. */
@Composable
private fun CopyFixRequest(problems: List<String>) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        copyToClipboard(context, "Fix request", fixRequest(problems))
        copied = true
    }) { Text(if (copied) "Copied — paste it into the chat" else "Copy fix request") }
}

internal fun fixRequest(problems: List<String>): String =
    "The trip file you sent does not pass validate_trip.py --complete. Fix these problems, run " +
        "`python3 validate_trip.py --stamp <file>` until it prints OK, and send the whole corrected file, " +
        "ending your reply with the validator's OK line.\n\n" + problems.joinToString("\n") { "- $it" }

@Composable
private fun Warnings(warnings: List<String>) {
    if (warnings.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${warnings.size} note${if (warnings.size == 1) "" else "s"} about this file — it can still be imported", style = MaterialTheme.typography.titleSmall)
            (if (open) warnings else warnings.take(3)).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (warnings.size > 3) {
                androidx.compose.material3.TextButton(onClick = { open = !open }) { Text(if (open) "Show fewer" else "Show all") }
            }
        }
    }
}

@Composable
private fun RevisionReview(p: PendingImport.Revision, onCancel: () -> Unit, onApply: (Merge.Decisions) -> Unit) {
    val plan = p.plan
    var takeTheirs by remember { mutableStateOf(emptySet<String>()) }
    var remove by remember { mutableStateOf(plan.defaults.remove) }
    var restore by remember { mutableStateOf(emptySet<String>()) }
    val decisions = Merge.Decisions(takeTheirs, remove, restore)
    val preview = remember(decisions) { Merge.apply(plan, decisions) }
    val headsUp = remember(preview) { preview.dates().flatMap { d -> dayWarnings(preview, d).map { "${d.shortLabel()}: $it" } } }
    WithActions(
        actions = {
            Button(onClick = { onApply(decisions) }) { Text("Apply update") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        },
    ) {

    Text("“${plan.local.title}”: revision ${plan.base.revision} → ${plan.incoming.revision}", style = MaterialTheme.typography.titleLarge)
    Text("From ${p.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (plan.freshness) {
        Merge.Freshness.OLDER -> Notice("This file looks older than the version you are using (revision ${plan.incoming.revision}, yours is ${plan.base.revision}). Importing it may bring back suggestions that were replaced.")
        Merge.Freshness.SAME_REVISION -> Notice("This file has the same revision number as yours but different content. It may be an older copy, or the assistant forgot to bump the revision.")
        else -> {}
    }
    if (p.deleted) Notice("This trip is in Recently deleted; updating it restores it.")
    Text(
        "Your notes, personal entries, moves and done/skipped marks are kept. Where you and the file changed the same thing, choose below. A copy of the current version is kept in History, so this can be undone.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (plan.changes.isNotEmpty()) {
        SectionTitle("What changed in the file")
        plan.changes.groupBy { it.category }.forEach { (cat, list) ->
            Text("${cat.label} (${list.size})", style = MaterialTheme.typography.titleSmall)
            list.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        }
    } else if (plan.conflicts.isEmpty() && plan.removals.isEmpty()) {
        Text("The file changes nothing you would see.", style = MaterialTheme.typography.bodyMedium)
    }

    if (plan.conflicts.isNotEmpty()) {
        SectionTitle("Changed on both sides (${plan.conflicts.size})")
        plan.conflicts.forEach { c ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${c.label} — ${c.field}", style = MaterialTheme.typography.titleSmall)
                    Choice("Keep mine", c.mine, selected = c.key !in takeTheirs) { takeTheirs = takeTheirs - c.key }
                    Choice("Use the file's", c.theirs, selected = c.key in takeTheirs) { takeTheirs = takeTheirs + c.key }
                }
            }
        }
    }

    if (plan.removals.isNotEmpty()) {
        SectionTitle("Not in the new file (${plan.removals.size})")
        Text("Ticked items are removed; unticked ones stay in your plan.", style = MaterialTheme.typography.bodySmall)
        plan.removals.forEach { r ->
            CheckRow("Remove ${r.label}", r.reason, checked = r.key in remove) { remove = if (it) remove + r.key else remove - r.key }
        }
    }

    if (plan.restorables.isNotEmpty()) {
        SectionTitle("You deleted these; the file updated them")
        plan.restorables.forEach { r ->
            CheckRow("Bring back ${r.label}", null, checked = r.key in restore) { restore = if (it) restore + r.key else restore - r.key }
        }
    }

    if (headsUp.isNotEmpty()) {
        SectionTitle("Heads-up after this update")
        headsUp.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Text("Nothing booked is moved automatically; adjust these in the day plan.", style = MaterialTheme.typography.bodySmall)
    }

    AssistantCheck(p.check)
    Warnings(p.warnings)
    }
}

@Composable
private fun Notice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Choice(label: String, value: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CheckRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BackupReview(p: PendingImport.Backup, onCancel: () -> Unit, onRestore: (List<dev.tlong.traveler.data.BackupTrip>) -> Unit) {
    var chosen by remember { mutableStateOf(p.file.trips.filter { it.base.id !in p.existing }.map { it.base.id }.toSet()) }
    WithActions(
        actions = {
            Button(onClick = { onRestore(p.file.trips.filter { it.base.id in chosen }) }, enabled = chosen.isNotEmpty()) { Text("Restore ${chosen.size}") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        },
    ) {
    Text("Backup made ${p.file.createdAt}, with ${p.file.trips.size} trip${if (p.file.trips.size == 1) "" else "s"}.")
    Text(
        "A trip already on this phone is replaced only if you tick it, and its current version is kept in its History first.",
        style = MaterialTheme.typography.bodyMedium,
    )
    p.file.trips.forEach { t ->
        val exists = t.base.id in p.existing
        CheckRow(
            t.local.title,
            (if (exists) "Replaces the copy on this phone · " else "New on this phone · ") + dateRangeLabel(t.local.start, t.local.end),
            checked = t.base.id in chosen,
        ) { chosen = if (it) chosen + t.base.id else chosen - t.base.id }
    }
    }
}
