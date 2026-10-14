package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookingsTest {
    private val longBefore = LocalDate.parse("2026-01-01")
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
    fun `booked out of planned, per stay, split into logistics and events`() {
        val L = Bookable.Group.LOGISTICS
        val E = Bookable.Group.EVENTS
        assertEquals(
            mapOf(
                "bsas-1" to mapOf(L to (0 to 1), E to (0 to 1)),
                "iguazu" to mapOf(L to (0 to 2), E to (1 to 2)),
                "bsas-2" to mapOf(L to (1 to 3)),
            ),
            items.tallyByStay(),
        )
    }

    @Test
    fun `the flight home counts toward the stay it leaves, not the hidden home airport`() {
        val demo = Fixtures.trip("demo.trip.json").normalized().bookables()
        assertEquals("madryn", demo.single { it.key == "transfer:fly-home" }.stayId)
        assertEquals("palermo", demo.single { it.key == "transfer:fly-out" }.stayId)
    }

    @Test
    fun `book-by counts back from the item's date, with an old priority standing in`() {
        listOf(
            Triple("2026-11-07", 21, null) to "2026-10-17",
            Triple("2026-11-07", 0, 1) to "2026-11-07",
            Triple("2026-11-07", null, 1) to LocalDate.MIN.toString(),
            Triple("2026-11-07", null, 2) to "2026-10-24",
            Triple("2026-11-07", null, 3) to "2026-11-06",
            Triple("2026-11-07", null, null) to null,
            Triple(null, 21, null) to null,
        ).forEach { (input, expected) -> assertEquals(input.toString(), expected, bookBy(input.first, input.second, input.third)?.toString()) }
    }

    @Test
    fun `an activity's book-by date moves with its planned day`() {
        val at = trip.placementsOf("garganta-del-diablo").first()
        assertEquals(at.date.toDate()!!.minusDays(1), item("activity:garganta-del-diablo").bookBy)
        val later = at.date.toDate()!!.plusDays(2).toString()
        val moved = Edits.move(trip, Edits.ItemRef(at.date, at.index), later, Slot.MORNING)
        assertEquals(later.toDate()!!.minusDays(1), moved.bookables().single { it.key == "activity:garganta-del-diablo" }.bookBy)
    }

    @Test
    fun `due to book is what falls within a week of its book-by date, but not booked or only skipped`() {
        val week = setOf("lodging:bsas-1", "lodging:iguazu", "transfer:fly-bsas-iguazu", "lodging:bsas-2", "transfer:fly-iguazu-bsas")
        assertEquals(week, trip.dueToBook(LocalDate.parse("2026-10-22")).map { it.key }.toSet())
        assertEquals(week + "activity:don-julio", trip.dueToBook(LocalDate.parse("2026-10-23")).map { it.key }.toSet())
        val booked = trip.copy(stays = trip.stays.map { if (it.id == "iguazu") it.copy(lodging = it.lodging?.copy(status = "booked")) else it })
        assertFalse("lodging:iguazu" in booked.dueToBook(LocalDate.parse("2026-10-22")).map { it.key })
        val dayBefore = trip.placementsOf("garganta-del-diablo").first().date.toDate()!!.minusDays(1)
        assertTrue("activity:garganta-del-diablo" in trip.dueToBook(dayBefore).map { it.key })
        val skipped = trip.copy(days = trip.days.map { d -> d.copy(plan = d.plan.map { if (it.activityId == "garganta-del-diablo") it.copy(status = "skipped") else it }) })
        assertFalse("activity:garganta-del-diablo" in skipped.dueToBook(dayBefore).map { it.key })
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
    fun `the plan's lead time and booking advice reach the item`() {
        val b = item("transfer:fly-bsas-iguazu")
        assertEquals("Aerolíneas Argentinas or Flybondi; fares rise inside four weeks.", b.how)
        val home = trip.copy(commitments = trip.commitments.map { if (it.id == "flight-home") it.copy(bookDaysBefore = 30) else it })
            .bookables().single { it.key == "commitment:flight-home" }
        assertEquals(
            listOf("2026-10-15", "2026-10-17", "2026-10-30", "2026-10-16"),
            listOf(item("lodging:bsas-1").bookBy, b.bookBy, item("activity:don-julio").bookBy, home.bookBy).map { it.toString() },
        )
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
    fun `a nightly lodging rate is multiplied by the nights, and a per-person price by the travelers`() {
        val nightly = Price(65.0, 80.0, "USD", unit = "night")
        val perPerson = Price(30.0, currency = "USD", unit = "person")
        val t = trip.copy(
            travelers = 2,
            stays = trip.stays.map { if (it.id == "iguazu") it.copy(lodging = it.lodging?.copy(price = nightly)) else it },
            activities = trip.activities.map { if (it.id == "garganta-del-diablo") it.copy(booking = it.booking?.copy(price = perPerson)) else it },
        )
        val lodging = t.bookables().single { it.key == "lodging:iguazu" }
        val nights = t.stay("iguazu")!!.nights
        assertEquals(listOf(65.0 * nights, 80.0 * nights), listOf(lodging.price?.amount, lodging.price?.max))
        assertEquals(nightly, lodging.rate)
        assertEquals(60.0, t.bookables().single { it.key == "activity:garganta-del-diablo" }.price?.amount)
        assertEquals("$65–80/night", nightly.rateLabel())
    }

    @Test
    fun `a price without a unit is the whole item, whatever the travelers`() {
        val b = trip.copy(travelers = 3).bookables().single { it.key == "transfer:fly-bsas-iguazu" }
        assertEquals(item("transfer:fly-bsas-iguazu").price, b.price)
        assertEquals(null, b.rate)
    }

    @Test
    fun `prices read as money, with ranges sharing the symbol`() {
        assertEquals("$120–180", Price(120.0, 180.0, "USD").label())
        assertEquals("$45.50", Price(45.5).label())
        assertEquals("ARS 85,000", money(85000.0, "ARS"))
    }
}
