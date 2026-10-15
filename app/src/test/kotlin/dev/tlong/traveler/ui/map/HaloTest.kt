package dev.tlong.traveler.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTest {
    @Test
    fun `a stop's halo grows 3 dp per night beyond the shortest stop, up to 45`() {
        assertEquals(listOf(0f, 3f, 12f, 45f), listOf(3, 4, 7, 27).map { haloBand(it, fewest = 3) })
        assertEquals(0f, haloBand(30, fewest = 30)) // one long stay on its own gets no halo
    }
}
