package dev.tlong.traveler.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tlong.traveler.ui.theme.LocalMapColors
import dev.tlong.traveler.ui.theme.MapColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.max

/** OpenFreeMap's OpenStreetMap vector tiles: free, no key; their attribution shows under the ⓘ button. */
private const val TILES = "https://tiles.openfreemap.org/planet"

/** No closer than this: a pin's width is then about a quarter mile, and the saved maps stop here too. */
internal const val MAX_ZOOM = 13.0

/**
 * Land, water, parks and built-up areas, and nothing else: no streets, no lettering. It answers
 * "what is near what" (Google Maps does directions), and having no text means no font downloads,
 * which were most of a saved map's size. Pins are images for the same reason. [relief] adds Natural
 * Earth shaded relief (to zoom 6), which shows mountains at the scale of a whole trip.
 */
internal fun terrainStyle(c: MapColors, relief: Boolean = false): String {
    fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
    val green = "#7DB46C"
    return """
    {"version": 8, "name": "Traveler terrain",
     "sources": {"omt": {"type": "vector", "url": "$TILES"}${if (relief) RELIEF_SOURCE else ""}},
     "layers": [
      {"id": "land", "type": "background", "paint": {"background-color": "${hex(c.land)}"}},${if (relief) RELIEF_LAYER else ""}
      {"id": "built", "type": "fill", "source": "omt", "source-layer": "landuse",
       "filter": ["in", ["get", "class"], ["literal", ["residential", "commercial", "industrial", "retail"]]],
       "paint": {"fill-color": "${hex(c.border)}", "fill-opacity": 0.35}},
      {"id": "green", "type": "fill", "source": "omt", "source-layer": "landcover",
       "filter": ["in", ["get", "class"], ["literal", ["wood", "grass", "wetland"]]],
       "paint": {"fill-color": "$green", "fill-opacity": 0.3}},
      {"id": "park", "type": "fill", "source": "omt", "source-layer": "park", "paint": {"fill-color": "$green", "fill-opacity": 0.35}},
      {"id": "sand", "type": "fill", "source": "omt", "source-layer": "landcover", "filter": ["==", ["get", "class"], "sand"],
       "paint": {"fill-color": "#E8D9A8", "fill-opacity": 0.6}},
      {"id": "river", "type": "line", "source": "omt", "source-layer": "waterway",
       "paint": {"line-color": "${hex(c.water)}", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1, 13, 2.5]}},
      {"id": "water", "type": "fill", "source": "omt", "source-layer": "water", "paint": {"fill-color": "${hex(c.water)}"}},
      {"id": "border", "type": "line", "source": "omt", "source-layer": "boundary",
       "filter": ["all", ["==", ["get", "admin_level"], 2], ["!=", ["get", "maritime"], 1]],
       "paint": {"line-color": "${hex(c.label)}", "line-width": 1, "line-dasharray": [3, 2]}}
     ]}
    """.trimIndent()
}

private const val RELIEF_SOURCE =
    """, "relief": {"type": "raster", "tileSize": 256, "maxzoom": 6, "tiles": ["https://tiles.openfreemap.org/natural_earth/ne2sr/{z}/{x}/{y}.png"]}"""
private const val RELIEF_LAYER = """
      {"id": "relief", "type": "raster", "source": "relief", "paint": {"raster-opacity": 0.55}},"""

internal object AreaMaps {
    @Volatile private var ready = false

    /** Whatever has been viewed also lands in MapLibre's ambient cache, on top of saved maps. */
    fun init(context: Context) {
        if (ready) return
        MapLibre.getInstance(context.applicationContext)
        OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(100L * 1024 * 1024, null)
        ready = true
    }
}

/**
 * A terrain map with numbered pins. Without tiles (offline, nothing saved) it is the land colour
 * with the pins and scale bar, which still answers the question. [onClick] makes it a fixed preview
 * that opens something larger; without it the map pans and zooms, and names each pin.
 */
@Composable
fun AreaMap(points: List<MapPoint>, modifier: Modifier = Modifier, description: String, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val colors = LocalMapColors.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var metersPerPx by remember { mutableDoubleStateOf(0.0) }
    // The update block runs on every recomposition; re-framing then would undo the traveler's panning.
    val shown = remember { arrayOfNulls<List<MapPoint>>(1) }
    val named = onClick == null
    val view = remember {
        AreaMaps.init(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.uiSettings.setAllGesturesEnabled(named)
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.isRotateGesturesEnabled = false
                // The scale bar sits bottom left; the data attribution (ⓘ) is what the licence asks for.
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.attributionGravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                map.setMaxZoomPreference(MAX_ZOOM)
                map.setStyle(Style.Builder().fromJson(terrainStyle(colors))) { style ->
                    style.addSource(GeoJsonSource("pins"))
                    style.addLayer(SymbolLayer("pin", "pins").withProperties(iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true)))
                    map.addOnCameraIdleListener {
                        metersPerPx = map.projection.getMetersPerPixelAtLatitude(map.cameraPosition.target?.latitude ?: 0.0) / pixelRatio
                        shown[0]?.let { pins(map, it, pixelRatio, colors, named) }
                    }
                    mapRef = map
                }
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            view.onDestroy()
        }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(colors) { TextStyle(color = colors.label, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
    Box(modifier.semantics { contentDescription = description }) {
        AndroidView({ view }, Modifier.fillMaxSize(), update = {
            val map = mapRef ?: return@AndroidView
            if (shown[0] == points) return@AndroidView
            shown[0] = points
            // Framing needs the view's size, which a just-opened dialog does not have yet.
            view.post {
                fit(map, points, rightPad = if (named) 360 else 120)
                pins(map, points, view.pixelRatio, colors, named)
            }
        })
        if (metersPerPx > 0) Canvas(Modifier.matchParentSize()) { drawScaleBar(metersPerPx / 1000, measurer, labelStyle, colors) }
        if (onClick != null) Box(Modifier.matchParentSize().clickable(onClickLabel = "Open the map full screen", onClick = onClick))
    }
}

/**
 * Pins as drawn at the current zoom: points that would overlap share one marker ("3–5", or "6×"
 * for a long list), as on the hand-drawn map. Re-run whenever the camera settles.
 */
private fun pins(map: MapLibreMap, points: List<MapPoint>, density: Float, colors: MapColors, named: Boolean) {
    val style = map.style ?: return
    val screen = points.map { map.projection.toScreenLocation(LatLng(it.lat, it.lng)).let { p -> Offset(p.x, p.y) } }
    val features = cluster(points, screen, 30f * density).map { c ->
        val first = c.points.first()
        val badge = badgeOf(c.points).let { if (it.length > 6) "${c.points.size}×" else it }
        val name = if (named && c.points.size == 1) first.name else ""
        val home = c.points.all { it.badge.toIntOrNull() == null }
        val icon = "pin:$badge:$name:$home"
        if (style.getImage(icon) == null) style.addImage(icon, pinBitmap(badge, name, if (home) colors.route else colors.marker, colors, density))
        Feature.fromGeometry(Point.fromLngLat(first.lng, first.lat)).apply { addStringProperty("icon", icon) }
    }
    style.getSourceAs<GeoJsonSource>("pins")?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** A numbered disc centred in the image, with the name (if any) to its right on a pale backing. */
private fun pinBitmap(badge: String, name: String, fill: Color, colors: MapColors, density: Float): Bitmap {
    val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * density; typeface = Typeface.DEFAULT_BOLD; color = colors.onMarker.toArgb(); textAlign = Paint.Align.CENTER
    }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * density; typeface = Typeface.DEFAULT_BOLD; color = colors.label.toArgb() }
    val r = max(11 * density, numberPaint.measureText(badge) / 2 + 5 * density)
    val outer = r + 2 * density
    val gap = 4 * density
    val nameWidth = if (name.isEmpty()) 0f else namePaint.measureText(name) + 8 * density
    val half = outer + if (nameWidth > 0) gap + nameWidth else 0f
    val bmp = Bitmap.createBitmap((2 * half).toInt() + 2, (2 * max(outer, 10 * density)).toInt() + 2, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = bmp.width / 2f
    val cy = bmp.height / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE; canvas.drawCircle(cx, cy, outer, paint)
    paint.color = fill.toArgb(); canvas.drawCircle(cx, cy, r, paint)
    canvas.drawText(badge, cx, cy - (numberPaint.ascent() + numberPaint.descent()) / 2, numberPaint)
    if (nameWidth > 0) {
        val left = cx + outer + gap
        paint.color = colors.land.copy(alpha = 0.85f).toArgb()
        canvas.drawRoundRect(left, cy - 9 * density, left + nameWidth, cy + 9 * density, 4 * density, 4 * density, paint)
        canvas.drawText(name, left + 4 * density, cy - (namePaint.ascent() + namePaint.descent()) / 2, namePaint)
    }
    return bmp
}

/** Frames every pin, leaving [rightPad] px for names; a lone pin gets the closest zoom. */
private fun fit(map: MapLibreMap, points: List<MapPoint>, rightPad: Int) {
    if (points.isEmpty()) return
    map.moveCamera(
        if (points.size == 1) CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lng), MAX_ZOOM)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lng) }).build(), 120, 120, rightPad, 120),
    )
}
