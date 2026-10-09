package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.WorkRhythm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarTest {

    @Test
    fun `zero-night stays opening and closing the trip are home, not destinations`() {
        val trip = Fixtures.iguazu
        val out = Stay("home-out", "Denver", arrive = "2026-11-04", depart = "2026-11-04")
        val back = Stay("home-back", "Denver", arrive = "2026-11-16", depart = "2026-11-16")
        val withHome = trip.copy(stays = listOf(out) + trip.stays + back)
        assertEquals(trip.stays.map { it.id }, withHome.destinations.map { it.id })
        // A zero-night stop mid-trip is a destination; so is a lone stay.
        val midStop = trip.copy(stays = trip.stays.take(1) + out.copy(id = "stop") + trip.stays.drop(1))
        assertEquals(midStop.stays.map { it.id }, midStop.destinations.map { it.id })
        assertEquals(listOf("home-out"), trip.copy(stays = listOf(out)).destinations.map { it.id })
    }

    @Test
    fun `each date belongs to the right stay, travel days to the stay arrived at`() {
        val trip = Fixtures.iguazu
        val cases = mapOf(
            "2026-11-05" to "bsas-1", "2026-11-06" to "bsas-1", "2026-11-07" to "iguazu",
            "2026-11-12" to "iguazu", "2026-11-13" to "bsas-2", "2026-11-15" to "bsas-2",
        )
        cases.forEach { (d, stay) -> assertEquals(d, stay, trip.stayFor(LocalDate.parse(d))?.id) }
    }

    @Test
    fun `normalizing gives every date a day and compact removes the added ones`() {
        val trip = Fixtures.trip("minimal.trip.json")
        val n = trip.normalized()
        assertEquals(listOf("2026-10-10", "2026-10-11"), n.days.map { it.date })
        assertTrue(n.days.all { it.stayId == "denver" })
        assertEquals(trip.days, n.compact().days)
    }

    @Test
    fun `a stay's dates follow its days, so an evening-flight day stays put`() {
        val trip = Fixtures.iguazu.normalized()
        assertEquals((7..13).map { "2026-11-%02d".format(it) }, trip.datesOf(trip.stay("iguazu")!!).map { it.toString() })
    }

    private fun stay(zone: String, rhythm: WorkRhythm) =
        Stay(id = "s", name = "S", arrive = "2026-01-01", depart = "2026-12-31", timezone = zone, workRhythm = rhythm)

    @Test
    fun `work hours kept in a home zone convert per date across its clock change`() {
        // Denver leaves daylight time on 1 Nov 2026; Buenos Aires never changes its clocks.
        val s = stay("America/Argentina/Buenos_Aires", WorkRhythm(start = "08:00", end = "15:00", timezone = "America/Denver"))
        val cases = mapOf(
            "2026-10-30" to (LocalTime.of(11, 0) to LocalTime.of(18, 0)),
            "2026-11-02" to (LocalTime.of(12, 0) to LocalTime.of(19, 0)),
        )
        cases.forEach { (d, hours) ->
            val w = workHoursOn(s, LocalDate.parse(d))!!
            assertEquals(d, hours, w.start to w.end)
            assertTrue(w.sourceLabel!!.contains("Denver"))
        }
    }

    @Test
    fun `work hours follow the rhythm's weekdays`() {
        val s = stay("America/Denver", WorkRhythm(days = listOf("mon", "tue"), start = "08:00", end = "15:00"))
        assertEquals(LocalTime.of(8, 0), workHoursOn(s, LocalDate.parse("2026-11-09"))?.start) // Monday
        assertNull(workHoursOn(s, LocalDate.parse("2026-11-11"))) // Wednesday
    }
}
