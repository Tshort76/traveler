package dev.tlong.traveler.domain

import kotlinx.serialization.Serializable
import java.time.LocalTime

/** One stretch of work on a day, in the stay's local time ("HH:MM"). */
@Serializable
data class WorkBlock(val start: String, val end: String) {
    val startTime get() = start.toTime() ?: LocalTime.MIN
    val endTime get() = end.toTime() ?: LocalTime.MAX
    val label get() = "$start–$end"
}

/**
 * The traveler's actual work schedule, per date. A date with no entry follows the stay's work
 * rhythm, cut into [BLOCK_HOURS]-hour blocks; an entry, even an empty one, replaces it. Kept on
 * the phone beside the trip file, so no revision or assistant ever changes it.
 */
@Serializable
data class WorkPlan(val days: Map<String, List<WorkBlock>> = emptyMap()) {

    fun blocksOn(date: String, hours: WorkHours?): List<WorkBlock> = days[date] ?: defaultBlocks(hours)

    /** Sets [date]'s blocks; matching the default drops the entry, so a later rhythm change still applies. */
    fun with(date: String, blocks: List<WorkBlock>, hours: WorkHours?): WorkPlan {
        val sorted = blocks.sortedBy { it.start }
        return WorkPlan(if (sorted == defaultBlocks(hours)) days - date else days + (date to sorted))
    }

    fun isEdited(date: String) = date in days

    companion object {
        const val BLOCK_HOURS = 2L

        /** The work hours cut into consecutive blocks of [BLOCK_HOURS]; the last takes the remainder. */
        fun defaultBlocks(hours: WorkHours?): List<WorkBlock> {
            hours ?: return emptyList()
            val blocks = mutableListOf<WorkBlock>()
            var s = hours.start
            while (s < hours.end) {
                val next = s.plusHours(BLOCK_HOURS)
                val e = if (next <= s || next > hours.end) hours.end else next
                blocks += WorkBlock(s.hhmm(), e.hhmm())
                if (e == hours.end) break
                s = e
            }
            return blocks
        }
    }
}
