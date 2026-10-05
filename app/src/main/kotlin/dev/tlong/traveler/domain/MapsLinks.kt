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

    fun directions(place: Place?, fallbackName: String, context: String?, mode: String? = null): String {
        val q = query(place, fallbackName, context)
        val id = place?.placeId?.let { "&destination_place_id=${enc(it)}" }.orEmpty()
        val m = mode?.let { "&travelmode=$it" }.orEmpty()
        return "https://www.google.com/maps/dir/?api=1&destination=${enc(q)}$id$m"
    }
}
