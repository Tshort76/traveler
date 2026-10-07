package dev.tlong.traveler.domain

import dev.tlong.traveler.domain.MapsLocation.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsLocationTest {

    @Test
    fun `coordinates from what Google Maps lets you copy`() {
        listOf(
            // A place link: the place's pin (!3d!4d) wins over the viewport centre (@).
            "https://www.google.com/maps/place/Caf%C3%A9+Tortoni/@-34.6100,-58.3800,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d-34.6087!4d-58.3787" to LatLng(-34.6087, -58.3787),
            "https://www.google.com/maps/@-42.7692,-65.0385,14z" to LatLng(-42.7692, -65.0385),
            "https://maps.google.com/?q=-25.6953,-54.4367" to LatLng(-25.6953, -54.4367),
            "https://www.google.com/maps/search/?api=1&query=-34.6%2C-58.38" to LatLng(-34.6, -58.38),
            "staticmap?center=43.6797%2C-116.3391" to null,
            "-34.603722, -58.381592" to LatLng(-34.603722, -58.381592),
            " (40, -105) " to LatLng(40.0, -105.0),
            "Café Tortoni, Buenos Aires" to null,
            "95.0, 10.0" to null,
            "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9+Tortoni" to null,
        ).forEach { (text, expected) -> assertEquals(text, expected, MapsLocation.coordinates(text)) }
        val dms = MapsLocation.coordinates("25°35'43.1\"S 54°34'24.6\"W")!!
        assertEquals(-25.5953, dms.lat, 1e-4)
        assertEquals(-54.5735, dms.lng, 1e-4)
    }

    @Test
    fun `the Share sheet's text yields its short link`() {
        val link = MapsLocation.firstUrl("Café Tortoni\nAv. de Mayo 825, Buenos Aires\nhttps://maps.app.goo.gl/AbC123xyz")
        assertEquals("https://maps.app.goo.gl/AbC123xyz", link)
        assertTrue(MapsLocation.isShortLink(link!!))
        assertFalse(MapsLocation.isShortLink("https://www.google.com/maps/place/x"))
    }
}
