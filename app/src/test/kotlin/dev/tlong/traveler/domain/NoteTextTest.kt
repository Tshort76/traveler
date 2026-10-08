package dev.tlong.traveler.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {

    @Test
    fun `enter after a bullet continues the list, and on an empty bullet ends it`() {
        // (before, after, cursor after typing) to (text, cursor)
        listOf(
            Triple("- milk", "- milk\n", 7) to ("- milk\n- " to 9),
            Triple("- milk\n- ", "- milk\n- \n", 10) to ("- milk\n" to 7),
            Triple("plain", "plain\n", 6) to ("plain\n" to 6),
            Triple("* eggs", "* eggs\n", 7) to ("* eggs\n- " to 9),
            Triple("- a\n- b", "- a\n\n- b", 4) to ("- a\n- \n- b" to 6),
            Triple("- milk", "- milkx", 7) to ("- milkx" to 7),
        ).forEach { (input, expected) -> assertEquals(input.toString(), expected, NoteText.afterTyping(input.first, input.second, input.third)) }
    }

    @Test
    fun `the bullet button toggles the cursor's line`() {
        listOf(
            ("milk" to 4) to ("- milk" to 6),
            ("- milk" to 6) to ("milk" to 4),
            ("a\nb" to 3) to ("a\n- b" to 5),
            ("a\nb" to 2) to ("a\n- b" to 4),
            ("" to 0) to ("- " to 2),
        ).forEach { (input, expected) -> assertEquals(input.toString(), expected, NoteText.toggleBullet(input.first, input.second)) }
    }
}
