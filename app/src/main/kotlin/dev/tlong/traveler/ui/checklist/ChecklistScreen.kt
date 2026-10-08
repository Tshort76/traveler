package dev.tlong.traveler.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.ChecklistTemplate
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.newItemId
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.common.SaveIndicator
import dev.tlong.traveler.ui.common.rememberSession
import dev.tlong.traveler.ui.importer.CheckRow
import dev.tlong.traveler.ui.overview.BackButton
import dev.tlong.traveler.ui.overview.LoadingScaffold
import kotlinx.coroutines.launch

@Composable
fun ChecklistScreen(tripId: String, tab: Int, navigator: Navigator) {
    val session = rememberSession(tripId).value ?: return LoadingScaffold(navigator)
    TripChecklist(session, Kind.entries[tab], navigator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripChecklist(session: TripSession, startKind: Kind, navigator: Navigator) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val trip by session.trip.collectAsStateWithLifecycle()
    val list by session.checklist.collectAsStateWithLifecycle()
    val saveState by session.saveState.collectAsStateWithLifecycle()
    val templates by container.templates.templates.collectAsState(emptyList())
    var kind by rememberSaveable { mutableStateOf(startKind) }
    var menu by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var savingAs by remember { mutableStateOf(false) }

    /** Applies a change and offers to take it back. */
    fun changeWithUndo(after: Checklist, message: String) {
        val before = list
        session.setChecklist(after)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) session.setChecklist(before)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist") },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    SaveIndicator(saveState, session::retrySave)
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add from a template…") }, onClick = { menu = false; picking = true })
                        DropdownMenuItem(text = { Text("Paste several items…") }, onClick = { menu = false; pasting = true })
                        DropdownMenuItem(text = { Text("Save as a template…") }, enabled = list.items.isNotEmpty(), onClick = { menu = false; savingAs = true })
                        DropdownMenuItem(
                            text = { Text("Uncheck all ${if (kind == Kind.PACK) "packing" else "to-dos"}") },
                            enabled = Checklists.of(list, kind).any { it.done },
                            onClick = { menu = false; changeWithUndo(Checklists.uncheckAll(list, kind), "Unchecked all ${kind.label().lowercase()}") },
                        )
                        DropdownMenuItem(text = { Text("Manage templates") }, onClick = { menu = false; navigator.templates() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            KindTabs(list, kind, checkable = true) { kind = it }
            ChecklistEditor(
                list, kind, checkable = true,
                onChange = session::setChecklist,
                onRemoved = { index, item -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar("Deleted “${item.text}”", actionLabel = "Undo", withDismissAction = true)
                    if (r == SnackbarResult.ActionPerformed) session.setChecklist(Checklists.insert(session.checklist.value, index, item))
                } },
                modifier = Modifier.weight(1f),
                onFromTemplate = { picking = true },
            )
        }
    }

    if (picking) TemplatePicker(
        list, templates,
        onDismiss = { picking = false },
        onManage = { picking = false; navigator.templates() },
    ) { chosen ->
        picking = false
        val (after, n) = Checklists.merge(list, chosen.flatMap { it.items })
        changeWithUndo(after, "Added $n item${if (n == 1) "" else "s"} from ${chosen.joinToString(" + ") { it.name }}")
    }
    if (pasting) PasteDialog(list, kind, onDismiss = { pasting = false }) { items ->
        pasting = false
        val (after, n) = Checklists.merge(list, items)
        changeWithUndo(after, "Added $n item${if (n == 1) "" else "s"}")
    }
    if (savingAs) {
        val names = templates.associateBy { it.name.lowercase() }
        NameDialog(
            "Save as a template", "", "Template name", onDismiss = { savingAs = false },
            note = "Replaces a template with the same name.",
        ) { name ->
            savingAs = false
            val id = names[name.lowercase()]?.id ?: newItemId()
            scope.launch {
                container.templates.save(ChecklistTemplate(id, name, list.items.map { it.copy(done = false) }))
                snackbar.showSnackbar("Saved template “$name”")
            }
        }
    }
}

@Composable
private fun TemplatePicker(
    list: Checklist,
    templates: List<ChecklistTemplate>,
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    onAdd: (List<ChecklistTemplate>) -> Unit,
) {
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    val picked = templates.filter { it.id in chosen }
    val (after, n) = remember(list, picked) { Checklists.merge(list, picked.flatMap { it.items }) }
    val added = after.items.drop(list.items.size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from a template") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (templates.isEmpty()) Text("No templates yet.")
                templates.forEach { t ->
                    CheckRow(t.name, counts(Checklist(t.items)), checked = t.id in chosen) { chosen = if (it) chosen + t.id else chosen - t.id }
                }
                if (picked.isNotEmpty()) Text(
                    "Adds ${added.count { it.kind == Kind.TODO }} to do and ${added.count { it.kind == Kind.PACK }} to pack" +
                        (picked.sumOf { it.items.size } - n).let { if (it > 0) " ($it already here)" else "" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(picked) }, enabled = n > 0) { Text("Add") } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onManage) { Text("Manage") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** "8 to do · 30 to pack" */
fun counts(list: Checklist): String =
    "${Checklists.of(list, Kind.TODO).size} to do · ${Checklists.of(list, Kind.PACK).size} to pack"
