package dev.tlong.traveler.domain

/**
 * The traveler's notes are plain text where a line starting "- " (or "* ", "• ") is a bullet.
 * These keep the editor list-like without a rich-text model: Enter after a bullet starts the next
 * one, and Enter on an empty bullet ends the list.
 */
object NoteText {
    private val bullet = Regex("""^\s*[-*•]\s+""")

    /** A line's bullet text, or null when the line is not a bullet. */
    fun bulletOf(line: String): String? = bullet.find(line)?.let { line.substring(it.range.last + 1) }

    /**
     * The text and cursor after the user typed, given what was there before. Only a single newline
     * typed at the cursor is changed; everything else passes through.
     */
    fun afterTyping(before: String, after: String, cursor: Int): Pair<String, Int> {
        if (after.length != before.length + 1 || cursor < 1 || cursor > after.length || after[cursor - 1] != '\n') return after to cursor
        val lineStart = after.lastIndexOf('\n', cursor - 2) + 1
        val line = after.substring(lineStart, cursor - 1)
        val text = bulletOf(line) ?: return after to cursor
        return if (text.isBlank()) {
            // An empty bullet: drop it, and the newline, to end the list.
            after.removeRange(lineStart, cursor) to lineStart
        } else {
            val marker = "- "
            after.substring(0, cursor) + marker + after.substring(cursor) to cursor + marker.length
        }
    }

    /** Makes the cursor's line a bullet, or a bullet line plain again. */
    fun toggleBullet(text: String, cursor: Int): Pair<String, Int> {
        val start = text.lastIndexOf('\n', cursor - 1) + 1
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val marker = bullet.find(line)
        return if (marker != null) {
            val cut = marker.range.last + 1
            text.removeRange(start, start + cut) to (cursor - cut).coerceAtLeast(start)
        } else {
            text.substring(0, start) + "- " + text.substring(start) to cursor + 2
        }
    }
}
