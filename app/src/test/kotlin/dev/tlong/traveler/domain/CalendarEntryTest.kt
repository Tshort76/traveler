package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarEntryTest {
    private val day = LocalDate.of(2026, 11, 7)
    private val bsas = ZoneId.of("America/Argentina/Buenos_Aires")
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `an overnight flight between zones lands the next day, in the arrival's local time`() {
        val e = calendarEntry("Flight", day, LocalTime.of(22, 0), LocalTime.of(15, 30), bsas, madrid)
        assertFalse(e.allDay)
        assertEquals(Instant.parse("2026-11-08T01:00:00Z").toEpochMilli(), e.begin)
        assertEquals(Instant.parse("2026-11-08T14:30:00Z").toEpochMilli(), e.end)
    }

    @Test
    fun `a booking with no end lasts an hour, and its details skip blanks`() {
        val e = calendarEntry("Tango show", day, LocalTime.of(20, 0), null, bsas, details = listOf("Confirmation: X1", null, " "))
        assertEquals(3_600_000, e.end - e.begin)
        assertEquals("Confirmation: X1", e.description)
    }

    @Test
    fun `with no time it is all day through the last date`() {
        val e = calendarEntry("Hotel", day, null, null, bsas, lastDate = day.plusDays(2))
        assertTrue(e.allDay)
        assertEquals(Instant.parse("2026-11-07T00:00:00Z").toEpochMilli(), e.begin)
        assertEquals(Instant.parse("2026-11-10T00:00:00Z").toEpochMilli(), e.end)
    }

    @Test
    fun `a saved booking becomes lodging all day through check-out, an untimed flight all day, a booked event at its local times`() {
        val trip = Fixtures.iguazu.normalized()
        val items = trip.bookables().associateBy { it.key }
        fun entry(key: String) = trip.calendarEntry(items.getValue(key))!!
        fun utc(s: String) = Instant.parse(s).toEpochMilli()

        val lodging = entry("lodging:iguazu")
        assertTrue(lodging.allDay)
        assertEquals(utc("2026-11-07T00:00:00Z") to utc("2026-11-14T00:00:00Z"), lodging.begin to lodging.end)

        val flight = entry("transfer:fly-bsas-iguazu")
        assertTrue(flight.allDay && flight.title.startsWith("Flight ") && "AEP → IGR" in flight.description!!)

        val boat = entry("activity:gran-aventura")
        assertEquals(utc("2026-11-08T17:00:00Z") to utc("2026-11-08T18:30:00Z"), boat.begin to boat.end)
        assertTrue("Confirmation: Voucher in email" in boat.description!! && "15 minutes early" in boat.description!!)
    }
}
