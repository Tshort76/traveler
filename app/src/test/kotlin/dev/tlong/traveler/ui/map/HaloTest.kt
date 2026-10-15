package dev.tlong.traveler.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTest {
    @Test
    fun `a stop's halo steps up with its nights`() {
        assertEquals(listOf(0f, 0f, 8f, 8f, 20f, 20f, 32f, 32f), listOf(1, 2, 3, 7, 8, 14, 15, 26).map { haloBand(it) })
    }
}
