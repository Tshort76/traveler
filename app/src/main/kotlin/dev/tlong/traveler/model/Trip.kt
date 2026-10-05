package dev.tlong.traveler.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/*
 * The trip file, field for field (docs/trip-format.md). The app keeps the document in this
 * shape end to end — import, edit, merge, export — so what it writes back is the same format
 * an assistant wrote. Dates and times stay ISO strings here and are parsed where they are
 * used (TripCalendar), which keeps a round trip byte-faithful. Enumerations are strings too:
 * TripValidator reports an unexpected value in plain words instead of failing the parse.
 */

const val TRIP_FORMAT = "traveler-trip"
const val TRIP_FORMAT_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Trip(
    @EncodeDefault val format: String = TRIP_FORMAT,
    @EncodeDefault val formatVersion: Int = TRIP_FORMAT_VERSION,
    val id: String,
    @EncodeDefault val revision: Int = 1,
    val generatedAt: String? = null,
    val generatedBy: String? = null,
    val title: String,
    val kicker: String? = null,
    val summary: String? = null,
    val startDate: String,
    val endDate: String,
    /** How many people the trip is for; per-person prices are multiplied by it. Absent means one. */
    val travelers: Int? = null,
    val links: List<Link> = emptyList(),
    val tripMap: TripMap? = null,
    val stays: List<Stay> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val days: List<Day> = emptyList(),
    val notes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val exportedFrom: ExportInfo? = null,
    val userEdited: List<String> = emptyList(),
    /** The validator's stamp; see [Stamp]. Dropped on export, since the app's edits change the content. */
    val validated: Validated? = null,
)

@Serializable
data class Validated(val by: String? = null, val hash: String? = null)

@Serializable
data class Link(val label: String? = null, val url: String, val kind: String? = null)

@Serializable
data class TripMap(
    val url: String? = null,
    val imageUrl: String? = null,
    val attribution: String? = null,
    val note: String? = null,
)

@Serializable
data class Place(
    val name: String? = null,
    val query: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val mapsUrl: String? = null,
) {
    val hasCoordinates get() = lat != null && lng != null
}

@Serializable
data class Stay(
    val id: String,
    val name: String,
    val region: String? = null,
    val place: Place? = null,
    val arrive: String,
    val depart: String,
    val timezone: String? = null,
    val summary: String? = null,
    val priorities: List<String> = emptyList(),
    val lodging: Lodging? = null,
    val transport: String? = null,
    val workRhythm: WorkRhythm? = null,
    val notes: List<String> = emptyList(),
    val mapUrl: String? = null,
    val links: List<Link> = emptyList(),
    val userEdited: List<String> = emptyList(),
)

@Serializable
data class Lodging(
    val name: String? = null,
    val place: Place? = null,
    val status: String? = null,
    val checkIn: String? = null,
    val checkOut: String? = null,
    val ref: String? = null,
    val url: String? = null,
    val priority: Int? = null,
    val how: String? = null,
    val notes: String? = null,
    val price: Price? = null,
) {
    val isBooked get() = status == "booked"
}

/**
 * A cost: [amount] alone is exact (or a single estimate), [amount] to [max] a range. [unit] says what
 * it is for: the whole item (the default), one night of a stay, or one traveler; the app does the
 * multiplying, because assistants quote nightly and per-person rates far more reliably than totals.
 */
@Serializable
data class Price(val amount: Double, val max: Double? = null, val currency: String? = null, val note: String? = null, val unit: String? = null)

const val UNIT_NIGHT = "night"
const val UNIT_PERSON = "person"

private val currencyCode = Regex("^[A-Z]{3}$")

fun isCurrencyCode(s: String) = currencyCode.matches(s)

@Serializable
data class WorkRhythm(
    val days: List<String> = emptyList(),
    val start: String,
    val end: String,
    val timezone: String? = null,
    val note: String? = null,
)

@Serializable
data class Transfer(
    val id: String,
    val from: String,
    val to: String,
    val date: String,
    val mode: String? = null,
    val depart: String? = null,
    val arrive: String? = null,
    val details: String? = null,
    val booking: Booking? = null,
) {
    /** The mode to show: the stated one, or for "other" the first vehicle the details name ("Park bus to Pudeto, then boat" is a bus). */
    val shownMode: String? get() = mode.takeUnless { it == null || it == "other" } ?: modeIn(details) ?: mode
}

private val modeWords = listOf(
    "flight" to Regex("\\b(fly|flies|flight|flights)\\b"),
    "train" to Regex("\\btrains?\\b"),
    "bus" to Regex("\\b(bus|buses|shuttle|coach)\\b"),
    "boat" to Regex("\\b(boat|catamaran)\\b"),
    "ferry" to Regex("\\bferry\\b"),
    "taxi" to Regex("\\b(taxi|remis|uber|cab|driver)\\b"),
    "car" to Regex("\\b(car|drive|driving)\\b"),
    "hike" to Regex("\\b(hike|hiking|trek|walk|descend|ascend)\\b"),
)

private fun modeIn(text: String?): String? {
    val t = text?.lowercase() ?: return null
    return modeWords.mapNotNull { (mode, re) -> re.find(t)?.let { it.range.first to mode } }.minByOrNull { it.first }?.second
}

@Serializable
data class Booking(
    val status: String? = null, val ref: String? = null, val url: String? = null, val priority: Int? = null, val how: String? = null,
    val price: Price? = null,
) {
    val isBooked get() = status == "booked"
}

@Serializable
data class Activity(
    val id: String,
    val stayId: String,
    val name: String,
    val tag: String? = null,
    val short: String? = null,
    @Serializable(with = ParagraphsSerializer::class)
    val detail: List<String> = emptyList(),
    val why: String? = null,
    val rank: Int? = null,
    /** How strongly the plan recommends it for this traveler: 3 must do, 2 likely to enjoy, 1 worth it if there's time. */
    val stars: Int? = null,
    val place: Place? = null,
    val duration: Duration? = null,
    val fit: String? = null,
    val bestTime: List<String> = emptyList(),
    val effort: String? = null,
    val conditions: List<String> = emptyList(),
    val hours: String? = null,
    val availability: List<Window> = emptyList(),
    val practical: Practical? = null,
    val booking: Booking? = null,
    val confidence: String? = null,
    val checkedOn: String? = null,
    val url: String? = null,
    val sources: List<String> = emptyList(),
    val origin: String? = null,
    val userEdited: List<String> = emptyList(),
    val userNote: String? = null,
) {
    val isCustom get() = origin == ORIGIN_USER
}

/** Fields the app writes; an import never overwrites them and they are not the traveler's edits. */
val APP_FIELDS = setOf("origin", "userEdited", "userNote")

const val ORIGIN_USER = "user"

@Serializable
data class Duration(val minutes: Int? = null, val maxMinutes: Int? = null, val includesTravel: Boolean? = null)

@Serializable
data class Window(
    val days: List<String> = emptyList(),
    val start: String? = null,
    val end: String? = null,
    val note: String? = null,
)

@Serializable
data class Practical(
    val transport: String? = null,
    val booking: String? = null,
    val cost: String? = null,
    val weather: String? = null,
    val access: String? = null,
    val tips: String? = null,
)

@Serializable
data class Commitment(
    val id: String,
    val title: String,
    val date: String,
    val start: String? = null,
    val end: String? = null,
    val kind: String? = null,
    val booked: Boolean? = null,
    val stayId: String? = null,
    val place: Place? = null,
    val ref: String? = null,
    val url: String? = null,
    val priority: Int? = null,
    val how: String? = null,
    val price: Price? = null,
    val notes: String? = null,
    val activityId: String? = null,
    val origin: String? = null,
    val userEdited: List<String> = emptyList(),
) {
    val isBooked get() = booked == true
}

@Serializable
data class Day(
    val date: String,
    val stayId: String? = null,
    val kind: String? = null,
    val title: String? = null,
    val note: String? = null,
    val plan: List<PlanItem> = emptyList(),
    val userEdited: List<String> = emptyList(),
)

@Serializable
data class PlanItem(
    val activityId: String,
    val slot: String,
    val time: String? = null,
    val status: String? = null,
    val note: String? = null,
)

@Serializable
data class ExportInfo(
    val app: String? = null,
    val exportedAt: String? = null,
    val basedOnRevision: Int? = null,
    val localChanges: Int? = null,
)

enum class Slot(val key: String, val label: String) {
    MORNING("morning", "Morning"),
    AFTERNOON("afternoon", "Afternoon"),
    EVENING("evening", "Evening"),
    ALLDAY("allday", "All day");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ALLDAY
    }
}

enum class ItemStatus(val key: String, val label: String) {
    PROPOSED("proposed", "Planned"),
    DONE("done", "Done"),
    SKIPPED("skipped", "Skipped");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: PROPOSED
    }
}

enum class DayKind(val key: String, val label: String) {
    PLAN("plan", "Plan"),
    WORK("work", "Work day"),
    REST("rest", "Rest day"),
    TRAVEL("travel", "Travel day"),
    FREE("free", "Free day");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: PLAN
    }
}

object Vocab {
    val priceUnits = listOf("total", UNIT_NIGHT, UNIT_PERSON)
    val slots = Slot.entries.map { it.key }
    val statuses = ItemStatus.entries.map { it.key }
    val dayKinds = DayKind.entries.map { it.key }
    val fits = listOf("short", "half-day", "full-day", "evening")
    val efforts = listOf("easy", "moderate", "hard")
    val confidences = listOf("confirmed", "estimate", "check")
    val bestTimes = listOf("morning", "afternoon", "evening")
    val transferModes = listOf("flight", "train", "bus", "boat", "ferry", "car", "taxi", "hike", "other")
    val commitmentKinds = listOf("booking", "appointment", "work", "other")
    val lodgingStatuses = listOf("booked", "tentative", "undecided")
    val bookingStatuses = listOf("booked", "tentative", "needed")
    val weekdays = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val origins = listOf("plan", "user")
}
