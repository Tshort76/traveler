package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StayLengthTest {
    // bsas-1 5–7 Nov, iguazu 7–13 Nov, bsas-2 13–15 Nov; a booked boat on 8 Nov, a booked flight home on 15 Nov.
    private val trip = Fixtures.iguazu.normalized()

    private fun Trip.span(id: String) = stay(id)!!.let { it.arrive to it.depart }
    private fun Trip.stayOn(date: String) = day(date)!!.stayId

    @Test
    fun `a night taken from the next stay moves the transfer and the day between them, and keeps the trip's dates`() {
        val change = trip.withNights("bsas-1", 3, NightsFrom.NEXT_STAY)!!
        val t = change.trip
        assertEquals("2026-11-05" to "2026-11-08", t.span("bsas-1"))
        assertEquals("2026-11-08" to "2026-11-13", t.span("iguazu"))
        assertEquals("2026-11-08", t.transfers.single { it.id == "fly-bsas-iguazu" }.date)
        assertEquals("bsas-1", t.stayOn("2026-11-07"))
        assertTrue(t.day("2026-11-07")!!.plan.all { t.activityOrWork(it.activityId)?.stayId == "bsas-1" })
        assertEquals(trip.day("2026-11-07")!!.plan.count { trip.activity(it.activityId)?.stayId == "iguazu" }, change.unscheduled)
        assertEquals(trip.startDate to trip.endDate, t.startDate to t.endDate)
        assertTrue("depart" in t.stay("bsas-1")!!.userEdited && "arrive" in t.stay("iguazu")!!.userEdited)
    }

    @Test
    fun `a night added before the later stays shifts them, the trip's end and what is booked after, and says so`() {
        val change = trip.withNights("iguazu", 7, NightsFrom.LATER_STAYS)!!
        val t = change.trip
        assertEquals("2026-11-07" to "2026-11-14", t.span("iguazu"))
        assertEquals("2026-11-14" to "2026-11-16", t.span("bsas-2"))
        assertEquals("2026-11-16", t.endDate)
        assertEquals("2026-11-14", t.transfers.single { it.id == "fly-iguazu-bsas" }.date)
        assertEquals("2026-11-16", t.commitments.single { it.id == "flight-home" }.date)
        assertEquals("2026-11-08", t.commitments.single { it.id == "boat-gran-aventura" }.date)
        assertEquals(trip.day("2026-11-13")!!.plan, t.day("2026-11-14")!!.plan)
        assertEquals("iguazu", t.stayOn("2026-11-13"))
        assertEquals(0, change.unscheduled)
        assertEquals(1, change.booked.size)
        assertTrue(change.booked.single().startsWith(t.commitments.single { it.id == "flight-home" }.title))
    }

    @Test
    fun `a night given up with the later stays drops the stay's last day and its plans`() {
        val change = trip.withNights("iguazu", 5, NightsFrom.LATER_STAYS)!!
        assertEquals("2026-11-14", change.trip.endDate)
        assertEquals(trip.day("2026-11-12")!!.plan.size, change.unscheduled)
        assertEquals(trip.day("2026-11-13")!!.plan, change.trip.day("2026-11-12")!!.plan)
    }

    @Test
    fun `no change leaves a stay under one night`() {
        assertNull(trip.withNights("iguazu", 8, NightsFrom.NEXT_STAY))  // bsas-2 would have none
        assertNull(trip.withNights("iguazu", 0, NightsFrom.LATER_STAYS))
        assertNull(trip.withNights("bsas-2", 3, NightsFrom.NEXT_STAY))  // nothing after it
    }
}
