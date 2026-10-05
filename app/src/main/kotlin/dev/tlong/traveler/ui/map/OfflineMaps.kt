package dev.tlong.traveler.ui.map

import android.content.Context
import dev.tlong.traveler.domain.mapAreas
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.model.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

/**
 * Street maps saved ahead of time, one MapLibre offline region per stay, tagged "tripId/stayId".
 * The streets come down once, in the background of the app, and keep working without a signal.
 * They hold the terrain style's tiles down to [MAX_ZOOM], about a quarter mile per pin's width.
 */
object OfflineMaps {
    private const val MIN_ZOOM = 8.0
    private const val STYLE_URL = "https://traveler.invalid/terrain-style.json"

    sealed interface State {
        data class Saving(val stayName: String, val index: Int, val count: Int, val fraction: Float, val bytes: Long) : State
        data class Saved(val stays: Int, val bytes: Long) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<String, Job>()
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    /** Per trip id: what is saved, or the download under way. */
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    private fun set(tripId: String, s: State?) = _states.update { if (s == null) it - tripId else it + (tripId to s) }

    /** Reads what is already saved for [trip], for a dialog opened after a restart. */
    fun refresh(context: Context, trip: Trip) {
        if (jobs[trip.id]?.isActive == true) return
        scope.launch {
            val saved = regions(context).filter { tripOf(it) == trip.id }
            set(trip.id, if (saved.isEmpty()) null else State.Saved(saved.size, size(saved.mapNotNull { status(it) })))
        }
    }

    /** Replaces the trip's saved maps with one per stay that has places. */
    fun save(context: Context, trip: Trip) {
        if (jobs[trip.id]?.isActive == true) return
        jobs[trip.id] = scope.launch {
            regions(context).filter { tripOf(it) == trip.id }.forEach { delete(it) }
            val areas = trip.mapAreas()
            // A region needs a style URL, and the downloader only fetches over HTTP; so the style is put
            // in the database under a placeholder URL it then finds there. Tiles are shared with the
            // on-screen style by their URL.
            val now = System.currentTimeMillis() / 1000
            OfflineManager.getInstance(context).putResourceWithUrl(STYLE_URL, terrainStyle(LIGHT).encodeToByteArray(), now, now + 10L * 365 * 24 * 3600, null, false)
            val done = mutableListOf<OfflineRegionStatus>()
            areas.forEachIndexed { i, area ->
                val name = trip.stay(area.stayId)?.name ?: area.stayId
                set(trip.id, State.Saving(name, i + 1, areas.size, 0f, size(done)))
                val bounds = LatLngBounds.from(area.north, area.east, area.south, area.west)
                val definition = OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density)
                val region = create(context, definition, "${trip.id}/${area.stayId}".encodeToByteArray())
                    ?: return@launch set(trip.id, State.Failed("Could not start saving $name."))
                val error = download(region) { s ->
                    val fraction = if (s.requiredResourceCount > 0) s.completedResourceCount.toFloat() / s.requiredResourceCount else 0f
                    set(trip.id, State.Saving(name, i + 1, areas.size, fraction, size(done + s)))
                }
                if (error != null) return@launch set(trip.id, State.Failed("Saving $name stopped: $error"))
                status(region)?.let { done += it }
            }
            set(trip.id, State.Saved(areas.size, size(done)))
        }
    }

    fun remove(context: Context, tripId: String) {
        jobs.remove(tripId)?.cancel()
        scope.launch {
            regions(context).filter { tripOf(it) == tripId }.forEach { delete(it) }
            set(tripId, null)
        }
    }

    /** Drops saved maps of trips that no longer exist. */
    fun prune(context: Context, liveTripIds: Set<String>) = scope.launch {
        regions(context).filter { tripOf(it) !in liveTripIds }.forEach { delete(it) }
    }

    /** Roughly what the regions take on disk: each one's tiles, plus the shared style, sprites and glyphs once. */
    private fun size(s: List<OfflineRegionStatus>) =
        s.sumOf { it.completedTileSize } + (s.maxOfOrNull { it.completedResourceSize - it.completedTileSize } ?: 0L)

    /** Colours do not matter to what gets saved, only the sources and layers. */
    private val LIGHT = dev.tlong.traveler.ui.theme.MapColors(
        androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Gray,
        androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Black,
    )

    private fun tripOf(r: OfflineRegion) = r.metadata.decodeToString().substringBefore('/')

    private suspend fun regions(context: Context): List<OfflineRegion> = suspendCancellableCoroutine { c ->
        AreaMaps.init(context)
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = c.resume(offlineRegions?.toList().orEmpty())
            override fun onError(error: String) = c.resume(emptyList())
        })
    }

    private suspend fun create(context: Context, d: OfflineTilePyramidRegionDefinition, meta: ByteArray): OfflineRegion? = suspendCancellableCoroutine { c ->
        OfflineManager.getInstance(context).createOfflineRegion(d, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = c.resume(offlineRegion)
            override fun onError(error: String) = c.resume(null)
        })
    }

    private suspend fun status(r: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { c ->
        r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = c.resume(status)
            override fun onError(error: String?) = c.resume(null)
        })
    }

    private suspend fun delete(r: OfflineRegion) = suspendCancellableCoroutine { c ->
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = c.resume(Unit)
            override fun onError(error: String) = c.resume(Unit)
        })
    }

    /**
     * Runs the region's download to the end; null when complete, else why it stopped. A dropped
     * connection is not an error here: MapLibre keeps retrying, and the progress simply pauses.
     */
    private suspend fun download(r: OfflineRegion, onProgress: (OfflineRegionStatus) -> Unit): String? = suspendCancellableCoroutine { c ->
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                onProgress(status)
                if (status.isComplete && c.isActive) { r.setDownloadState(OfflineRegion.STATE_INACTIVE); c.resume(null) }
            }
            override fun onError(error: OfflineRegionError) { android.util.Log.w("OfflineMaps", "${error.reason}: ${error.message}") }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                if (c.isActive) c.resume("the area is larger than $limit map tiles")
            }
        })
        c.invokeOnCancellation { r.setDownloadState(OfflineRegion.STATE_INACTIVE) }
        r.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
}
