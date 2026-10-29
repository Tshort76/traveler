package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SlotSuggestionsTest {
    private val trip = TripReader.read(Fixtures.text("demo.trip.json")).trip!!.normalized()
    private val pool = trip.activities.filter { it.stayId == "san-telmo" } + trip.activity("barrancas-belgrano")!!

    private fun groups(free: Int?) = suggestFor(trip, pool, LocalDate.parse("2027-10-21"), Slot.AFTERNOON, LocalTime.of(14, 0), free, work = emptyList())
        .groupBy({ it.group }, { it.activity.id to it.reason })

    @Test
    fun `groups fits, other ideas with a reason, planned or done, and won't do`() {
        val g = groups(free = 180)
        assertEquals(listOf("caminito" to null), g[SlotFit.Group.FITS])
        assertEquals(listOf("tango-show" to "An evening outing"), g[SlotFit.Group.OTHER])
        assertEquals("done", g[SlotFit.Group.PLANNED]!!.toMap()["mercado-san-telmo"])
        assertEquals(listOf("barrancas-belgrano" to null), g[SlotFit.Group.WONT_DO])
    }

    @Test
    fun `an activity longer than the free gap is not a fit`() {
        assertEquals("Takes 2 h 30; 1 h free", groups(free = 60)[SlotFit.Group.OTHER]!!.toMap()["caminito"])
    }
}
