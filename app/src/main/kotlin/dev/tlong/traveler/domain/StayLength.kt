package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Stay
import dev.tlong.traveler.model.Trip
import java.time.LocalDate

/** Where the nights a stay gains come from, or the nights it gives up go. */
enum class NightsFrom { NEXT_STAY, LATER_STAYS }

/**
 * A stay-length change worked out before it is made: the trip after it, how many planned items
 * lose their day, and every booked thing whose date or length it changes, so the traveler sees
 * them before agreeing.
 */
data class StayLength(val trip: Trip, val unscheduled: Int, val booked: List<String>)

/** The stay after [stay] when the two meet, and it could give up or take nights. */
fun Trip.nextStay(stay: Stay): Stay? =
    stays.getOrNull(stays.indexOfFirst { it.id == stay.id } + 1)?.takeIf { it.arrive == stay.depart && !isHome(it) }

/**
 * [stayId] lasting [nights] nights, by moving its departure. With [NightsFrom.NEXT_STAY] the next
 * stay starts on the new departure day and the trip keeps its dates; with [NightsFrom.LATER_STAYS]
 * everything from the old departure on shifts, and the trip ends that much earlier or later. Null
 * when it can't be done: under one night, or the next stay would drop under one.
 */
fun Trip.withNights(stayId: String, nights: Int, from: NightsFrom): StayLength? {
    val trip = normalized()
    val stay = trip.stay(stayId) ?: return null
    val shift = (nights - stay.nights).toLong()
    if (nights < 1 || shift == 0L) return null
    val oldDepart = stay.departDate
    val newDepart = oldDepart.plusDays(shift)
    val booked = mutableListOf<String>()
    var unscheduled = 0
    fun lodgingNote(s: Stay, n: Int) {
        if (s.lodging?.isBooked == true) booked += "${s.lodging.name ?: "Lodging in ${s.name}"}: booked for ${s.nights} night${if (s.nights == 1) "" else "s"}, now $n"
    }
    fun moved(what: String, old: String, new: String) {
        booked += "$what: ${old.toDate()?.label() ?: old} → ${new.toDate()?.label() ?: new}"
    }
    lodgingNote(stay, nights)

    val result = when (from) {
        NightsFrom.NEXT_STAY -> {
            val next = trip.nextStay(stay) ?: return null
            if (next.nights - shift < 1) return null
            lodgingNote(next, (next.nights - shift).toInt())
            // The days changing hands go to the stay now there, keeping only what belongs to it.
            val (range, owner) = if (shift > 0) (oldDepart until newDepart) to stay.id else (newDepart until oldDepart) to next.id
            trip.commitments.filter { it.isBooked && it.date.toDate()?.let { d -> d in range } == true }
                .forEach { booked += "${it.title}: ${it.date.toDate()?.label()} is now in ${trip.stay(owner)?.name}" }
            trip.copy(
                stays = trip.stays.map {
                    when (it.id) {
                        stay.id -> it.copy(depart = newDepart.toString()).edited("depart")
                        next.id -> it.copy(arrive = newDepart.toString()).edited("arrive")
                        else -> it
                    }
                },
                transfers = trip.transfers.map { t ->
                    if (t.from != stay.id || t.to != next.id || t.date != stay.depart) return@map t
                    if (t.booking?.isBooked == true) moved(t.label(trip), t.date, newDepart.toString())
                    t.copy(date = newDepart.toString())
                },
                days = trip.days.map { d ->
                    if (d.date.toDate()?.let { it in range } != true) return@map d
                    val kept = d.plan.filter { trip.activity(it.activityId)?.stayId == owner }
                    unscheduled += d.plan.size - kept.size
                    val day = d.copy(stayId = owner)
                    if (kept.size == d.plan.size) day else day.copy(plan = kept, userEdited = (d.userEdited + "plan").distinct())
                },
            )
        }
        NightsFrom.LATER_STAYS -> {
            fun shifted(date: String) = date.toDate()?.takeIf { it >= oldDepart }?.plusDays(shift)?.toString() ?: date
            // Shortening drops the stay's last days; what was planned on them goes back to its list.
            val dropped = if (shift < 0) newDepart until oldDepart else LocalDate.MAX until LocalDate.MAX
            trip.commitments.filter { it.isBooked && it.date.toDate()?.let { d -> d in dropped } == true }
                .forEach { booked += "${it.title}: ${it.date.toDate()?.label()} is no longer in ${stay.name}" }
            trip.stays.filter { it.id != stay.id && it.arriveDate >= oldDepart }.forEach { s ->
                if (s.lodging?.isBooked == true) moved(s.lodging.name ?: "Lodging in ${s.name}", s.arrive, shifted(s.arrive))
            }
            trip.copy(
                endDate = shifted(trip.endDate),
                userEdited = (trip.userEdited + "endDate").distinct(),
                stays = trip.stays.map {
                    val s = it.copy(arrive = shifted(it.arrive), depart = shifted(it.depart))
                    s.edited(*listOfNotNull("arrive".takeIf { _ -> s.arrive != it.arrive }, "depart".takeIf { _ -> s.depart != it.depart }).toTypedArray())
                },
                transfers = trip.transfers.map { t ->
                    val date = shifted(t.date)
                    if (date != t.date && t.booking?.isBooked == true) moved(t.label(trip), t.date, date)
                    t.copy(date = date)
                },
                commitments = trip.commitments.map { c ->
                    val date = shifted(c.date)
                    if (date != c.date && c.isBooked) moved(c.title, c.date, date)
                    c.copy(date = date)
                },
                days = trip.days.mapNotNull { d ->
                    if (d.date.toDate()?.let { it in dropped } == true) { unscheduled += d.plan.size; return@mapNotNull null }
                    d.copy(date = shifted(d.date))
                },
            )
        }
    }
    return StayLength(result.normalized(), unscheduled, booked)
}

private infix fun LocalDate.until(end: LocalDate): ClosedRange<LocalDate> = this..end.minusDays(1)

private fun Stay.edited(vararg fields: String): Stay =
    if (fields.isEmpty()) this else copy(userEdited = (userEdited + fields).distinct())

private fun dev.tlong.traveler.model.Transfer.label(trip: Trip) =
    "${modeLabel(shownMode)} to ${trip.stay(to)?.name ?: to}"

private fun modeLabel(mode: String?) = mode?.replaceFirstChar { it.uppercase() } ?: "Transfer"
