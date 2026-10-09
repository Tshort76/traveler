package dev.tlong.traveler.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DayLayoutTest {
    private fun span(s: String) = s.split("-").map { t -> t.split(":").let { it[0].toInt() * 60 + it[1].toInt() } }.let { Span(it[0], it[1]) }

    @Test
    fun `loose items take the first gap in their window that fits`() {
        // (busy, window, minutes) to the start it gets
        val cases = listOf(
            Triple(listOf<String>(), "12:00-17:00", 60) to "12:00",
            Triple(listOf("12:00-15:00"), "12:00-17:00", 90) to "15:00",
            Triple(listOf("12:00-13:00", "13:30-17:00"), "12:00-17:00", 60) to "12:00", // 30 min free fits nothing: window start
            Triple(listOf("11:00-13:00", "14:30-15:30"), "12:00-17:00", 90) to "13:00",
            Triple(listOf("11:00-13:00", "13:30-15:30"), "12:00-17:00", 90) to "15:30",
        )
        cases.forEach { (input, want) ->
            val (busy, window, minutes) = input
            val got = DayLayout.firstGap(span(window), busy.map(::span), minutes)
            assertEquals("$input", span("$want-$want").start, got)
        }
    }

    @Test
    fun `loose items placed earlier count as busy for later ones`() {
        val placed = DayLayout.placeLoose(listOf(span("08:00-09:00")), listOf(span("08:00-12:00") to 60, span("08:00-12:00") to 60))
        assertEquals(listOf(span("09:00-10:00"), span("10:00-11:00")), placed)
    }

    @Test
    fun `overlapping blocks share the width and separate ones keep it`() {
        // spans to (column, columns) for each
        val cases = listOf(
            listOf("11:00-13:00", "12:00-13:00") to listOf(0 to 2, 1 to 2),
            listOf("11:00-13:00", "13:00-15:00") to listOf(0 to 1, 0 to 1),
            listOf("09:00-12:00", "09:30-10:00", "10:30-11:00") to listOf(0 to 2, 1 to 2, 1 to 2),
            listOf("09:00-10:00", "09:00-10:00", "09:00-10:00") to listOf(0 to 3, 1 to 3, 2 to 3),
        )
        cases.forEach { (spans, want) -> assertEquals("$spans", want, DayLayout.columns(spans.map(::span))) }
    }

    @Test
    fun `work hours split into two-hour blocks with the remainder last`() {
        // hours to blocks
        val cases = listOf(
            "09:00-17:00" to listOf("09:00-11:00", "11:00-13:00", "13:00-15:00", "15:00-17:00"),
            "11:00-15:00" to listOf("11:00-13:00", "13:00-15:00"),
            "08:00-13:00" to listOf("08:00-10:00", "10:00-12:00", "12:00-13:00"),
            "08:00-09:00" to listOf("08:00-09:00"),
        )
        cases.forEach { (hours, want) ->
            val (s, e) = hours.split("-").map(LocalTime::parse)
            assertEquals(hours, want, WorkPlan.defaultBlocks(WorkHours(s, e, null, null)).map { it.label.replace("–", "-") })
        }
    }

    @Test
    fun `a day set back to the default blocks follows the rhythm again`() {
        val hours = WorkHours(LocalTime.of(11, 0), LocalTime.of(15, 0), null, null)
        val moved = WorkPlan().with("2027-10-19", listOf(WorkBlock("11:00", "13:00"), WorkBlock("15:30", "17:30")), hours)
        assertEquals(listOf(WorkBlock("11:00", "13:00"), WorkBlock("15:30", "17:30")), moved.blocksOn("2027-10-19", hours))
        val cancelled = moved.with("2027-10-19", emptyList(), hours)
        assertEquals(emptyList<WorkBlock>(), cancelled.blocksOn("2027-10-19", hours))
        assertEquals(WorkPlan(), cancelled.with("2027-10-19", WorkPlan.defaultBlocks(hours), hours))
    }
}
