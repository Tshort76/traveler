package dev.tlong.traveler.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * A trip is stored as documents, not tables: BASE is the file as last imported and LOCAL is the
 * traveler's current plan, both in the trip-file format. Merge, export and backup all work on
 * whole documents, and a few hundred activities is well under a megabyte, so rewriting the local
 * document on every edit is cheap and keeps every save atomic. The scalar columns exist for the
 * trip list, which must not parse every document to draw itself.
 */

@Entity(tableName = "trips")
data class TripRow(
    @PrimaryKey val id: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val baseRevision: Int,
    val baseHash: String,
    val baseJson: String,
    val localJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    /** Set when moved to "Recently deleted"; purged after [TripStore.DELETE_AFTER_MS]. */
    val deletedAt: Long? = null,
    val lastExportedAt: Long? = null,
    /** Hash of LOCAL at the last export, so the app can say whether there are unexported changes. */
    val lastExportedHash: String? = null,
    /** The traveler's own notes on the trip. Kept here, beside the trip file rather than in it, so no revision or assistant ever sees or changes them. */
    val notes: String? = null,
    /** The trip's to-dos and packing list as a [dev.tlong.traveler.domain.Checklist] document; beside the trip file, like [notes]. */
    val checklist: String? = null,
)

/** A reusable checklist: a [dev.tlong.traveler.domain.ChecklistTemplate] document, copied into a trip when applied. */
@Entity(tableName = "templates")
data class TemplateRow(
    @PrimaryKey val id: String,
    val name: String,
    val json: String,
    val updatedAt: Long,
)

data class TripSummaryRow(
    val id: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val baseRevision: Int,
    val updatedAt: Long,
    val archived: Boolean,
    val deletedAt: Long?,
)

@Entity(tableName = "snapshots", indices = [Index("tripId")])
data class SnapshotRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val createdAt: Long,
    val reason: String,
    val baseRevision: Int,
    val baseJson: String,
    val localJson: String,
)

data class SnapshotSummary(val id: Long, val tripId: String, val createdAt: Long, val reason: String, val baseRevision: Int)

@Dao
interface TripDao {
    @Query("SELECT id, title, startDate, endDate, baseRevision, updatedAt, archived, deletedAt FROM trips ORDER BY startDate")
    fun observeSummaries(): Flow<List<TripSummaryRow>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripRow?

    @Query("SELECT * FROM trips WHERE deletedAt IS NULL ORDER BY startDate")
    suspend fun allLive(): List<TripRow>

    @Upsert
    suspend fun upsert(row: TripRow)

    @Query("UPDATE trips SET localJson = :json, title = :title, startDate = :start, endDate = :end, updatedAt = :at WHERE id = :id")
    suspend fun updateLocal(id: String, json: String, title: String, start: String, end: String, at: Long)

    @Query("UPDATE trips SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: String, notes: String?)

    @Query("UPDATE trips SET checklist = :json WHERE id = :id")
    suspend fun setChecklist(id: String, json: String?)

    @Query("UPDATE trips SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long)

    @Query("UPDATE trips SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: String, deletedAt: Long?)

    @Query("UPDATE trips SET lastExportedAt = :at, lastExportedHash = :hash WHERE id = :id")
    suspend fun markExported(id: String, at: Long, hash: String)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM trips WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deletedBefore(before: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(row: SnapshotRow): Long

    @Query("SELECT id, tripId, createdAt, reason, baseRevision FROM snapshots WHERE tripId = :tripId ORDER BY createdAt DESC, id DESC")
    fun observeSnapshots(tripId: String): Flow<List<SnapshotSummary>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun snapshot(id: Long): SnapshotRow?

    @Query("DELETE FROM snapshots WHERE tripId = :tripId AND id NOT IN (SELECT id FROM snapshots WHERE tripId = :tripId ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun pruneSnapshots(tripId: String, keep: Int)

    @Query("DELETE FROM snapshots WHERE tripId = :tripId")
    suspend fun deleteSnapshots(tripId: String)

    @Transaction
    suspend fun purge(id: String) {
        deleteSnapshots(id)
        delete(id)
    }
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<TemplateRow>>

    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<TemplateRow>

    @Upsert
    suspend fun upsert(row: TemplateRow)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [TripRow::class, SnapshotRow::class, TemplateRow::class], version = 3, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class TravelerDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun templates(): TemplateDao

    companion object {
        fun open(context: Context): TravelerDatabase =
            Room.databaseBuilder(context, TravelerDatabase::class.java, "traveler.db").build()

        fun inMemory(context: Context): TravelerDatabase =
            Room.inMemoryDatabaseBuilder(context, TravelerDatabase::class.java).allowMainThreadQueries().build()
    }
}
