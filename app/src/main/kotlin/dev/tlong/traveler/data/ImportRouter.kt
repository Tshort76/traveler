package dev.tlong.traveler.data

import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import dev.tlong.traveler.model.TripReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What an opened file turned out to be, and what the preview screen should offer. */
sealed interface PendingImport {
    val source: String

    data class Invalid(override val source: String, val errors: List<String>, val warnings: List<String>) : PendingImport

    data class NewTrip(override val source: String, val trip: Trip, val warnings: List<String>) : PendingImport

    /** The same content as the copy already in use: nothing to do, and no duplicate is made. */
    data class AlreadyImported(override val source: String, val trip: Trip, val deleted: Boolean) : PendingImport

    data class Revision(override val source: String, val plan: Merge.Plan, val warnings: List<String>, val deleted: Boolean) : PendingImport

    data class Backup(override val source: String, val file: BackupFile, val existing: Set<String>) : PendingImport
}

class ImportRouter(private val store: TripStore) {

    suspend fun route(text: String, source: String): PendingImport {
        if (looksLikeBackup(text)) {
            val file = runCatching { store.readBackup(TripJson.extractJson(text)) }.getOrElse {
                return PendingImport.Invalid(source, listOf("This looks like a Traveler backup but could not be read: ${it.message}"), emptyList())
            }
            return PendingImport.Backup(source, file, file.trips.filter { store.exists(it.base.id) }.map { it.base.id }.toSet())
        }
        val read = TripReader.read(text)
        val trip = read.trip
        if (!read.ok || trip == null) return PendingImport.Invalid(source, read.errors, read.warnings)
        val stored = store.load(trip.id) ?: return PendingImport.NewTrip(source, trip, read.warnings)
        val deleted = stored.row.deletedAt != null
        if (Export.hash(trip) == stored.row.baseHash || Export.hash(trip) == Export.hash(stored.local)) {
            return PendingImport.AlreadyImported(source, stored.local, deleted)
        }
        return PendingImport.Revision(source, Merge.plan(stored.base, stored.local, trip), read.warnings, deleted)
    }

    private fun looksLikeBackup(text: String): Boolean = runCatching {
        val e = TripJson.reader.parseToJsonElement(TripJson.extractJson(text)) as? JsonObject
        (e?.get("format") as? JsonPrimitive)?.content == BACKUP_FORMAT
    }.getOrDefault(false)
}
