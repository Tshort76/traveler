package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlaceableDaysTest {
    private val demo = TripReader.read(Fixtures.text("demo.trip.json")).trip!!

    @Test
    fun `a stay's days from today through the day it is left, all of them once it is over`() {
        listOf(
            Triple("2027-10-01", null, listOf("18", "19", "20")),
            Triple("2027-10-19", null, listOf("19", "20")),
            Triple("2027-10-30", null, listOf("18", "19", "20")),
            Triple("2027-10-19", "2027-10-18", listOf("18", "19", "20")),
        ).forEach { (today, keep, expected) ->
            val days = demo.placeableDays("palermo", LocalDate.parse(today), keep).map { it.date.takeLast(2) }
            assertEquals("today $today, keep $keep", expected, days)
        }
    }
}
