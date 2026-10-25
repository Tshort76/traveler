package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Place
import java.net.URLEncoder

/**
 * Google Maps URLs (the documented "Maps URLs" API, which opens the Maps app when installed and
 * the website otherwise). Opening a place and getting directions to it are separate actions.
 */
object MapsLinks {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The best search term for a place: an explicit query, then coordinates, then name plus context. */
    fun query(place: Place?, fallbackName: String, context: String?): String = when {
        place?.query != null -> place.query
        place?.lat != null && place.lng != null -> "${place.lat},${place.lng}"
        place?.address != null -> listOfNotNull(place.name ?: fallbackName, place.address).joinToString(", ")
        else -> listOfNotNull(place?.name ?: fallbackName, context).joinToString(", ")
    }

    fun open(place: Place?, fallbackName: String, context: String?): String {
        place?.mapsUrl?.let { return it }
        val q = query(place, fallbackName, context)
        val id = place?.placeId?.let { "&query_place_id=${enc(it)}" }.orEmpty()
        return "https://www.google.com/maps/search/?api=1&query=${enc(q)}$id"
    }

    /** Google Maps takes nine stops between the ends of a route; one URL covers ten stops at most. */
    const val MAX_STOPS = 10

    /**
     * Directions through [stops] in the order given, ending at the last: from [origin] (the lodging)
     * or, without one, from wherever the phone is. Stops past [MAX_STOPS] are left out. Null with no stops.
     */
    fun route(origin: String?, stops: List<String>): String? {
        val kept = stops.take(MAX_STOPS).ifEmpty { return null }
        val from = origin?.let { "&origin=${enc(it)}" }.orEmpty()
        val via = kept.dropLast(1).takeIf { it.isNotEmpty() }?.let { w -> "&waypoints=" + w.joinToString("%7C") { enc(it) } }.orEmpty()
        return "https://www.google.com/maps/dir/?api=1$from&destination=${enc(kept.last())}$via"
    }

    /** Where a route goes: exact coordinates when known, since a name can match a place on another continent. */
    fun stop(place: Place?, fallbackName: String, context: String?): String =
        if (place?.lat != null && place.lng != null) "${place.lat},${place.lng}" else query(place, fallbackName, context)

    fun directions(place: Place?, fallbackName: String, context: String?, mode: String? = null): String {
        val q = query(place, fallbackName, context)
        val id = place?.placeId?.let { "&destination_place_id=${enc(it)}" }.orEmpty()
        val m = mode?.let { "&travelmode=$it" }.orEmpty()
        return "https://www.google.com/maps/dir/?api=1&destination=${enc(q)}$id$m"
    }
}
