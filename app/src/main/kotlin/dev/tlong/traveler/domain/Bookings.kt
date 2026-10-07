package dev.tlong.traveler.domain

import dev.tlong.traveler.model.Activity
import dev.tlong.traveler.model.Commitment
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.UNIT_NIGHT
import dev.tlong.traveler.model.UNIT_PERSON
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Everything on the trip that needs reserving, in one list: each stay's lodging, transfers by
 * public transport (or with a booking), suggestions the plan marks as needing a booking, and
 * bookings the traveler or the plan added. A suggestion booked through the app is one item, not
 * two: its booking record is folded into it.
 */
data class Bookable(
    val key: String,
    val kind: Kind,
    val title: String,
    /** The day it counts toward (check-in, departure, the booked or planned day); null if unscheduled. */
    val date: String?,
    val time: String? = null,
    val booked: Boolean,
    /** What it comes to: a nightly or per-person [rate] already multiplied out. */
    val price: Price? = null,
    /** The price as the plan gave it, when that was per night or per person. */
    val rate: Price? = null,
    val ref: String? = null,
    val url: String? = null,
    /** How soon to book it (1 now, 2 a week or more ahead, 3 can wait), as the plan wrote it but raised by [urgency]; and how or where. */
    val priority: Int? = null,
    val how: String? = null,
    val stayId: String? = null,
    /** The transfer mode, or the suggestion's tag, for the glyph. */
    val glyph: String? = null,
    /** The booking record behind it: the commitment itself, or the one booking a suggestion. */
    val record: Commitment? = null,
) {
    enum class Kind { LODGING, TRANSFER, ACTIVITY, BOOKING }

    /** An activity that needs booking but is on no day yet: not a booking until it is planned. */
    val unplanned get() = kind == Kind.ACTIVITY && date == null && !booked

    /** The id of the stay, transfer, activity or commitment it stands for. */
    val id get() = key.substringAfter(':')
}

private val ticketedModes = setOf("flight", "train", "bus", "boat", "ferry")

fun Trip.bookables(today: LocalDate = LocalDate.now()): List<Bookable> {
    val people = travelers ?: 1
    // The rate is worth showing only when multiplying changed it: "$800 per person" for one traveler is noise.
    fun rate(p: Price?, nights: Int = 1) = p?.takeIf { (it.unit == UNIT_NIGHT || it.unit == UNIT_PERSON) && it.total(nights, people) != it.copy(unit = null) }
    val lodging = stays.filter { it.nights > 0 }.map { s ->
        val l = s.lodging
        Bookable(
            "lodging:${s.id}", Bookable.Kind.LODGING, l?.name ?: "Lodging in ${s.name}", s.arrive,
            time = l?.checkIn, booked = l?.isBooked == true, price = l?.price?.total(s.nights, people), rate = rate(l?.price, s.nights),
            ref = l?.ref, url = l?.url, priority = l?.priority, how = l?.how,
            stayId = s.id,
        )
    }
    val transfers = transfers.filter { it.booking != null || it.mode in ticketedModes }.map { t ->
        Bookable(
            "transfer:${t.id}", Bookable.Kind.TRANSFER, "${stay(t.from)?.name ?: t.from} → ${stay(t.to)?.name ?: t.to}", t.date,
            time = t.depart, booked = t.booking?.isBooked == true, price = t.booking?.price?.total(travelers = people),
            rate = rate(t.booking?.price), ref = t.booking?.ref, url = t.booking?.url,
            priority = t.booking?.priority, how = t.booking?.how ?: t.details, stayId = t.to, glyph = t.shownMode,
        )
    }
    val needsBooking = activities.filter { it.booking != null }
    val activities = needsBooking.map { a ->
        val c = commitments.firstOrNull { it.activityId == a.id && it.isBooked }
        val placed = placementsOf(a.id).firstOrNull()
        Bookable(
            "activity:${a.id}", Bookable.Kind.ACTIVITY, a.name, c?.date ?: placed?.date, time = c?.start ?: placed?.item?.time,
            booked = c != null || a.booking?.isBooked == true, price = (c?.price ?: a.booking?.price)?.total(travelers = people),
            rate = rate(c?.price ?: a.booking?.price), ref = c?.ref ?: a.booking?.ref,
            url = c?.url ?: a.booking?.url ?: a.url, priority = a.booking?.priority, how = a.booking?.how ?: a.practical?.booking,
            stayId = a.stayId, glyph = a.tag, record = c,
        )
    }
    val folded = needsBooking.map { it.id }.toSet()
    val bookings = commitments.filter { (it.kind == "booking" || it.isBooked) && it.activityId !in folded }.map { c ->
        Bookable(
            "commitment:${c.id}", Bookable.Kind.BOOKING, c.title, c.date, time = c.start, booked = c.isBooked,
            price = c.price?.total(travelers = people), rate = rate(c.price),
            ref = c.ref, url = c.url, priority = c.priority, how = c.how, stayId = c.stayId, glyph = c.activityId?.let { activity(it)?.tag }, record = c,
        )
    }
    return (lodging + transfers + activities + bookings).map { it.copy(priority = urgency(it.priority, it.date, it.booked, today)) }
        .sortedWith(compareBy({ it.date ?: "9999" }, { it.time ?: "" }))
}

/** Days before its date at which an open "a week or more ahead" item has no slack left. */
const val P2_DUE_WITHIN_DAYS = 14L

/**
 * The plan's priority is written once, but its meaning runs out as the date nears: a P2 two weeks
 * off or less is now a P1. A P3 ("fine last minute") stays as it is, and a booked item is left alone.
 */
fun urgency(priority: Int?, date: String?, booked: Boolean, today: LocalDate): Int? {
    val days = date?.toDate()?.let { ChronoUnit.DAYS.between(today, it) } ?: return priority
    return if (!booked && priority == 2 && days <= P2_DUE_WITHIN_DAYS) 1 else priority
}

/** What to book as soon as possible: every unbooked P1, on a day or not, except an activity only ever skipped. */
fun Trip.bookAhead(today: LocalDate = LocalDate.now()): List<Bookable> = bookables(today).filter { b ->
    !b.booked && b.priority == 1 &&
        !(b.kind == Bookable.Kind.ACTIVITY && placementsOf(b.id).let { p -> p.isNotEmpty() && p.all { ItemStatus.of(it.item.status) == ItemStatus.SKIPPED } })
}

/** Booked through the plan or by the traveler, from the activity's sheet or the Bookings screen. */
fun Trip.isBooked(a: Activity): Boolean = a.booking?.isBooked == true || commitments.any { it.activityId == a.id && it.isBooked }

fun Trip.bookablesOn(date: String): List<Bookable> = bookables().filter { it.date == date }

/** Spending per currency: the estimate across everything, and what is booked. Prices without a currency count as USD. */
data class Totals(val currency: String, val estimateLow: Double, val estimateHigh: Double, val booked: Double) {
    val estimate get() = Price(estimateLow, estimateHigh, currency).label()
}

fun List<Bookable>.totals(): List<Totals> = mapNotNull { b -> b.price?.let { b to it } }
    .groupBy { (_, p) -> p.currency ?: "USD" }
    .map { (currency, items) ->
        Totals(
            currency,
            estimateLow = items.sumOf { it.second.amount },
            estimateHigh = items.sumOf { it.second.max ?: it.second.amount },
            booked = items.filter { it.first.booked }.sumOf { it.second.amount },
        )
    }

/** "$1,250", or "ARS 85,000" where the phone's locale has no symbol for it; whole amounts drop the cents. */
fun money(amount: Double, currency: String?): String {
    val f = java.text.NumberFormat.getCurrencyInstance()
    runCatching { f.currency = java.util.Currency.getInstance(currency ?: "USD") }
    f.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    f.minimumFractionDigits = f.maximumFractionDigits
    return f.format(amount).replace(Regex("^([A-Z]{3})(\\d)"), "$1 $2")
}

/**
 * What a price comes to: a nightly rate times [nights], a per-person price times [travelers], and
 * anything else as given. The result is a plain total, so it can be shown, summed and saved as one.
 */
fun Price.total(nights: Int = 1, travelers: Int = 1): Price {
    val times = when (unit) {
        UNIT_NIGHT -> nights
        UNIT_PERSON -> travelers
        else -> return if (unit == null) this else copy(unit = null)
    }.toDouble()
    return copy(amount = amount * times, max = max?.let { it * times }, unit = null)
}

/** "$65/night", "$45 per person"; null for a price that is already a total. */
fun Price.rateLabel(): String? = when (unit) {
    UNIT_NIGHT -> label() + "/night"
    UNIT_PERSON -> label() + " per person"
    else -> null
}

/** "$120", or "$120–200" for a range. */
fun Price.label(): String =
    money(amount, currency) + (max?.takeIf { it > amount }?.let { "–" + money(it, currency).dropWhile { c -> !c.isDigit() } } ?: "")
