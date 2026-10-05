package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingsTest {
    private val trip = Fixtures.iguazu.normalized()
    private val items = trip.bookables()
    private fun item(key: String) = items.single { it.key == key }

    @Test
    fun `lodging, ticketed transfers, suggestions needing a booking and bookings are listed once each`() {
        assertEquals(
            listOf("lodging:bsas-1", "activity:don-julio", "lodging:iguazu", "transfer:fly-bsas-iguazu", "activity:garganta-del-diablo",
                "activity:gran-aventura", "lodging:bsas-2", "transfer:fly-iguazu-bsas", "commitment:flight-home"),
            items.map { it.key },
        )
    }

    @Test
    fun `a suggestion booked through a commitment is one booked item carrying the booking's time and price`() {
        val paid = trip.copy(commitments = trip.commitments.map { if (it.id == "boat-gran-aventura") it.copy(price = Price(95.0, currency = "USD")) else it })
        val boat = paid.bookables().single { it.key == "activity:gran-aventura" }
        assertEquals(95.0, boat.price?.amount)
        assertTrue(boat.booked)
        assertEquals("2026-11-08" to "14:00", boat.date to boat.time)
        assertEquals("boat-gran-aventura", boat.record?.id)
    }

    @Test
    fun `an other-mode transfer shows the first vehicle its details name`() {
        fun shown(mode: String?, details: String?) = Transfer("t", "a", "b", "2026-11-08", mode = mode, details = details).shownMode
        assertEquals("bus", shown("other", "Park bus to Pudeto, then boat to Paine Grande"))
        assertEquals("hike", shown("other", "Travel to Villa Catedral, then hike to Frey"))
        assertEquals("taxi", shown("other", "Remis from the airport"))
        assertEquals("hike", shown("other", "Descend to Villa Catedral and return to town"))
        assertEquals("other", shown("other", "Return to town"))
        assertEquals("flight", shown("flight", "then the bus to El Chaltén"))
    }

    @Test
    fun `an activity needing a booking is unplanned until it is on a day`() {
        assertFalse(item("activity:don-julio").unplanned)
        val dropped = trip.copy(days = trip.days.map { d -> d.copy(plan = d.plan.filterNot { it.activityId == "don-julio" }) })
        val julio = dropped.bookables().single { it.key == "activity:don-julio" }
        assertTrue(julio.unplanned)
        assertEquals(null, julio.date)
    }

    @Test
    fun `book ahead lists open P1s, planned or not, but not an activity only skipped`() {
        assertEquals(listOf("lodging:iguazu", "transfer:fly-bsas-iguazu"), trip.bookAhead().map { it.key })
        val p1 = trip.copy(activities = trip.activities.map { a -> if (a.id == "garganta-del-diablo") a.copy(booking = a.booking?.copy(priority = 1)) else a })
        assertTrue("activity:garganta-del-diablo" in p1.bookAhead().map { it.key })
        val skipped = p1.copy(days = p1.days.map { d -> d.copy(plan = d.plan.map { if (it.activityId == "garganta-del-diablo") it.copy(status = "skipped") else it }) })
        assertFalse("activity:garganta-del-diablo" in skipped.bookAhead().map { it.key })
    }

    @Test
    fun `an activity counts as booked through its booked commitment`() {
        assertTrue(trip.isBooked(trip.activity("gran-aventura")!!))
        assertFalse(trip.isBooked(trip.activity("garganta-del-diablo")!!))
    }

    @Test
    fun `totals sum the estimate range and what is booked`() {
        val t = items.totals().single()
        assertEquals(listOf(1875.0, 2395.0, 750.0), listOf(t.estimateLow, t.estimateHigh, t.booked))
        assertEquals("$1,875–2,395", t.estimate)
    }

    @Test
    fun `a day counts lodging on check-in and transfers on their date`() {
        assertEquals(setOf("lodging:iguazu", "transfer:fly-bsas-iguazu"), trip.bookablesOn("2026-11-07").map { it.key }.toSet())
    }

    @Test
    fun `marking lodging booked records the details as the traveler's edit`() {
        val paid = Price(510.0, currency = "USD")
        val t = Edits.markBooked(trip, item("lodging:iguazu"), Edits.Booked("Hotel Cataratas", "AB12", null, paid, null, "14:00", null))
        val stay = t.stay("iguazu")!!
        assertEquals(listOf("booked", "Hotel Cataratas", "AB12", "14:00"), listOf(stay.lodging?.status, stay.lodging?.name, stay.lodging?.ref, stay.lodging?.checkIn))
        assertEquals(paid, stay.lodging?.price)
        assertTrue("lodging" in stay.userEdited)
        assertTrue(t.bookables().single { it.key == "lodging:iguazu" }.booked)
    }

    @Test
    fun `unbooking a booked suggestion unbooks its booking record`() {
        val t = Edits.markNotBooked(trip, item("activity:gran-aventura"))
        assertFalse(t.bookables().single { it.key == "activity:gran-aventura" }.booked)
        assertFalse(t.commitments.single { it.id == "boat-gran-aventura" }.isBooked)
    }

    @Test
    fun `unbooking a transfer sets it back to needed`() {
        val booked = Edits.markBooked(trip, item("transfer:fly-bsas-iguazu"), Edits.Booked(null, "XY9", null, null, null, "09:10", "11:00"))
        val tr = booked.transfers.single { it.id == "fly-bsas-iguazu" }
        assertEquals(listOf("booked", "XY9", "09:10", "11:00"), listOf(tr.booking?.status, tr.booking?.ref, tr.depart, tr.arrive))
        val t = Edits.markNotBooked(booked, booked.bookables().single { it.key == "transfer:fly-bsas-iguazu" })
        assertEquals("needed", t.transfers.single { it.id == "fly-bsas-iguazu" }.booking?.status)
    }

    @Test
    fun `totals are kept apart per currency, and a price without one counts as USD`() {
        val priced = listOf(Price(85000.0, currency = "ARS"), Price(10.0), Price(5.0, currency = "USD"))
            .mapIndexed { i, p -> Bookable("commitment:$i", Bookable.Kind.BOOKING, "x", null, booked = false, price = p) }
        assertEquals(mapOf("ARS" to 85000.0, "USD" to 15.0), priced.totals().associate { it.currency to it.estimateLow })
    }

    @Test
    fun `the plan's priority and booking advice reach the item`() {
        val b = item("transfer:fly-bsas-iguazu")
        assertEquals(1 to "Aerolíneas Argentinas or Flybondi; fares rise inside four weeks.", b.priority to b.how)
        assertEquals(listOf(2, 1, 2, 3), listOf("lodging:bsas-1", "lodging:iguazu", "activity:don-julio", "activity:garganta-del-diablo").map { item(it).priority })
    }

    @Test
    fun `a suggestion's own booking advice wins, and without it the plan's booking note is used`() {
        fun how(own: String?) = trip.copy(activities = trip.activities.map {
            if (it.id == "gran-aventura") it.copy(booking = it.booking?.copy(how = own)) else it
        }).bookables().single { it.key == "activity:gran-aventura" }.how
        assertEquals("Booked — see the commitment for the time.", how(null))
        assertEquals("Reserve at the stand", how("Reserve at the stand"))
    }

    @Test
    fun `prices read as money, with ranges sharing the symbol`() {
        assertEquals("$120–180", Price(120.0, 180.0, "USD").label())
        assertEquals("$45.50", Price(45.5).label())
        assertEquals("ARS 85,000", money(85000.0, "ARS"))
    }
}
