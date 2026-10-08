package dev.tlong.traveler.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.domain.CheckItem
import dev.tlong.traveler.domain.CheckItem.Kind
import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.Checklists

fun Kind.label() = when (this) { Kind.TODO -> "To do"; Kind.PACK -> "Packing" }
fun Kind.glyph() = when (this) { Kind.TODO -> "✅"; Kind.PACK -> "🎒" }

/** "To do 3/12" on a trip, where items are ticked; "To do (12)" on a template, where they are not. */
@Composable
fun KindTabs(list: Checklist, kind: Kind, checkable: Boolean, onKind: (Kind) -> Unit) {
    PrimaryTabRow(selectedTabIndex = kind.ordinal) {
        Kind.entries.forEach { k ->
            val (done, total) = Checklists.tally(list, k)
            Tab(k == kind, onClick = { onKind(k) }, text = { Text(k.label() + if (checkable) " $done/$total" else " ($total)") })
        }
    }
}

/**
 * One kind of a checklist: open items under their sections, ticked ones folded into "Done" at the
 * bottom, and an add bar that stays open for the next item. Tap an item to edit it, swipe to delete.
 */
@Composable
fun ChecklistEditor(
    list: Checklist,
    kind: Kind,
    checkable: Boolean,
    onChange: (Checklist) -> Unit,
    onRemoved: (index: Int, item: CheckItem) -> Unit,
    modifier: Modifier = Modifier,
    onFromTemplate: (() -> Unit)? = null,
) {
    val items = Checklists.of(list, kind)
    val open = if (checkable) items.filterNot { it.done } else items
    val done = if (checkable) items.filter { it.done } else emptyList()
    var showDone by rememberSaveable(kind) { mutableStateOf(false) }
    val scroll = key(kind) { rememberLazyListState() }
    var sectionMenu by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CheckItem?>(null) }
    var target by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val sections = Checklists.sectionNames(list, kind)

    fun remove(item: CheckItem) {
        val index = list.items.indexOfFirst { it.id == item.id }
        onChange(Checklists.remove(list, item.id))
        onRemoved(index, item)
    }

    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(bottom = 8.dp)) {
            if (items.isEmpty()) item("empty") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nothing yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    onFromTemplate?.let { TextButton(onClick = it) { Text("Add from a template") } }
                }
            }
            Checklists.sections(open).forEach { (section, rows) ->
                if (section != null) item("s-$section") {
                    Box {
                        SectionHeader(section) { sectionMenu = section }
                        DropdownMenu(sectionMenu == section, onDismissRequest = { sectionMenu = null }) {
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { sectionMenu = null; onChange(Checklists.moveSection(list, kind, section, -1)) })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { sectionMenu = null; onChange(Checklists.moveSection(list, kind, section, 1)) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { sectionMenu = null; renaming = section })
                        }
                    }
                }
                items(rows, key = { it.id }) { item ->
                    ItemRow(item, checkable, onToggle = { onChange(Checklists.toggle(list, item.id)) }, onEdit = { editing = item }, onSwipe = { remove(item) })
                }
            }
            if (done.isNotEmpty()) {
                item("done-header") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (showDone) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null, Modifier.size(20.dp))
                        Text("Done (${done.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (showDone) items(done, key = { it.id }) { item ->
                    ItemRow(item, checkable, onToggle = { onChange(Checklists.toggle(list, item.id)) }, onEdit = { editing = item }, onSwipe = { remove(item) })
                }
            }
        }
        HorizontalDivider()
        AddBar(kind, sections, target, onTarget = { target = it }) { text -> onChange(Checklists.add(list, kind, text, target)) }
    }

    renaming?.let { from ->
        NameDialog("Rename section", from, "Section name", onDismiss = { renaming = null }) { to ->
            renaming = null
            onChange(Checklists.renameSection(list, kind, from, to))
        }
    }
    editing?.let { item ->
        ItemDialog(
            item, sections,
            onDismiss = { editing = null },
            onSave = { text, section ->
                editing = null
                if (text.isBlank()) remove(item) else onChange(Checklists.update(list, item.id) { it.copy(text = text.trim(), section = section?.trim()?.ifEmpty { null }) })
            },
            onDelete = { editing = null; remove(item) },
        )
    }
}

/** Tapping a heading offers to move or rename the section. */
@Composable
private fun SectionHeader(name: String, onClick: () -> Unit) {
    Text(
        name.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp).clickable(onClickLabel = "Move or rename", onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun ItemRow(item: CheckItem, checkable: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onSwipe: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) { if (state.currentValue != SwipeToDismissBoxValue.Settled) onSwipe() }
    SwipeToDismissBox(
        state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        Surface {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (checkable) {
                    Checkbox(item.done, { onToggle() }, Modifier.padding(start = 4.dp))
                } else {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 20.dp, end = 12.dp))
                }
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 10.dp).padding(end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddBar(kind: Kind, sections: List<String>, target: String?, onTarget: (String?) -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable(kind) { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    fun add() { if (text.isNotBlank()) { onAdd(text); text = "" } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box {
            TextButton(onClick = { picking = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Section: ${target ?: "none"}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(picking, onDismissRequest = { picking = false }) {
                DropdownMenuItem(text = { Text("No section") }, onClick = { picking = false; onTarget(null) })
                sections.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { picking = false; onTarget(s) }) }
                DropdownMenuItem(text = { Text("New section…") }, onClick = { picking = false; naming = true })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, singleLine = true,
                placeholder = { Text(if (kind == Kind.TODO) "Add a to-do" else "Add something to pack") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Done adds the item and keeps the keyboard up for the next one.
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::add, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, "Add") }
        }
    }
    if (naming) NameDialog("New section", "", "Section name", onDismiss = { naming = false }) { naming = false; onTarget(it) }
}

@Composable
private fun ItemDialog(item: CheckItem, sections: List<String>, onDismiss: () -> Unit, onSave: (String, String?) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(item.text) }
    var section by remember { mutableStateOf(item.section.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Item") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(section, { section = it }, singleLine = true, label = { Text("Section") }, placeholder = { Text("None") }, modifier = Modifier.fillMaxWidth())
                if (sections.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { s -> SuggestionChip(onClick = { section = s }, label = { Text(s) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, section) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Many items at once: one per line, "# Heading" for a section. */
@Composable
fun PasteDialog(list: Checklist, kind: Kind, onDismiss: () -> Unit, onAdd: (List<CheckItem>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, kind) { Checklists.parse(text, kind) }
    val adds = Checklists.newCount(list, parsed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste ${kind.label().lowercase()} items") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One item per line. A line starting with # starts a section.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    text, { text = it },
                    placeholder = { Text("# Day before\nCharge devices\n- [ ] Print tickets") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                if (parsed.isNotEmpty()) Text(
                    "Adds $adds" + if (parsed.size > adds) " (${parsed.size - adds} already here)" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(parsed) }, enabled = adds > 0) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, note: String? = null, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text(label) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
