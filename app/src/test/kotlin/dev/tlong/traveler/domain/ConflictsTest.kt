package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.domain.PlacementIssue.Kind
import dev.tlong.traveler.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ConflictsTest {
    private val trip = Fixtures.iguazu.normalized()

    private fun kinds(activity: String, date: String, slot: Slot, time: String? = null) =
        checkPlacement(trip, trip.activity(activity)!!, LocalDate.parse(date), slot, time?.let { LocalTime.parse(it) })
            .map { it.kind }.toSet()

    @Test
    fun `placement issues by case`() {
        data class Case(val activity: String, val date: String, val slot: Slot, val time: String?, val expected: Set<Kind>)
        val cases = listOf(
            // Sunday's boat is booked 14:00–15:30.
            Case("guira-oga", "2026-11-08", Slot.AFTERNOON, "14:30", setOf(Kind.BOOKED)),
            Case("guira-oga", "2026-11-08", Slot.AFTERNOON, "16:00", emptySet()),
            // The booked activity itself does not conflict with its own booking.
            Case("gran-aventura", "2026-11-08", Slot.AFTERNOON, "14:00", setOf(Kind.ALREADY_PLANNED)),
            // Weekday work 08:00–15:00 leaves 06:00–08:00: a 2.5-hour hike cannot fit, a 1.5-hour visit can.
            Case("sendero-macuco", "2026-11-10", Slot.MORNING, null, setOf(Kind.WORK)),
            Case("guira-oga", "2026-11-12", Slot.MORNING, null, emptySet()),
            Case("guira-oga", "2026-11-10", Slot.MORNING, "10:00", setOf(Kind.WORK)),
            // Availability: the San Telmo fair is Sundays only.
            Case("feria-san-telmo", "2026-11-14", Slot.MORNING, null, setOf(Kind.CLOSED, Kind.ALREADY_PLANNED)),
            Case("reserva-costanera-sur", "2026-11-05", Slot.EVENING, "19:00", setOf(Kind.HOURS, Kind.ALREADY_PLANNED)),
            // Friday's 17:30 flight blocks from 16:00; work ends at 15:00.
            Case("guira-oga", "2026-11-13", Slot.AFTERNOON, "15:00", setOf(Kind.BOOKED)),
            // Another stay's suggestion is allowed, with a note.
            Case("malba", "2026-11-14", Slot.AFTERNOON, null, setOf(Kind.OTHER_STAY)),
        )
        cases.forEach { c -> assertEquals(c.toString(), c.expected, kinds(c.activity, c.date, c.slot, c.time)) }
    }

    @Test
    fun `informational issues do not block a move`() {
        val issues = checkPlacement(trip, trip.activity("malba")!!, LocalDate.parse("2026-11-14"), Slot.AFTERNOON, null)
        assertEquals(false, issues.any { it.blocking })
    }

    @Test
    fun `a day lists a timed item that collides with its booking once`() {
        val inferior = trip.day("2026-11-08")!!.plan.indexOfFirst { it.activityId == "circuito-inferior" }
        val moved = Edits.setTime(trip, Edits.ItemRef("2026-11-08", inferior), "14:30")
        assertEquals(1, dayWarnings(moved, LocalDate.parse("2026-11-08")).size)
        assertEquals(0, dayWarnings(trip, LocalDate.parse("2026-11-08")).size)
        // Work hours are the backdrop of a work day, not a warning.
        val inWork = Edits.setTime(trip, Edits.ItemRef("2026-11-10", 0), "10:00")
        assertEquals(0, dayWarnings(inWork, LocalDate.parse("2026-11-10")).size)
    }
}
