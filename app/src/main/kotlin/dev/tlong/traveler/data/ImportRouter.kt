package dev.tlong.traveler.data

import dev.tlong.traveler.domain.ActivityImport
import dev.tlong.traveler.domain.Export
import dev.tlong.traveler.domain.Merge
import dev.tlong.traveler.model.ACTIVITIES_FORMAT
import dev.tlong.traveler.model.ActivityFile
import dev.tlong.traveler.model.Stamp
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import dev.tlong.traveler.model.TripReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/** What an opened file turned out to be, and what the preview screen should offer. */
sealed interface PendingImport {
    val source: String

    data class Invalid(override val source: String, val errors: List<String>, val warnings: List<String>) : PendingImport

    data class NewTrip(override val source: String, val trip: Trip, val warnings: List<String>, val check: FileCheck = FileCheck()) : PendingImport

    /** The same content as the copy already in use: nothing to do, and no duplicate is made. */
    data class AlreadyImported(override val source: String, val trip: Trip, val deleted: Boolean, val check: FileCheck = FileCheck()) : PendingImport

    data class Revision(
        override val source: String, val plan: Merge.Plan, val warnings: List<String>, val deleted: Boolean, val check: FileCheck = FileCheck(),
    ) : PendingImport

    data class Backup(override val source: String, val file: BackupFile, val existing: Set<String>) : PendingImport

    /** New activities for one of [trips] (live trips, newest plans first); the preview picks the trip and stays. */
    data class Activities(
        override val source: String, val file: ActivityFile, val trips: List<Trip>, val check: FileCheck = FileCheck(), val preferTrip: String? = null,
    ) : PendingImport
}

/**
 * A day's ＋ menu imports day plans only: an activities file with `days`, applied as the trip's import would,
 * with [tripId] chosen. Anything else is refused with what to do instead.
 */
fun dayPlanOnly(p: PendingImport, tripId: String): PendingImport = when {
    p is PendingImport.Invalid -> p
    p is PendingImport.Activities && p.file.days.isNotEmpty() ->
        p.copy(preferTrip = tripId.takeIf { id -> p.trips.any { it.id == id } })
    else -> PendingImport.Invalid(p.source, listOf(
        if (p is PendingImport.Activities) "This activities file has no day plans. Import it from the trip's Import revision instead."
        else "This is not a day plan. Import trip files and backups from the trip's Import revision instead."
    ), emptyList())
}

/** What the strict checks found for the assistant to fix, and whether the validator's stamp matches. */
data class FileCheck(val forAssistant: List<String> = emptyList(), val stamp: Stamp.Check = Stamp.Check.NONE)

class ImportRouter(private val store: TripStore) {

    suspend fun route(text: String, source: String): PendingImport {
        if (looksLikeBackup(text)) {
            val file = runCatching { store.readBackup(TripJson.extractJson(text)) }.getOrElse {
                return PendingImport.Invalid(source, listOf("This looks like a Traveler backup but could not be read: ${it.message}"), emptyList())
            }
            return PendingImport.Backup(source, file, file.trips.filter { store.exists(it.base.id) }.map { it.base.id }.toSet())
        }
        if (formatOf(text) == ACTIVITIES_FORMAT) {
            val read = TripReader.readActivities(text)
            val file = read.file
            if (!read.ok || file == null) return PendingImport.Invalid(source, read.errors, read.warnings)
            val trips = store.liveTrips()
            if (trips.isEmpty()) return PendingImport.Invalid(source, listOf("These are activities for a trip, and there is no trip on this phone to add them to. Import the trip first."), emptyList())
            return PendingImport.Activities(source, file, ActivityImport.ordered(trips, LocalDate.now()), FileCheck(read.forAssistant, read.stamp))
        }
        val read = TripReader.read(text)
        val trip = read.trip
        if (!read.ok || trip == null) return PendingImport.Invalid(source, read.errors, read.warnings)
        val check = FileCheck(read.forAssistant, read.stamp)
        val stored = store.load(trip.id) ?: return PendingImport.NewTrip(source, trip, read.warnings, check)
        val deleted = stored.row.deletedAt != null
        if (Export.hash(trip) == stored.row.baseHash || Export.hash(trip) == Export.hash(stored.local)) {
            return PendingImport.AlreadyImported(source, stored.local, deleted, check)
        }
        return PendingImport.Revision(source, Merge.plan(stored.base, stored.local, trip), read.warnings, deleted, check)
    }

    private fun looksLikeBackup(text: String): Boolean = formatOf(text) == BACKUP_FORMAT

    private fun formatOf(text: String): String? = runCatching {
        val e = TripJson.reader.parseToJsonElement(TripJson.extractJson(text)) as? JsonObject
        (e?.get("format") as? JsonPrimitive)?.content
    }.getOrNull()
}
