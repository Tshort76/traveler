package dev.tlong.traveler.ui.map

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.maplibre.android.geometry.LatLng
import dev.tlong.traveler.ui.theme.LocalMapColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A numbered place on the map. Several points at one spot (a repeat visit) share a marker. */
@Immutable
data class MapPoint(val lat: Double, val lng: Double, val badge: String, val name: String)

/** A transfer between two points, by index; drawn as a dashed arc so it never reads as a road. */
@Immutable
data class MapSegment(val from: Int, val to: Int)

/** Country outlines bundled as assets/basemap/countries.txt (Natural Earth, public domain). */
object Basemap {
    class Ring(val xs: FloatArray, val ys: FloatArray, val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

    @Volatile private var cache: List<Ring>? = null

    suspend fun load(context: Context): List<Ring> = cache ?: withContext(Dispatchers.Default) {
        val rings = context.assets.open("basemap/countries.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val parts = line.split(' ')
                val xs = FloatArray(parts.size)
                val ys = FloatArray(parts.size)
                parts.forEachIndexed { i, p ->
                    val c = p.indexOf(',')
                    xs[i] = mercX(p.substring(0, c).toDouble())
                    ys[i] = mercY(p.substring(c + 1).toDouble())
                }
                Ring(xs, ys, xs.min(), xs.max(), ys.min(), ys.max())
            }.toList()
        }
        cache = rings
        rings
    }
}

// Web Mercator in "world units": x and y both span 0..1 across the whole world.
fun mercX(lng: Double) = ((lng + 180.0) / 360.0).toFloat()
fun mercY(lat: Double): Float {
    val l = lat.coerceIn(-85.0, 85.0) * PI / 180.0
    return ((1.0 - ln(tan(PI / 4 + l / 2)) / PI) / 2.0).toFloat()
}

/** Great-circle distance in km, for the accessible description and the scale bar. */
fun distanceKm(a: MapPoint, b: MapPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * The map that answers "where are my destinations relative to one another, and in what order?".
 * Drawn entirely from bundled data, so it works in airplane mode. [interactive] adds pinch-zoom
 * and pan for the full-screen view. With a [backdrop] (a terrain picture framed exactly as this
 * canvas frames the points, see [snapshotCamera]) that replaces the country outlines.
 */
@Composable
fun TripMapCanvas(
    points: List<MapPoint>,
    segments: List<MapSegment>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    description: String,
    backdrop: ImageBitmap? = null,
) {
    val context = LocalContext.current
    val colors = LocalMapColors.current
    val measurer = rememberTextMeasurer()
    val rings by produceState<List<Basemap.Ring>>(emptyList()) { value = Basemap.load(context) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { z, p, _ ->
        zoom = (zoom * z).coerceIn(1f, 12f)
        pan += p
    }
    val labelStyle = remember(colors) { TextStyle(color = colors.label, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
    val badgeStyle = remember(colors) { TextStyle(color = colors.onMarker, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

    val mod = modifier.semantics { contentDescription = description }
        .let { if (interactive) it.transformable(transform) else it }

    Canvas(mod) {
        drawRect(colors.water)
        if (points.isEmpty()) return@Canvas
        val view = fitView(points, size.width, size.height, 36f * density)
        withTransform({
            translate(pan.x, pan.y)
            scale(zoom, zoom, Offset(size.width / 2, size.height / 2))
        }) {
            if (backdrop != null) drawImage(backdrop, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            else drawBasemap(rings, view, colors.land, colors.border, 1f / zoom)
            val screen = points.map { view.toScreen(mercX(it.lng), mercY(it.lat)) }
            segments.forEach { s -> drawTransfer(screen[s.from], screen[s.to], colors.route, 1f / zoom) }
        }
        // Markers and labels are drawn unscaled so they stay legible at any zoom. Points that would
        // overlap on screen share one marker ("3–4", "1,14"); every marker is an obstacle for labels,
        // and a label that cannot be placed clear of everything is left out — the ordered list
        // under the map names every stop anyway.
        val screen = points.map { pt ->
            view.toScreen(mercX(pt.lng), mercY(pt.lat)).let {
                Offset((it.x - size.width / 2) * zoom + size.width / 2 + pan.x, (it.y - size.height / 2) * zoom + size.height / 2 + pan.y)
            }
        }
        val clusters = cluster(points, screen, 24f * density)
        // Every marker is drawn before any label, so labels can avoid all of them.
        val placed = clusters.mapTo(mutableListOf()) { c -> drawMarker(c.at, badgeOf(c.points), measurer, badgeStyle, colors.marker) }
        clusters.forEach { c ->
            drawLabel(c.at, c.points.map { it.name }.distinct().joinToString(" · "), measurer, labelStyle, colors, placed)
        }
    }
}

internal class Cluster(val at: Offset, val points: MutableList<MapPoint>)

internal fun cluster(points: List<MapPoint>, screen: List<Offset>, radius: Float): List<Cluster> {
    val out = mutableListOf<Cluster>()
    points.forEachIndexed { i, p ->
        val near = out.firstOrNull { (it.at - screen[i]).getDistance() < radius }
        if (near != null) near.points += p else out += Cluster(screen[i], mutableListOf(p))
    }
    return out
}

/** "3", "1,14", or "3–5" for a run of consecutive stops. */
internal fun badgeOf(points: List<MapPoint>): String {
    val nums = points.mapNotNull { it.badge.toIntOrNull() }
    if (nums.size != points.size) return points.joinToString(",") { it.badge }
    val sorted = nums.distinct().sorted()
    val runs = mutableListOf<IntRange>()
    sorted.forEach { n -> if (runs.isNotEmpty() && runs.last().last == n - 1) runs[runs.lastIndex] = runs.last().first..n else runs += n..n }
    return runs.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
}

private class View(val minX: Float, val minY: Float, val scale: Float, val offX: Float, val offY: Float) {
    fun toScreen(x: Float, y: Float) = Offset((x - minX) * scale + offX, (y - minY) * scale + offY)
}


/**
 * Where a map renderer must look, and how close, to draw exactly what [TripMapCanvas] frames on a
 * canvas of this size: centre and zoom from [fitView], with MapLibre's 512-unit world at zoom 0.
 */
internal fun snapshotCamera(points: List<MapPoint>, widthPx: Float, heightPx: Float, density: Float): Pair<LatLng, Double> {
    val v = fitView(points, widthPx, heightPx, 36f * density)
    val x = v.minX + (widthPx / 2 - v.offX) / v.scale
    val y = v.minY + (heightPx / 2 - v.offY) / v.scale
    val lat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * y))))
    return LatLng(lat, x * 360.0 - 180.0) to kotlin.math.log2(v.scale / (512.0 * density))
}

private fun fitView(points: List<MapPoint>, w: Float, h: Float, padPx: Float): View {
    val xs = points.map { mercX(it.lng) }
    val ys = points.map { mercY(it.lat) }
    var minX = xs.min(); var maxX = xs.max(); var minY = ys.min(); var maxY = ys.max()
    // Never zoom in further than ~40 km across, so one stay still shows its region.
    val minSpan = 0.0012f
    if (maxX - minX < minSpan) { val c = (minX + maxX) / 2; minX = c - minSpan / 2; maxX = c + minSpan / 2 }
    if (maxY - minY < minSpan) { val c = (minY + maxY) / 2; minY = c - minSpan / 2; maxY = c + minSpan / 2 }
    val pad = padPx * 1.6f
    val scale = minOf((w - 2 * pad) / (maxX - minX), (h - 2 * pad) / (maxY - minY))
    val offX = (w - (maxX - minX) * scale) / 2
    val offY = (h - (maxY - minY) * scale) / 2
    return View(minX, minY, scale, offX, offY)
}

private fun DrawScope.drawBasemap(rings: List<Basemap.Ring>, v: View, land: Color, border: Color, hairline: Float) {
    val vx0 = v.minX - v.offX / v.scale
    val vy0 = v.minY - v.offY / v.scale
    val vx1 = vx0 + size.width / v.scale
    val vy1 = vy0 + size.height / v.scale
    val path = Path()
    rings.forEach { r ->
        if (r.maxX < vx0 || r.minX > vx1 || r.maxY < vy0 || r.minY > vy1) return@forEach
        val first = v.toScreen(r.xs[0], r.ys[0])
        path.moveTo(first.x, first.y)
        for (i in 1 until r.xs.size) {
            val p = v.toScreen(r.xs[i], r.ys[i])
            path.lineTo(p.x, p.y)
        }
        path.close()
    }
    clipRect {
        drawPath(path, land)
        drawPath(path, border, style = Stroke(width = 1.2f * hairline * density))
    }
}

private fun DrawScope.drawTransfer(a: Offset, b: Offset, color: Color, k: Float) {
    if ((a - b).getDistance() < 2f) return
    // A gentle arc, bowed to one side, reads as "travel between" rather than "this road".
    val mid = (a + b) / 2f
    val d = b - a
    val normal = Offset(-d.y, d.x) / d.getDistance()
    val ctrl = mid + normal * (d.getDistance() * 0.18f)
    val path = Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
    drawPath(
        path, color,
        style = Stroke(width = 2.5f * density * k, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density * k, 7f * density * k))),
    )
    val t = 0.5f
    val pt = Offset((1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * ctrl.x + t * t * b.x, (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * ctrl.y + t * t * b.y)
    val tangent = Offset(2 * (1 - t) * (ctrl.x - a.x) + 2 * t * (b.x - ctrl.x), 2 * (1 - t) * (ctrl.y - a.y) + 2 * t * (b.y - ctrl.y))
    val len = tangent.getDistance().takeIf { it > 0 } ?: return
    val u = tangent / len
    val n = Offset(-u.y, u.x)
    val s = 7f * density * k
    val head = Path().apply {
        moveTo(pt.x + u.x * s, pt.y + u.y * s)
        lineTo(pt.x - u.x * s + n.x * s * 0.8f, pt.y - u.y * s + n.y * s * 0.8f)
        lineTo(pt.x - u.x * s - n.x * s * 0.8f, pt.y - u.y * s - n.y * s * 0.8f)
        close()
    }
    drawPath(head, color)
}

/** Draws a numbered marker and returns the area it covers, for label placement. */
private fun DrawScope.drawMarker(p: Offset, badge: String, measurer: TextMeasurer, style: TextStyle, color: Color): Rect {
    val text = measurer.measure(badge, style)
    val r = max(11f * density, text.size.width / 2f + 5f * density)
    val outer = r + 2f * density
    drawCircle(Color.White, outer, p)
    drawCircle(color, r, p)
    drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
    return Rect(p.x - outer, p.y - outer, p.x + outer, p.y + outer)
}

private fun DrawScope.drawLabel(p: Offset, name: String, measurer: TextMeasurer, style: TextStyle, colors: dev.tlong.traveler.ui.theme.MapColors, placed: MutableList<Rect>) {
    val text = measurer.measure(name, style)
    val gap = 16f * density
    val w = text.size.width.toFloat()
    val h = text.size.height.toFloat()
    val candidates = listOf(
        Offset(p.x + gap, p.y - h / 2), Offset(p.x - gap - w, p.y - h / 2),
        Offset(p.x - w / 2, p.y + gap), Offset(p.x - w / 2, p.y - gap - h),
    )
    val spot = candidates.firstOrNull { c ->
        val r = Rect(c, androidx.compose.ui.geometry.Size(w, h))
        r.left >= 0 && r.right <= size.width && r.top >= 0 && r.bottom <= size.height && placed.none { it.overlaps(r) }
    } ?: return
    val rect = Rect(spot, androidx.compose.ui.geometry.Size(w, h))
    placed += rect
    drawRoundRect(
        colors.land.copy(alpha = 0.85f), topLeft = Offset(rect.left - 3 * density, rect.top - 1 * density),
        size = androidx.compose.ui.geometry.Size(w + 6 * density, h + 2 * density),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * density),
    )
    drawText(text, topLeft = spot)
}

/** A bar of a round distance no wider than a quarter of the map, bottom left: miles where the phone's country uses them. */
internal fun DrawScope.drawScaleBar(kmPerPx: Double, measurer: TextMeasurer, style: TextStyle, colors: dev.tlong.traveler.ui.theme.MapColors) {
    val miles = java.util.Locale.getDefault().country in setOf("US", "LR", "MM")
    val unitKm = if (miles) 1.609344 else 1.0
    val maxUnits = kmPerPx * size.width / 4 / unitKm
    if (maxUnits <= 0 || maxUnits.isNaN()) return
    val step = 10.0.pow(kotlin.math.floor(kotlin.math.log10(maxUnits)))
    val units = (listOf(5.0, 2.5, 2.0, 1.0).map { it * step } + listOf(0.25, 0.5).filter { miles }).sortedDescending().first { it <= maxUnits || it == step }
    val len = (units * unitKm / kmPerPx).toFloat()
    val text = when {
        miles && units == 0.25 -> "¼ mi"
        miles && units == 0.5 -> "½ mi"
        miles -> "${units.toBigDecimal().stripTrailingZeros().toPlainString()} mi"
        units < 1 -> "${(units * 1000).toInt()} m"
        else -> "${units.toBigDecimal().stripTrailingZeros().toPlainString()} km"
    }
    val label = measurer.measure(text, style)
    val x = 12f * density
    val y = size.height - 12f * density
    drawRoundRect(
        colors.land.copy(alpha = 0.85f), topLeft = Offset(x - 4 * density, y - label.size.height - 8 * density),
        size = androidx.compose.ui.geometry.Size(max(len, label.size.width.toFloat()) + 8 * density, label.size.height + 12 * density),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * density),
    )
    drawLine(colors.label, Offset(x, y), Offset(x + len, y), strokeWidth = 2f * density)
    listOf(x, x + len).forEach { drawLine(colors.label, Offset(it, y), Offset(it, y - 5 * density), strokeWidth = 2f * density) }
    drawText(label, topLeft = Offset(x, y - label.size.height - 6 * density))
}
