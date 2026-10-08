package dev.tlong.traveler.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.ChecklistTemplate
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.newItemId
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.overview.BackButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(navigator: Navigator) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val templates by container.templates.templates.collectAsState(null)
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist templates") },
                navigationIcon = { BackButton(navigator) },
                actions = { TextButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Text("New") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Reusable to-dos and packing items, such as “Every trip” or “International”. Adding one to a trip copies its items, so you can change the trip's list without changing the template.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (templates?.isEmpty() == true) item { Text("No templates yet.", style = MaterialTheme.typography.bodyMedium) }
            items(templates.orEmpty(), key = { it.id }) { t ->
                Card(onClick = { navigator.template(t.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(counts(Checklist(t.items)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (creating) NameDialog("New template", "", "Template name", onDismiss = { creating = false }) { name ->
        creating = false
        val t = ChecklistTemplate(newItemId(), name)
        scope.launch { container.templates.save(t); navigator.template(t.id) }
    }
}

/** Edits one template. Kept in local state and written on each change, so quick additions never race the database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(id: String, navigator: Navigator) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var template by remember(id) { mutableStateOf<ChecklistTemplate?>(null) }
    LaunchedEffect(id) { template = container.templates.all().firstOrNull { it.id == id } }
    val t = template ?: return Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(navigator) }) }) { Column(Modifier.padding(it)) {} }
    var kind by rememberSaveable { mutableStateOf(Kind.TODO) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val list = Checklist(t.items)

    fun save(next: ChecklistTemplate) {
        template = next
        scope.launch { container.templates.save(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.name) },
                navigationIcon = { BackButton(navigator) },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Paste several items…") }, onClick = { menu = false; pasting = true })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete template", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            KindTabs(list, kind, checkable = false) { kind = it }
            ChecklistEditor(
                list, kind, checkable = false,
                onChange = { save(t.copy(items = it.items)) },
                onRemoved = { index, item -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar("Deleted “${item.text}”", actionLabel = "Undo", withDismissAction = true)
                    if (r == SnackbarResult.ActionPerformed) template?.let { cur -> save(cur.copy(items = Checklists.insert(Checklist(cur.items), index, item).items)) }
                } },
                empty = if (kind == Kind.TODO) "No to-dos in this template. Type below, or paste a list (⋮ menu)."
                else "Nothing to pack in this template. Type below, or paste a list (⋮ menu).",
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pasting) PasteDialog(list, kind, onDismiss = { pasting = false }) { items ->
        pasting = false
        save(t.copy(items = Checklists.merge(list, items).first.items))
    }
    if (renaming) NameDialog("Rename template", t.name, "Template name", onDismiss = { renaming = false }) { name ->
        renaming = false
        save(t.copy(name = name))
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete “${t.name}”?") },
        text = { Text("Trips that already used it keep their items.") },
        confirmButton = {
            TextButton(onClick = { deleting = false; scope.launch { container.templates.delete(t.id); navigator.back() } }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}
