package dev.tlong.traveler.data

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

    fun retrySave() = set(_trip.value)

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
