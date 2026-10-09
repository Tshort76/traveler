package dev.tlong.traveler.domain

import kotlin.math.roundToInt

/** A stretch of a day in minutes since midnight, end exclusive. */
data class Span(val start: Int, val end: Int) {
    val minutes get() = end - start
    fun overlaps(o: Span) = start < o.end && o.start < end
}

/** The geometry of the day calendar: where loose items sit and how overlapping blocks share the width. */
object DayLayout {

    /**
     * Places each loose item, in order, at the earliest start in its window where its minutes fit
     * between [busy] and the items already placed; at the window's start when nothing fits.
     */
    fun placeLoose(busy: List<Span>, loose: List<Pair<Span, Int>>): List<Span> {
        val taken = busy.toMutableList()
        return loose.map { (window, minutes) ->
            val start = firstGap(window, taken, minutes)
            Span(start, start + minutes).also { taken += it }
        }
    }

    fun firstGap(window: Span, busy: List<Span>, minutes: Int): Int {
        var cursor = window.start
        for (b in busy.filter { it.overlaps(window) }.sortedBy { it.start }) {
            if (b.start - cursor >= minutes) return cursor
            cursor = maxOf(cursor, b.end)
        }
        return if (window.end - cursor >= minutes) cursor else window.start
    }

    /** For each span, in input order: its column and the number of columns in its group of overlapping spans. */
    fun columns(spans: List<Span>): List<Pair<Int, Int>> {
        val result = arrayOfNulls<Pair<Int, Int>>(spans.size)
        val order = spans.indices.sortedWith(compareBy({ spans[it].start }, { -spans[it].end }))
        val group = mutableListOf<Pair<Int, Int>>()
        val columnEnds = mutableListOf<Int>()
        var groupEnd = Int.MIN_VALUE
        fun close() {
            group.forEach { (i, c) -> result[i] = c to columnEnds.size }
            group.clear(); columnEnds.clear(); groupEnd = Int.MIN_VALUE
        }
        for (i in order) {
            val s = spans[i]
            if (s.start >= groupEnd) close()
            val free = columnEnds.indexOfFirst { it <= s.start }
            val c = if (free >= 0) free.also { columnEnds[it] = s.end } else { columnEnds += s.end; columnEnds.lastIndex }
            group += i to c
            groupEnd = maxOf(groupEnd, s.end)
        }
        close()
        return result.map { it!! }
    }

    fun snap(minutes: Float, step: Int = 15): Int = (minutes / step).roundToInt() * step
}
