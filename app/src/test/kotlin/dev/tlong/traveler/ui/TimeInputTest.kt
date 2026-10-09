package dev.tlong.traveler.ui

import dev.tlong.traveler.ui.activity.normalizeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeInputTest {
    @Test
    fun `typed times normalize to HH-MM or are refused`() {
        val cases = mapOf(
            "14:30" to "14:30", "1430" to "14:30", "930" to "09:30", "9:05" to "09:05", "14.30" to "14:30",
            " 0700 " to "07:00", "2400" to null, "1460" to null, "14" to null, "" to null, "noon" to null,
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, normalizeTime(input)) }
    }
}
