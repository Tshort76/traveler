package dev.tlong.traveler.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.tlong.traveler.ui.theme.LocalMapColors
import dev.tlong.traveler.ui.theme.MapColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import kotlin.coroutines.resume

/**
 * The trip map: [TripMapCanvas] over a terrain picture of the trip. The picture is drawn once,
 * while online, for this trip, these stays and this size (about 10–70 KB as WebP), and kept, so it
 * works offline with no tiles saved. Until one exists, or after the stays move, the canvas shows
 * its bundled country outlines instead.
 */
@Composable
fun TripMap(
    tripId: String, points: List<MapPoint>, segments: List<MapSegment>, online: Boolean, description: String,
    modifier: Modifier = Modifier, interactive: Boolean = false,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val backdrop = rememberBackdrop(tripId, points, size, online)
    Box(modifier.onSizeChanged { size = it }) {
        TripMapCanvas(points, segments, Modifier.fillMaxSize(), interactive, description, backdrop)
    }
}

@Composable
private fun rememberBackdrop(tripId: String, points: List<MapPoint>, size: IntSize, online: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val colors = LocalMapColors.current
    val density = context.resources.displayMetrics.density
    val key = remember(points, size, colors) {
        "${size.width}x${size.height}-" + (points.map { it.lat to it.lng } to colors).hashCode().toUInt().toString(16)
    }
    val image by produceState<ImageBitmap?>(null, key, online) {
        if (points.isEmpty() || size.width == 0 || size.height == 0) return@produceState
        val file = File(TripBackdrops.dir(context), "$tripId-$key.webp")
        value = withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
        if (value != null || !online) return@produceState
        val bitmap = snapshot(context, points, size, density, colors) ?: return@produceState
        value = bitmap.asImageBitmap()
        launch(Dispatchers.IO) { TripBackdrops.save(context, tripId, key, bitmap) }
    }
    return image
}

private suspend fun snapshot(context: Context, points: List<MapPoint>, size: IntSize, density: Float, colors: MapColors): Bitmap? {
    AreaMaps.init(context)
    val (center, zoom) = snapshotCamera(points, size.width.toFloat(), size.height.toFloat(), density)
    val options = MapSnapshotter.Options((size.width / density).toInt(), (size.height / density).toInt())
        .withPixelRatio(density)
        .withStyleBuilder(Style.Builder().fromJson(terrainStyle(colors, relief = true)))
        .withCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build())
        .withLogo(false)
        .withAttribution(true)
    return suspendCancellableCoroutine { c ->
        val snapshotter = MapSnapshotter(context, options)
        c.invokeOnCancellation { snapshotter.cancel() }
        snapshotter.start({ c.resume(it.bitmap) }, { c.resume(null) })
    }
}

internal object TripBackdrops {
    fun dir(context: Context) = File(context.filesDir, "trip-maps").apply { mkdirs() }

    /** Keeps one picture per trip and size: the newest. */
    fun save(context: Context, tripId: String, key: String, bitmap: Bitmap) {
        val size = key.substringBefore('-')
        dir(context).listFiles { f -> f.name.startsWith("$tripId-$size-") }?.forEach { it.delete() }
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        File(dir(context), "$tripId-$key.webp").outputStream().use { bitmap.compress(format, 80, it) }
    }

    /** Drops pictures of trips that no longer exist. */
    fun prune(context: Context, liveTripIds: Set<String>) {
        // "<tripId>-<w>x<h>-<hash>.webp"; a trip id may itself contain dashes.
        dir(context).listFiles()?.filter { it.name.substringBeforeLast('-').substringBeforeLast('-') !in liveTripIds }?.forEach { it.delete() }
    }
}
