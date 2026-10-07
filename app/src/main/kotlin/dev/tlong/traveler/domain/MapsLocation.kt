package dev.tlong.traveler.domain

import java.net.URLDecoder

/**
 * Coordinates from what Google Maps lets the traveler copy: a shared place link (the text the
 * Share sheet produces, link and all), a full maps URL, or coordinates copied from a dropped pin.
 * A short `maps.app.goo.gl` link carries none until it is followed; [isShortLink] says when the
 * caller has to resolve it first and pass the resolved URL, or the page, back in.
 */
object MapsLocation {
    data class LatLng(val lat: Double, val lng: Double)

    private const val NUM = """(-?\d{1,3}(?:\.\d+)?)"""

    // In order of trust: the place's own pin, then an explicit point, then the viewport centre.
    private val patterns = listOf(
        Regex("""!3d$NUM!4d$NUM"""),
        Regex("""[?&](?:q|query|ll|destination)=(?:loc:)?$NUM\s*,\s*$NUM"""),
        Regex("""@$NUM,$NUM"""),
    )
    private val bare = Regex("""^\s*\(?$NUM\s*,\s*$NUM\)?\s*$""")
    private val url = Regex("""https?://\S+""")
    // How Google Maps labels a dropped pin: 25°35'43.1"S 54°34'24.6"W
    private val dms = Regex("""(\d{1,2})°\s*(\d{1,2})['′]\s*([\d.]+)["″]?\s*([NS])[\s,]*(\d{1,3})°\s*(\d{1,2})['′]\s*([\d.]+)["″]?\s*([EW])""")

    fun firstUrl(text: String): String? = url.find(text)?.value?.trimEnd('.', ',', ')')

    fun isShortLink(link: String): Boolean =
        Regex("""^https?://(maps\.app\.goo\.gl|goo\.gl/maps)/""").containsMatchIn(link)

    /** The coordinates in [text], or null when it holds none that are on the globe. */
    fun coordinates(text: String): LatLng? {
        bare.find(text)?.let { m -> return valid(m) }
        dms.find(text)?.let { m ->
            val g = m.groupValues
            fun deg(d: String, mi: String, s: String, hemi: String) =
                (d.toDouble() + mi.toDouble() / 60 + s.toDouble() / 3600) * (if (hemi == "S" || hemi == "W") -1 else 1)
            val at = LatLng(deg(g[1], g[2], g[3], g[4]), deg(g[5], g[6], g[7], g[8]))
            return at.takeIf { it.lat in -90.0..90.0 && it.lng in -180.0..180.0 }
        }
        val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        for (p in patterns) {
            p.findAll(decoded).forEach { m -> valid(m)?.let { return it } }
        }
        return null
    }

    private fun valid(m: MatchResult): LatLng? {
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        return LatLng(lat, lng).takeIf { lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0) }
    }
}
