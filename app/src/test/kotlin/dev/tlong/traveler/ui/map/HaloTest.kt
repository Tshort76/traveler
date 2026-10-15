package dev.tlong.traveler.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTest {
    @Test
    fun `a stop's halo grows with log2 of its nights`() {
        assertEquals(listOf(0f, 5f, 10f, 15f), listOf(0, 1, 3, 7).map { haloBand(it) })
        assertEquals(4.75f, haloBand(26) / haloBand(1), 0.01f)
    }
}
