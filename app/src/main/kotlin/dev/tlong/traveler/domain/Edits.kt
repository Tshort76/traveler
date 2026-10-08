package dev.tlong.traveler.domain

import dev.tlong.traveler.model.APP_FIELDS
import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Booking
import dev.tlong.traveler.model.Commitment
import dev.tlong.traveler.model.Day
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Link
import dev.tlong.traveler.model.Lodging
import dev.tlong.traveler.model.ORIGIN_USER
import dev.tlong.traveler.model.Place
import dev.tlong.traveler.model.PlanItem
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import dev.tlong.traveler.model.Transfer
import kotlinx.serialization.json.jsonObject

/**
 * Every change the traveler can make, as a pure function from one trip to the next. The
 * session keeps the previous trip for undo, so nothing here needs to be reversible itself.
 *
 * Edits record provenance as they go: a field the traveler rewrote on an assistant's item is
 * added to that item's `userEdited`, and a day whose note or plan they changed records it too.
 * The exported file carries that to the next assistant, which is told not to overwrite it.
 */
object Edits {

    /** A plan item's address within a day. Indexes are into [Day.plan]. */
    data class ItemRef(val date: String, val index: Int)

    private fun Trip.updateDay(date: String, f: (Day) -> Day): Trip {
        val existing = day(date)
        val base = existing ?: Day(date = date, stayId = date.toDate()?.let { stayFor(it)?.id })
        val updated = f(base)
        val list = if (existing == null) (days + updated).sortedBy { it.date } else days.map { if (it.date == date) updated else it }
        return copy(days = list)
    }

    private fun Day.marked(field: String) = if (field in userEdited) this else copy(userEdited = userEdited + field)

    private fun Day.withPlan(plan: List<PlanItem>) = copy(plan = chronological(plan)).marked("plan")

    fun setDayNote(trip: Trip, date: String, note: String): Trip =
        trip.updateDay(date) { it.copy(note = note.ifBlank { null }).marked("note") }

    fun setDayKind(trip: Trip, date: String, kind: String): Trip =
        trip.updateDay(date) { it.copy(kind = kind).marked("kind") }

    /** Appends to the end of [slot], or inserts at [positionInSlot] among that slot's items. */
    fun place(trip: Trip, activityId: String, date: String, slot: Slot, time: String? = null, positionInSlot: Int? = null): Trip =
        trip.updateDay(date) { day ->
            day.withPlan(insertInSlot(day.plan, PlanItem(activityId = activityId, slot = slot.key, time = time), positionInSlot))
        }

    fun remove(trip: Trip, ref: ItemRef): Trip =
        trip.updateDay(ref.date) { day -> day.withPlan(day.plan.filterIndexed { i, _ -> i != ref.index }) }

    fun move(trip: Trip, from: ItemRef, toDate: String, toSlot: Slot, time: String? = null, positionInSlot: Int? = null): Trip {
        val item = trip.day(from.date)?.plan?.getOrNull(from.index) ?: return trip
        val moved = item.copy(slot = toSlot.key, time = time)
        val removed = remove(trip, from)
        return removed.updateDay(toDate) { day -> day.withPlan(insertInSlot(day.plan, moved, positionInSlot)) }
    }

    /** Replaces a day's plan wholesale — what a drag gesture produces when it lets go. */
    fun setPlan(trip: Trip, date: String, plan: List<PlanItem>): Trip =
        trip.updateDay(date) { it.withPlan(plan) }

    fun setStatus(trip: Trip, ref: ItemRef, status: ItemStatus): Trip = updateItem(trip, ref) {
        it.copy(status = if (status == ItemStatus.PROPOSED) null else status.key)
    }

    fun setTime(trip: Trip, ref: ItemRef, time: String?): Trip = updateItem(trip, ref) { item ->
        val slot = time?.toTime()?.let { slotForTime(it) }?.takeIf { Slot.of(item.slot) != Slot.ALLDAY }
        item.copy(time = time, slot = slot?.key ?: item.slot)
    }


    private fun updateItem(trip: Trip, ref: ItemRef, f: (PlanItem) -> PlanItem): Trip =
        trip.updateDay(ref.date) { day ->
            day.withPlan(day.plan.mapIndexed { i, item -> if (i == ref.index) f(item) else item })
        }

    /**
     * Slot order, and within a slot the fixed-time items first in time order, then the rest in
     * the traveler's order. A timed item's place follows from its time, so dragging cannot
     * contradict it.
     */
    fun chronological(plan: List<PlanItem>): List<PlanItem> = Slot.entries.flatMap { s ->
        val (timed, open) = plan.filter { Slot.of(it.slot) == s }.partition { it.time != null }
        timed.sortedBy { it.time } + open
    }

    /** Inserts keeping items grouped by slot in the canonical slot order. */
    fun insertInSlot(plan: List<PlanItem>, item: PlanItem, positionInSlot: Int?): List<PlanItem> {
        val bySlot = Slot.entries.associateWith { s -> plan.filter { Slot.of(it.slot) == s }.toMutableList() }
        val target = bySlot.getValue(Slot.of(item.slot))
        val at = positionInSlot?.coerceIn(0, target.size) ?: target.size
        target.add(at, item)
        return Slot.entries.flatMap { bySlot.getValue(it) }
    }


    fun newActivityId(trip: Trip, name: String): String = uniqueId(name, "entry", trip.activities.map { it.id }.toSet())

    private fun uniqueId(name: String, fallback: String, taken: Set<String>): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { fallback }
        var id = "my-$slug"
        var n = 2
        while (id in taken) id = "my-$slug-${n++}"
        return id
    }

    /** A personal entry: only a title is needed. Returns the trip and the new id. */
    fun addCustom(
        trip: Trip, stayId: String, name: String, note: String? = null, placeQuery: String? = null, tag: String? = null,
    ): Pair<Trip, String> {
        val id = newActivityId(trip, name)
        val activity = Activity(
            id = id, stayId = stayId, name = name.trim(), tag = tag, origin = ORIGIN_USER,
            userNote = note?.ifBlank { null }, place = placeQuery?.ifBlank { null }?.let { Place(query = it) },
        )
        return trip.copy(activities = trip.activities + activity) to id
    }


    /**
     * The traveler's own booking. Linked to a suggestion, it also schedules that suggestion at
     * the booked time on that day, taking it off any other day it was planned on.
     */
    fun addBooking(
        trip: Trip, date: String, title: String, start: String?, end: String?, ref: String?, notes: String?, activityId: String?,
        url: String? = null, price: Price? = null,
    ): Pair<Trip, String> {
        val id = uniqueId(title, "booking", trip.commitments.map { it.id }.toSet())
        val c = Commitment(
            id = id, title = title.trim(), date = date, start = start, end = end, kind = "booking", booked = true,
            stayId = date.toDate()?.let { trip.stayFor(it)?.id }, ref = ref?.ifBlank { null }, notes = notes?.ifBlank { null },
            activityId = activityId, origin = ORIGIN_USER, url = url?.ifBlank { null }, price = price,
        )
        return scheduleBooked(trip.copy(commitments = trip.commitments + c), c) to id
    }

    /** Edits a booking; on an assistant's booking the changed fields are recorded in `userEdited`. */
    fun updateBooking(trip: Trip, id: String, f: (Commitment) -> Commitment): Trip {
        val old = trip.commitments.firstOrNull { it.id == id } ?: return trip
        var c = f(old)
        if (c.origin != ORIGIN_USER) {
            val before = TripJson.compact.encodeToJsonElement(Commitment.serializer(), old).jsonObject
            val after = TripJson.compact.encodeToJsonElement(Commitment.serializer(), c).jsonObject
            c = c.copy(userEdited = (c.userEdited + (before.keys + after.keys).filter { it !in APP_FIELDS && before[it] != after[it] }).distinct())
        }
        val updated = trip.copy(commitments = trip.commitments.map { if (it.id == id) c else it })
        return if (c.activityId != null && (c.activityId != old.activityId || c.date != old.date || c.start != old.start)) scheduleBooked(updated, c) else updated
    }

    /**
     * After a merge: a booking the traveler made keeps its linked suggestion on the booked day,
     * even when the traveler took the file's version of that day's plan.
     */
    fun keepBookingsScheduled(trip: Trip): Trip = trip.commitments
        .filter { c -> c.origin == ORIGIN_USER && c.activityId != null && trip.activity(c.activityId) != null }
        .fold(trip) { t, c -> if (t.day(c.date)?.plan?.any { it.activityId == c.activityId } == true) t else scheduleBooked(t, c) }

    fun deleteBooking(trip: Trip, id: String): Trip = trip.copy(commitments = trip.commitments.filter { it.id != id })

    private fun scheduleBooked(trip: Trip, c: Commitment): Trip {
        val activityId = c.activityId ?: return trip
        val cleared = trip.days.fold(trip) { t, d ->
            if (d.plan.none { it.activityId == activityId }) t
            else t.updateDay(d.date) { day -> day.withPlan(day.plan.filter { it.activityId != activityId }) }
        }
        val slot = c.start?.toTime()?.let { slotForTime(it) } ?: Slot.ALLDAY
        return place(cleared, activityId, c.date, slot, c.start)
    }

    /**
     * Applies [f] to one activity. On an assistant's activity every content field that changed
     * is recorded in `userEdited`; a personal entry is the traveler's throughout, so needs none.
     */
    fun updateActivity(trip: Trip, id: String, f: (Activity) -> Activity): Trip = trip.copy(activities = trip.activities.map { a ->
        if (a.id != id) return@map a
        val b = f(a)
        if (b.isCustom) return@map b
        val before = TripJson.compact.encodeToJsonElement(Activity.serializer(), a).jsonObject
        val after = TripJson.compact.encodeToJsonElement(Activity.serializer(), b).jsonObject
        val changed = (before.keys + after.keys).filter { it !in APP_FIELDS && before[it] != after[it] }
        b.copy(userEdited = (b.userEdited + changed).distinct())
    })

    fun setTripNote(trip: Trip, note: String): Trip = trip.copy(userNote = note.trimEnd().ifBlank { null })

    fun setUserNote(trip: Trip, id: String, note: String): Trip =
        trip.copy(activities = trip.activities.map { if (it.id == id) it.copy(userNote = note.ifBlank { null }) else it })

    /** Deletes the activity itself — every placement of it goes too. Distinct from returning it to the pool. */
    fun deleteActivity(trip: Trip, id: String): Trip = trip.copy(
        activities = trip.activities.filterNot { it.id == id },
        days = trip.days.map { d -> if (d.plan.any { it.activityId == id }) d.withPlan(d.plan.filterNot { it.activityId == id }) else d },
        commitments = trip.commitments.map { if (it.activityId == id) it.copy(activityId = null) else it },
    )


    fun rename(trip: Trip, title: String): Trip =
        trip.copy(title = title.trim(), userEdited = (trip.userEdited + "title").distinct())

    /** Replaces [old] with [new]: no [old] adds a link, no [new] deletes it. */
    fun setLink(trip: Trip, old: Link?, new: Link?): Trip {
        val links = if (old == null) trip.links + listOfNotNull(new) else trip.links.mapNotNull { if (it == old) new else it }
        return trip.copy(links = links, userEdited = (trip.userEdited + "links").distinct())
    }

    /** What the traveler records on booking lodging or a transfer; [start]/[end] are check-in or depart/arrive. */
    data class Booked(val name: String?, val ref: String?, val url: String?, val price: Price?, val notes: String?, val start: String?, val end: String?)

    fun markBooked(trip: Trip, b: Bookable, d: Booked): Trip = when (b.kind) {
        Bookable.Kind.LODGING -> updateLodging(trip, b.id) {
            it.copy(status = "booked", name = d.name ?: it.name, ref = d.ref, url = d.url, price = d.price, notes = d.notes, checkIn = d.start)
        }
        Bookable.Kind.TRANSFER -> updateTransfer(trip, b.id) {
            it.copy(depart = d.start, arrive = d.end, booking = (it.booking ?: Booking()).copy(status = "booked", ref = d.ref, url = d.url, price = d.price))
        }
        // Suggestions and bookings are booked through a commitment (addBooking/updateBooking), which also schedules them.
        Bookable.Kind.ACTIVITY, Bookable.Kind.BOOKING -> b.record?.let { c -> updateBooking(trip, c.id) { it.copy(booked = true) } } ?: trip
    }

    fun markNotBooked(trip: Trip, b: Bookable): Trip = when (b.kind) {
        Bookable.Kind.LODGING -> updateLodging(trip, b.id) { it.copy(status = if (it.name != null) "tentative" else null) }
        Bookable.Kind.TRANSFER -> updateTransfer(trip, b.id) { it.copy(booking = (it.booking ?: Booking()).copy(status = "needed")) }
        Bookable.Kind.ACTIVITY, Bookable.Kind.BOOKING -> (b.record?.let { c -> updateBooking(trip, c.id) { it.copy(booked = false) } } ?: trip).let { t ->
            if (b.kind == Bookable.Kind.ACTIVITY && t.activity(b.id)?.booking?.isBooked == true) {
                updateActivity(t, b.id) { it.copy(booking = it.booking?.copy(status = "needed")) }
            } else t
        }
    }

    private fun updateTransfer(trip: Trip, id: String, f: (Transfer) -> Transfer): Trip =
        trip.copy(transfers = trip.transfers.map { if (it.id == id) f(it) else it })

    private fun updateLodging(trip: Trip, stayId: String, f: (Lodging) -> Lodging): Trip = trip.copy(stays = trip.stays.map {
        if (it.id == stayId) it.copy(lodging = f(it.lodging ?: Lodging()), userEdited = (it.userEdited + "lodging").distinct()) else it
    })

    fun setStayMapUrl(trip: Trip, stayId: String, url: String?): Trip = trip.copy(stays = trip.stays.map {
        if (it.id == stayId) it.copy(mapUrl = url?.ifBlank { null }, userEdited = (it.userEdited + "mapUrl").distinct()) else it
    })
}

/** Where an activity is placed, for "already scheduled" badges. */
data class Placement(val date: String, val index: Int, val item: PlanItem)

fun Trip.placementsOf(activityId: String): List<Placement> = days.flatMap { d ->
    d.plan.mapIndexedNotNull { i, item -> if (item.activityId == activityId) Placement(d.date, i, item) else null }
}

fun Trip.scheduledIds(): Set<String> = days.flatMapTo(mutableSetOf()) { d -> d.plan.map { it.activityId } }

fun Trip.activity(id: String): Activity? = activities.firstOrNull { it.id == id }
