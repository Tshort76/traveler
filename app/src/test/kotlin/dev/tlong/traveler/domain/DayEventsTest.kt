package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayEventsTest {
    private val trip = Fixtures.iguazu

    @Test
    fun `only timed items become events, in time order`() {
        // date to the keys of its events: untimed plan items, untimed transfers and skipped items drop out
        val work = listOf(WorkBlock("09:00", "11:00"))
        val cases = listOf(
            "2026-11-08" to listOf("i-0-garganta-del-diablo", "i-3-gran-aventura"),
            "2026-11-06" to listOf("i-1-don-julio"),
            "2026-11-07" to emptyList(),
            "2026-11-13" to listOf("t-" + trip.transfers.first { it.date == "2026-11-13" }.id),
            "2026-11-15" to listOf("c-" + trip.commitments.first { it.date == "2026-11-15" }.id),
        )
        cases.forEach { (date, want) -> assertEquals(date, want, dayEvents(trip, date, emptyList()).map { it.key }) }
        assertEquals(listOf("w-09:00", "i-1-don-julio"), dayEvents(trip, "2026-11-06", work).map { it.key })
        val skipped = Edits.setStatus(trip, Edits.ItemRef("2026-11-06", 1), ItemStatus.SKIPPED)
        assertEquals(emptyList<String>(), dayEvents(skipped, "2026-11-06", emptyList()).map { it.key })
    }

    @Test
    fun `a booked activity is one event that carries its confirmation`() {
        val events = dayEvents(trip, "2026-11-08", emptyList())
        assertEquals(1, events.count { "gran-aventura" in it.key || it.key.startsWith("c-") })
        assertTrue("Confirmation: Voucher in email" in events.first { "gran-aventura" in it.key }.description.orEmpty())
    }
}
