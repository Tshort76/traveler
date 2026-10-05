package dev.tlong.traveler.data

import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.normalized
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

data class TripSummary(
    val id: String, val title: String, val startDate: String, val endDate: String,
    val revision: Int, val updatedAt: Long, val archived: Boolean, val deletedAt: Long?,
)

/** A trip as the editing screens need it: the last import and the current plan. */
data class StoredTrip(val base: Trip, val local: Trip, val row: TripRow) {
    val hasUnexportedChanges get() = row.lastExportedHash != Export.hash(local)
}

/** A backup holds every trip, both documents each, so restoring keeps the merge base intact. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupFile(
    @EncodeDefault val format: String = BACKUP_FORMAT,
    @EncodeDefault val formatVersion: Int = 1,
    val createdAt: String,
    val trips: List<BackupTrip>,
)

@Serializable
data class BackupTrip(val base: Trip, val local: Trip, val archived: Boolean = false, val updatedAt: Long = 0)

const val BACKUP_FORMAT = "traveler-backup"

class TripStore(private val dao: TripDao, private val clock: () -> Long = System::currentTimeMillis) {

    val summaries: Flow<List<TripSummary>> = dao.observeSummaries().map { rows ->
        rows.map { TripSummary(it.id, it.title, it.startDate, it.endDate, it.baseRevision, it.updatedAt, it.archived, it.deletedAt) }
    }

    fun snapshots(tripId: String) = dao.observeSnapshots(tripId)

    /** Normalized on the way out, so a trip saved by an older version gets today's day and plan rules. */
    suspend fun load(id: String): StoredTrip? = dao.get(id)?.let {
        StoredTrip(TripJson.decode(it.baseJson).normalized(), TripJson.decode(it.localJson).normalized(), it)
    }

    suspend fun exists(id: String) = dao.get(id) != null

    suspend fun importNew(trip: Trip) {
        val n = trip.normalized()
        val now = clock()
        val json = TripJson.encodeCompact(n)
        dao.upsert(
            TripRow(
                id = n.id, title = n.title, startDate = n.startDate, endDate = n.endDate, baseRevision = n.revision,
                baseHash = Export.hash(n), baseJson = json, localJson = json, createdAt = now, updatedAt = now,
            ),
        )
    }

    suspend fun saveLocal(local: Trip) {
        dao.updateLocal(local.id, TripJson.encodeCompact(local), local.title, local.startDate, local.endDate, clock())
    }

    /** Accepting a revision: the old pair goes to a snapshot first, so the import can be undone. */
    suspend fun applyRevision(newBase: Trip, merged: Trip) {
        val row = dao.get(newBase.id) ?: return importNew(newBase)
        snapshot(row, "Before importing revision ${newBase.revision}")
        val base = newBase.normalized()
        dao.upsert(
            row.copy(
                title = merged.title, startDate = merged.startDate, endDate = merged.endDate,
                baseRevision = base.revision, baseHash = Export.hash(base), baseJson = TripJson.encodeCompact(base),
                localJson = TripJson.encodeCompact(merged), updatedAt = clock(), deletedAt = null,
            ),
        )
    }

    suspend fun restoreSnapshot(snapshotId: Long) {
        val snap = dao.snapshot(snapshotId) ?: return
        val row = dao.get(snap.tripId) ?: return
        snapshot(row, "Before restoring an earlier version")
        val base = TripJson.decode(snap.baseJson)
        val local = TripJson.decode(snap.localJson)
        dao.upsert(
            row.copy(
                title = local.title, startDate = local.startDate, endDate = local.endDate, baseRevision = snap.baseRevision,
                baseHash = Export.hash(base), baseJson = snap.baseJson, localJson = snap.localJson, updatedAt = clock(),
            ),
        )
    }

    private suspend fun snapshot(row: TripRow, reason: String) {
        dao.insertSnapshot(
            SnapshotRow(tripId = row.id, createdAt = clock(), reason = reason, baseRevision = row.baseRevision,
                baseJson = row.baseJson, localJson = row.localJson),
        )
        dao.pruneSnapshots(row.id, KEEP_SNAPSHOTS)
    }

    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, clock())

    suspend fun moveToDeleted(id: String) = dao.setDeleted(id, clock())

    suspend fun undelete(id: String) = dao.setDeleted(id, null)

    suspend fun purge(id: String) = dao.purge(id)

    suspend fun purgeExpired() = dao.deletedBefore(clock() - DELETE_AFTER_MS).forEach { dao.purge(it) }

    suspend fun markExported(local: Trip) = dao.markExported(local.id, clock(), Export.hash(local))


    suspend fun backupText(createdAt: String): String {
        val trips = dao.allLive().map {
            BackupTrip(TripJson.decode(it.baseJson), TripJson.decode(it.localJson), it.archived, it.updatedAt)
        }
        return TripJson.writer.encodeToString(BackupFile.serializer(), BackupFile(createdAt = createdAt, trips = trips)) + "\n"
    }

    fun readBackup(text: String): BackupFile = TripJson.reader.decodeFromString(BackupFile.serializer(), text)

    /** Restores the chosen trips; one that already exists is snapshotted before it is replaced. */
    suspend fun restore(trips: List<BackupTrip>) {
        trips.forEach { t ->
            val base = t.base.normalized()
            val existing = dao.get(base.id)
            if (existing != null) snapshot(existing, "Before restoring from a backup")
            val now = clock()
            dao.upsert(
                TripRow(
                    id = base.id, title = t.local.title, startDate = t.local.startDate, endDate = t.local.endDate,
                    baseRevision = base.revision, baseHash = Export.hash(base), baseJson = TripJson.encodeCompact(base),
                    localJson = TripJson.encodeCompact(t.local.normalized()), createdAt = existing?.createdAt ?: now,
                    updatedAt = now, archived = t.archived,
                ),
            )
        }
    }

    companion object {
        const val KEEP_SNAPSHOTS = 20
        const val DELETE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
