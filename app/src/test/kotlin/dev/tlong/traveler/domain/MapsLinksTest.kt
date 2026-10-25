package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsLinksTest {
    private val base = "https://www.google.com/maps/dir/?api=1"

    @Test
    fun `a day's route runs from the lodging through its stops in order, and keeps the first ten`() {
        val stops = (1..12).map { "Stop $it" }
        listOf(
            Triple("Hotel Uno", listOf("Museo, Palermo"), "$base&origin=Hotel+Uno&destination=Museo%2C+Palermo"),
            Triple(null, listOf("A", "B", "C"), "$base&destination=C&waypoints=A%7CB"),
            Triple(null, stops, "$base&destination=Stop+10&waypoints=" + (1..9).joinToString("%7C") { "Stop+$it" }),
        ).forEach { (origin, list, url) -> assertEquals(url, MapsLinks.route(origin, list)) }
        assertNull(MapsLinks.route("Hotel Uno", emptyList()))
    }

    @Test
    fun `a stop is its coordinates when it has them, its search otherwise`() {
        assertEquals("-34.5875,-58.43", MapsLinks.stop(Place(query = "Palermo Soho Loft", lat = -34.5875, lng = -58.43), "Loft", null))
        assertEquals("Palermo Soho Loft", MapsLinks.stop(Place(query = "Palermo Soho Loft"), "Loft", null))
    }
}
