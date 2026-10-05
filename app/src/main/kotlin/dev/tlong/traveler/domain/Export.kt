package dev.tlong.traveler.domain

import dev.tlong.traveler.model.ExportInfo
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

object Export {
    const val APP_NAME = "Traveler"

    /**
     * The current plan in the trip-file format, edits included. `revision` stays at the base
     * revision it was built on — the assistant bumps it when it sends a new version — and
     * `exportedFrom` says how far the plan has moved from that revision.
     */
    fun tripFile(base: Trip, local: Trip, now: Instant = Instant.now()): Trip = local.compact().copy(
        revision = base.revision,
        exportedFrom = ExportInfo(
            app = APP_NAME,
            exportedAt = now.truncatedTo(ChronoUnit.SECONDS).toString(),
            basedOnRevision = base.revision,
            localChanges = Merge.localChangeCount(base, local),
        ),
    )

    fun text(base: Trip, local: Trip, now: Instant = Instant.now()): String = TripJson.encode(tripFile(base, local, now)) + "\n"

    fun fileName(base: Trip, local: Trip): String {
        val edits = if (Merge.localChangeCount(base, local) > 0) "-edited" else ""
        return "${local.id}.r${base.revision}$edits.trip.json"
    }

    /** Identity of a document's content, used to spot the same file opened twice. */
    fun hash(trip: Trip): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(TripJson.encodeCompact(trip.copy(exportedFrom = null).normalized()).toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
