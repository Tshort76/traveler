package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Place
import dev.tlong.traveler.model.Trip
import kotlin.math.cos

/** The ground a stay's map needs: every activity, the lodging and the stay itself, plus a margin. */
data class MapArea(val stayId: String, val south: Double, val west: Double, val north: Double, val east: Double)

fun Trip.mapAreas(marginKm: Double = 2.0): List<MapArea> = destinations.mapNotNull { s ->
    val places = activities.filter { it.stayId == s.id }.map { it.place } + s.lodging?.place + s.place
    val points = places.filterNotNull().filter(Place::hasCoordinates)
    if (points.isEmpty()) return@mapNotNull null
    val south = points.minOf { it.lat!! }
    val north = points.maxOf { it.lat!! }
    val dLat = marginKm / 111.0
    val dLng = marginKm / (111.0 * cos(Math.toRadians((south + north) / 2)))
    MapArea(s.id, south - dLat, points.minOf { it.lng!! } - dLng, north + dLat, points.maxOf { it.lng!! } + dLng)
}
