package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.USER_DONE
import java.time.LocalDate
import java.time.LocalTime

/** Where an activity lands when picking something for a free time, and why it isn't a fit when it isn't. */
data class SlotFit(val activity: Activity, val group: Group, val reason: String? = null) {
    enum class Group { FITS, OTHER, PLANNED, WONT_DO }
}

/**
 * Sorts [candidates] for a free [slot] (and [time], with [freeMinutes] until the next thing) on [date]:
 * what fits first, then the rest with the reason, then what is planned or done, then what the traveler
 * won't do. Each group is most recommended first.
 */
fun suggestFor(
    trip: Trip, candidates: List<Activity>, date: LocalDate, slot: Slot, time: LocalTime?,
    freeMinutes: Int? = null, work: List<WorkBlock>? = null,
): List<SlotFit> = candidates.map { a ->
    val placed = trip.placementsOf(a.id)
    when {
        a.isWontDo -> SlotFit(a, SlotFit.Group.WONT_DO)
        a.userStatus == USER_DONE || placed.any { ItemStatus.of(it.item.status) == ItemStatus.DONE } -> SlotFit(a, SlotFit.Group.PLANNED, "done")
        placed.isNotEmpty() -> SlotFit(a, SlotFit.Group.PLANNED, "planned " + placed.mapNotNull { it.date.toDate()?.label() }.distinct().joinToString(", "))
        else -> misfit(trip, a, date, slot, time, freeMinutes, work)?.let { SlotFit(a, SlotFit.Group.OTHER, it) } ?: SlotFit(a, SlotFit.Group.FITS)
    }
}.sortedWith(compareBy<SlotFit> { it.group }.thenBy(byRecommendation) { it.activity })

private fun misfit(trip: Trip, a: Activity, date: LocalDate, slot: Slot, time: LocalTime?, freeMinutes: Int?, work: List<WorkBlock>?): String? {
    checkPlacement(trip, a, date, slot, time, work = work).firstOrNull { it.blocking }?.let { return it.message }
    val minutes = a.duration?.minutes
    if (freeMinutes != null && minutes != null && minutes > freeMinutes) return "Takes ${minutesLabel(minutes)}; ${minutesLabel(freeMinutes)} free"
    if (slot == Slot.ALLDAY) return null
    if (a.fit == "evening" && slot != Slot.EVENING) return "An evening outing"
    if (a.fit == "full-day" && slot == Slot.EVENING) return "Takes the whole day"
    if (a.bestTime.isNotEmpty() && slot.key !in a.bestTime) return "Best in the " + a.bestTime.joinToString(" or ")
    return null
}

private val byRecommendation = compareBy<Activity>({ -(it.stars ?: 0) }, { it.rank ?: Int.MAX_VALUE }, { it.name })

private fun minutesLabel(m: Int) = if (m < 60) "$m min" else "${m / 60} h" + (if (m % 60 > 0) " ${m % 60}" else "")
