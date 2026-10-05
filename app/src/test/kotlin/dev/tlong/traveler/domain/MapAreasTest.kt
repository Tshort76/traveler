package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreasTest {
    private val trip = Fixtures.iguazu.normalized()

    @Test
    fun `each stay's area holds all its activities with a margin`() {
        val areas = trip.mapAreas(marginKm = 2.0).associateBy { it.stayId }
        trip.activities.filter { it.place?.hasCoordinates == true }.forEach { a ->
            val area = areas.getValue(a.stayId)
            assertTrue(a.id, a.place!!.lat!! in area.south + 0.017..area.north - 0.017)
            assertTrue(a.id, a.place.lng!! in area.west + 0.017..area.east - 0.017)
        }
    }

    @Test
    fun `a stay with no coordinates anywhere has no area`() {
        val bare = trip.copy(
            stays = trip.stays.map { it.copy(place = null, lodging = it.lodging?.copy(place = null)) },
            activities = trip.activities.map { if (it.stayId == "iguazu") it.copy(place = Place(name = "x")) else it },
        )
        assertEquals(trip.stays.map { it.id } - "iguazu", bare.mapAreas().map { it.stayId })
    }
}
