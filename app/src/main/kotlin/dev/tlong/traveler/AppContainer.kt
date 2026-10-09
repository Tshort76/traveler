package dev.tlong.traveler

import dev.tlong.traveler.data.DeviceCalendar
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.tlong.traveler.data.BackupTrip
import dev.tlong.traveler.data.ImportRouter
import dev.tlong.traveler.data.PendingImport
import dev.tlong.traveler.data.TravelerDatabase
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.data.TemplateStore
import dev.tlong.traveler.data.TripStore
import dev.tlong.traveler.domain.ChecklistTemplate
import dev.tlong.traveler.domain.Checklists
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.map.OfflineMaps
import dev.tlong.traveler.ui.map.TripBackdrops
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Hand-wired dependencies: one database, one store, one session per open trip. */
class AppContainer(private val context: Context, db: TravelerDatabase = TravelerDatabase.open(context)) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = TripStore(db.trips())
    val templates = TemplateStore(db.templates())
    val calendar = DeviceCalendar(context)
    private val router = ImportRouter(store)

    private val sessions = mutableMapOf<String, TripSession>()
    private val sessionLock = Mutex()

    suspend fun session(id: String): TripSession? = sessionLock.withLock {
        sessions[id] ?: store.load(id)?.let { TripSession(it, store, scope).also { s -> sessions[id] = s } }
    }

    private suspend fun refreshSession(id: String) {
        val stored = store.load(id) ?: return
        sessionLock.withLock { sessions[id] }?.replaced(stored)
    }


    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pending.asStateFlow()

    suspend fun openText(text: String, source: String) {
        _pending.value = withContext(Dispatchers.IO) { router.route(text, source) }
    }

    suspend fun openUri(uri: Uri) {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "file"
        val text = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        }
        if (text == null) {
            _pending.value = PendingImport.Invalid(name, listOf("The file could not be opened. It may have moved, or the app that shared it withdrew access."), emptyList())
        } else {
            openText(text, name)
        }
    }

    fun dismissImport() { _pending.value = null }

    /** Imports a new trip and returns its id. */
    suspend fun acceptNew(trip: Trip): String {
        store.importNew(trip)
        _pending.value = null
        return trip.id
    }

    suspend fun acceptRevision(p: PendingImport.Revision, decisions: Merge.Decisions): String {
        val merged = Merge.apply(p.plan, decisions)
        store.applyRevision(p.plan.incoming, merged)
        refreshSession(merged.id)
        _pending.value = null
        return merged.id
    }

    suspend fun undelete(id: String) {
        store.undelete(id)
        _pending.value = null
    }

    suspend fun backupText(createdAt: String) = store.backupText(createdAt, templates.all())

    suspend fun restoreBackup(trips: List<BackupTrip>, restoredTemplates: List<ChecklistTemplate> = emptyList()) {
        store.restore(trips)
        restoredTemplates.forEach { templates.save(it) }
        trips.forEach { refreshSession(it.base.id) }
        _pending.value = null
    }

    suspend fun restoreSnapshot(tripId: String, snapshotId: Long) {
        store.restoreSnapshot(snapshotId)
        refreshSession(tripId)
    }

    /** Empties the recently-deleted list past its keep time, and the saved maps of trips now gone. */
    /** Offers the starter templates once, on the first launch that has checklists; deleting them all keeps them gone. */
    fun seedTemplatesLater() = scope.launch {
        val prefs = context.getSharedPreferences("traveler", Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_TEMPLATES, false)) return@launch
        templates.seedIfEmpty(Checklists.starters)
        prefs.edit().putBoolean(SEEDED_TEMPLATES, true).apply()
    }

    fun purgeExpiredLater() = scope.launch {
        store.purgeExpired()
        val ids = store.summaries.first().map { it.id }.toSet()
        TripBackdrops.prune(context, ids)
        OfflineMaps.prune(context, ids)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val SEEDED_TEMPLATES = "seededChecklistTemplates"
    }
}
