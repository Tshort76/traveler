package dev.tlong.traveler.data

import dev.tlong.traveler.domain.Checklist
import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.model.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Saved : SaveState
    data object Saving : SaveState
    data class Failed(val message: String) : SaveState
}

/**
 * One open trip. Every edit goes through [edit]: the previous plan goes on the undo stack, the
 * new one is shown at once and written straight away (no debounce, so closing the app a moment
 * later loses nothing). Writes are serialized and conflated — only the newest plan is ever
 * written — and [saveState] says "saved" only once the newest plan is on disk.
 */
class TripSession(
    initial: StoredTrip,
    private val store: TripStore,
    private val scope: CoroutineScope,
) {
    private val _trip = MutableStateFlow(initial.local)
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    private val _base = MutableStateFlow(initial.base)
    val base: StateFlow<Trip> = _base.asStateFlow()

    private val _exportedHash = MutableStateFlow(initial.row.lastExportedHash)
    val exportedHash: StateFlow<String?> = _exportedHash.asStateFlow()

    /** The traveler's notes, stored beside the trip file rather than in it; see [TripRow.notes]. */
    private val _notes = MutableStateFlow(initial.row.notes)
    val notes: StateFlow<String?> = _notes.asStateFlow()

    /** The trip's to-dos and packing list, stored beside the trip file like [notes]. */
    private val _checklist = MutableStateFlow(TripStore.checklistOf(initial.row))
    val checklist: StateFlow<Checklist> = _checklist.asStateFlow()

    private val checklistWrites = Channel<Checklist>(Channel.CONFLATED)

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Saved)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val undoStack = ArrayDeque<Pair<String, Trip>>()

    private val writes = Channel<Trip>(Channel.CONFLATED)

    init {
        scope.launch {
            for (t in writes) {
                try {
                    store.saveLocal(t)
                    if (_trip.value == t) _saveState.value = SaveState.Saved
                } catch (e: Exception) {
                    _saveState.value = SaveState.Failed(e.message ?: "could not write to storage")
                }
            }
        }
        scope.launch {
            for (c in checklistWrites) {
                try {
                    store.setChecklist(_trip.value.id, c)
                    if (_checklist.value == c) _saveState.value = SaveState.Saved
                } catch (e: Exception) {
                    _saveState.value = SaveState.Failed(e.message ?: "could not write to storage")
                }
            }
        }
    }

    /** Shows and saves a checklist change at once; ticking items in quick succession writes only the latest. */
    fun setChecklist(list: Checklist) {
        if (list == _checklist.value) return
        _checklist.value = list
        _saveState.value = SaveState.Saving
        checklistWrites.trySend(list)
    }

    /** Applies [f]; returns false (and records nothing) when it changed nothing. */
    fun edit(label: String, f: (Trip) -> Trip): Boolean {
        val before = _trip.value
        val after = f(before)
        if (after == before) return false
        undoStack.addLast(label to before)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        set(after)
        return true
    }

    /** Restores the plan before the last edit and returns that edit's label. */
    fun undo(): String? {
        val (label, before) = undoStack.removeLastOrNull() ?: return null
        set(before)
        return label
    }

    fun retrySave() {
        set(_trip.value)
        checklistWrites.trySend(_checklist.value)
    }

    /** Saves the notes at once; they have no undo, since the editor's Cancel covers a change of mind. */
    fun setNotes(text: String) {
        val notes = text.trimEnd().ifBlank { null }
        if (notes == _notes.value) return
        _notes.value = notes
        _saveState.value = SaveState.Saving
        scope.launch {
            try {
                store.setNotes(_trip.value.id, notes)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed(e.message ?: "could not write to storage")
            }
        }
    }

    private fun set(t: Trip) {
        _trip.value = t
        _saveState.value = SaveState.Saving
        writes.trySend(t)
    }

    /** After a revision import or a snapshot restore replaced the stored documents. */
    fun replaced(stored: StoredTrip) {
        undoStack.clear()
        _base.value = stored.base
        _trip.value = stored.local
        _exportedHash.value = stored.row.lastExportedHash
        _checklist.value = TripStore.checklistOf(stored.row)
        _saveState.value = SaveState.Saved
    }

    suspend fun markExported() {
        store.markExported(_trip.value)
        _exportedHash.value = Export.hash(_trip.value)
    }

    fun localChangeCount() = Merge.localChangeCount(_base.value, _trip.value)

    companion object {
        const val MAX_UNDO = 50
    }
}
