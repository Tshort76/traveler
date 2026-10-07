package dev.tlong.traveler.ui.common

import dev.tlong.traveler.domain.MapsLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Follows a short Google Maps link one redirect at a time and returns the first coordinates in a
 * URL along the way. Null when offline or when no URL carries them. Never reads the page itself:
 * its embedded map is centred on the requester's own location, not on the place.
 */
suspend fun resolveMapsLink(link: String): MapsLocation.LatLng? = withContext(Dispatchers.IO) {
    runCatching {
        var url = link
        repeat(6) {
            MapsLocation.coordinates(url)?.let { return@runCatching it }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            val next = try {
                conn.getHeaderField("Location")?.takeIf { conn.responseCode in 300..399 }
            } finally {
                conn.disconnect()
            }
            url = URL(URL(url), next ?: return@runCatching null).toString()
        }
        MapsLocation.coordinates(url)
    }.getOrNull()
}
