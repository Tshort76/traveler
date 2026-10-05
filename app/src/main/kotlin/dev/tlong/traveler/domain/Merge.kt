package dev.tlong.traveler.domain

import dev.tlong.traveler.model.APP_FIELDS
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Importing a revision of a trip the traveler has been editing.
 *
 * Three versions meet: BASE (the file last imported, kept untouched), LOCAL (the traveler's
 * current plan) and INCOMING (the new file). For each item — matched by id, or by date for
 * days — and each field of it:
 *
 *  - only the file changed it        → take the file's value (listed under "what changed")
 *  - only the traveler changed it    → keep the traveler's value
 *  - both changed it, differently    → a conflict: the traveler chooses; "keep mine" is the default
 *
 * A day's plan is compared as one value, because a merged plan made of both sides' items would be
 * a plan neither side wrote. Items the file drops become removal decisions: untouched suggestions
 * default to "remove", anything the traveler scheduled or edited defaults to "keep". The traveler's
 * own entries (not in BASE) are never touched.
 */
object Merge {

    enum class Category(val label: String) {
        DATES("Dates"), STAYS("Stays"), BOOKINGS("Bookings"), ACTIVITIES("Activities"), PLANS("Suggested plans"), TRIP("Trip")
    }

    enum class Freshness { UP_TO_DATE, NEWER, SAME_REVISION, OLDER }

    data class Change(val category: Category, val text: String)

    data class Conflict(
        val key: String, val category: Category, val label: String, val field: String,
        val mine: String, val theirs: String,
    )

    /** An item the incoming file no longer has. [key] goes in [Decisions.remove] to drop it. */
    data class Removal(val key: String, val category: Category, val label: String, val reason: String, val defaultRemove: Boolean)

    /** An item the traveler deleted that the incoming file changed. [key] goes in [Decisions.restore] to bring it back. */
    data class Restorable(val key: String, val category: Category, val label: String)

    data class Plan(
        val base: Trip, val local: Trip, val incoming: Trip,
        val freshness: Freshness,
        val changes: List<Change>,
        val conflicts: List<Conflict>,
        val removals: List<Removal>,
        val restorables: List<Restorable>,
    ) {
        val defaults get() = Decisions(remove = removals.filter { it.defaultRemove }.mapTo(mutableSetOf()) { it.key })
        val isEmpty get() = changes.isEmpty() && conflicts.isEmpty() && removals.isEmpty() && restorables.isEmpty()
    }

    data class Decisions(
        val takeTheirs: Set<String> = emptySet(),
        val remove: Set<String> = emptySet(),
        val restore: Set<String> = emptySet(),
    )

    private val tripLists = listOf("stays", "transfers", "activities", "commitments", "days")
    private val ignoredTripFields = setOf("exportedFrom", "revision", "generatedAt", "generatedBy", "format", "formatVersion", "validated")

    private fun categoryOf(kind: String, field: String): Category = when (kind) {
        "stays" -> when (field) { "arrive", "depart" -> Category.DATES; "lodging" -> Category.BOOKINGS; else -> Category.STAYS }
        "transfers", "commitments" -> Category.BOOKINGS
        "activities" -> if (field == "booking") Category.BOOKINGS else Category.ACTIVITIES
        "days" -> Category.PLANS
        else -> if (field == "startDate" || field == "endDate") Category.DATES else Category.TRIP
    }

    private fun keyOf(kind: String, o: JsonObject): String =
        (if (kind == "days") o["date"] else o["id"])?.jsonPrimitive?.content ?: ""

    private fun items(trip: JsonObject, kind: String): LinkedHashMap<String, JsonObject> {
        val out = LinkedHashMap<String, JsonObject>()
        (trip[kind] as? JsonArray)?.forEach { e -> (e as? JsonObject)?.let { out[keyOf(kind, it)] = it } }
        return out
    }

    private fun label(kind: String, o: JsonObject?, trip: Trip): String {
        if (o == null) return "?"
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content
        return when (kind) {
            "days" -> s("date")?.toDate()?.label() ?: s("date") ?: "?"
            "stays" -> s("name") ?: s("id") ?: "?"
            "transfers" -> listOfNotNull(trip.stay(s("from"))?.name ?: s("from"), trip.stay(s("to"))?.name ?: s("to")).joinToString(" → ")
            "commitments" -> s("title") ?: s("id") ?: "?"
            "activities" -> s("name") ?: s("id") ?: "?"
            else -> trip.title
        }
    }

    private fun canonical(trip: Trip): JsonObject {
        val o = TripJson.toElement(trip.normalized())
        return JsonObject(o.filterKeys { it !in ignoredTripFields })
    }

    fun freshness(base: Trip, incoming: Trip): Freshness {
        if (canonical(base) == canonical(incoming)) return Freshness.UP_TO_DATE
        if (incoming.revision < base.revision) return Freshness.OLDER
        val b = base.generatedAt
        val i = incoming.generatedAt
        if (b != null && i != null && i < b) return Freshness.OLDER
        if (incoming.revision == base.revision) return Freshness.SAME_REVISION
        return Freshness.NEWER
    }

    fun plan(base: Trip, local: Trip, incoming: Trip): Plan {
        val b = TripJson.toElement(base.normalized())
        val l = TripJson.toElement(local.normalized())
        val i = TripJson.toElement(incoming.normalized())
        val changes = mutableListOf<Change>()
        val conflicts = mutableListOf<Conflict>()
        val removals = mutableListOf<Removal>()
        val restorables = mutableListOf<Restorable>()
        val scheduled = local.scheduledIds()
        val names = (local.activities + incoming.activities).associate { it.id to it.name }

        mergeFields("trip", "trip", local.title, stripLists(b), stripLists(l), stripLists(i), null, changes, conflicts, names)

        for (kind in tripLists) {
            val bi = items(b, kind)
            val li = items(l, kind)
            val ii = items(i, kind)
            for (key in (ii.keys + li.keys + bi.keys).distinct()) {
                val bo = bi[key]
                val lo = li[key]
                val io = ii[key]
                val name = label(kind, io ?: lo ?: bo, incoming)
                when {
                    bo == null && lo == null && io != null ->
                        if (!(kind == "days" && isEmptyDay(io))) changes += Change(categoryOf(kind, ""), "Added: $name")
                    bo == null && lo != null && io != null ->
                        mergeFields(kind, key, name, JsonObject(emptyMap()), lo, io, null, changes, conflicts, names)
                    bo != null && lo != null && io != null ->
                        mergeFields(kind, key, name, bo, lo, io, null, changes, conflicts, names)
                    bo != null && lo != null && io == null -> {
                        if (kind == "days" && isEmptyDay(lo)) continue
                        val touched = stripApp(lo) != stripApp(bo)
                        val used = kind == "activities" && key in scheduled
                        val reason = when {
                            used && touched -> "You scheduled and edited it."
                            used -> "It is in your plan."
                            touched -> "You edited it."
                            else -> "Untouched suggestion."
                        }
                        removals += Removal("$kind:$key", categoryOf(kind, ""), name, reason, defaultRemove = !used && !touched)
                    }
                    bo != null && lo == null && io != null ->
                        if (stripApp(io) != stripApp(bo)) restorables += Restorable("$kind:$key", categoryOf(kind, ""), name)
                }
            }
        }
        return Plan(base, local, incoming, freshness(base, incoming), changes, conflicts, removals, restorables)
    }

    /** Applies [d] to [p] and returns the merged trip; the caller stores [Plan.incoming] as the new base. */
    fun apply(p: Plan, d: Decisions = p.defaults): Trip {
        val b = TripJson.toElement(p.base.normalized())
        val l = TripJson.toElement(p.local.normalized())
        val i = TripJson.toElement(p.incoming.normalized())
        val out = LinkedHashMap<String, JsonElement>()
        // Trip scalars: incoming metadata (revision, generatedAt…) always wins; it describes the file.
        val tripFields = mergeFields("trip", "trip", "", stripLists(b), stripLists(l), stripLists(i), d.takeTheirs, null, null)
        out.putAll(tripFields)
        listOf("revision", "generatedAt", "generatedBy", "format", "formatVersion").forEach { k -> i[k]?.let { out[k] = it } }
        out.remove("exportedFrom")

        for (kind in tripLists) {
            val bi = items(b, kind)
            val li = items(l, kind)
            val ii = items(i, kind)
            val result = mutableListOf<JsonObject>()
            for (key in (ii.keys + li.keys).distinct()) {
                val bo = bi[key]
                val lo = li[key]
                val io = ii[key]
                val k = "$kind:$key"
                when {
                    lo == null && io != null && bo == null -> result += io
                    lo == null && io != null && bo != null -> if (k in d.restore) result += io
                    lo != null && io == null && bo == null -> result += lo
                    lo != null && io == null && bo != null -> if (k !in d.remove) result += lo
                    lo != null && io != null ->
                        result += JsonObject(mergeFields(kind, key, "", bo ?: JsonObject(emptyMap()), lo, io, d.takeTheirs, null, null))
                }
            }
            out[kind] = JsonArray(if (kind == "days") result.sortedBy { keyOf(kind, it) } else result)
        }
        return Edits.keepBookingsScheduled(repair(TripJson.fromElement(JsonObject(out)))).normalized()
    }

    /** Field-by-field three-way merge of one item. Records changes/conflicts when given lists. */
    private fun mergeFields(
        kind: String, key: String, name: String,
        b: JsonObject, l: JsonObject, i: JsonObject,
        takeTheirs: Set<String>?, changes: MutableList<Change>?, conflicts: MutableList<Conflict>?,
        names: Map<String, String> = emptyMap(),
    ): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        val changedFields = mutableListOf<String>()
        for (f in (i.keys + l.keys + b.keys).distinct()) {
            if (kind == "trip" && f in ignoredTripFields) { l[f]?.let { out[f] = it }; continue }
            val bv = b[f]
            val lv = l[f]
            val iv = i[f]
            val chosen: JsonElement? = when {
                f in APP_FIELDS -> lv
                kind == "trip" && f == "links" -> { if (iv != bv) changedFields += f; mergeLinks(bv, lv, iv) }
                lv == bv -> { if (iv != bv) changedFields += f; iv }
                iv == bv || lv == iv -> lv
                else -> {
                    val ck = "$kind:$key:$f"
                    conflicts?.add(Conflict(ck, categoryOf(kind, f), name.ifEmpty { key }, fieldLabel(kind, f), show(lv, names), show(iv, names)))
                    if (takeTheirs != null && ck in takeTheirs) iv else lv
                }
            }
            if (chosen != null) out[f] = chosen
        }
        if (changes != null && changedFields.isNotEmpty()) {
            changedFields.groupBy { categoryOf(kind, it) }.forEach { (cat, fields) ->
                changes += Change(cat, describe(kind, name, fields, b, i))
            }
        }
        return out
    }

    /**
     * Trip links merge one link at a time, matched by url, so a planning sheet the traveler added
     * in the app survives a file that adds links of its own. Deletions on either side stick; a link
     * the traveler edited keeps their version.
     */
    private fun mergeLinks(b: JsonElement?, l: JsonElement?, i: JsonElement?): JsonArray {
        fun byUrl(e: JsonElement?) = (e as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            .associateBy { (it["url"] as? JsonPrimitive)?.content.orEmpty() }
        val base = byUrl(b)
        val local = byUrl(l)
        val incoming = byUrl(i)
        val fromFile = incoming.mapNotNull { (url, link) ->
            when {
                url in base && url !in local -> null
                url in local && local[url] != base[url] -> local[url]
                else -> link
            }
        }
        val mine = local.filter { (url, link) -> url !in incoming && (url !in base || link != base[url]) }.values
        return JsonArray(fromFile + mine)
    }

    private fun describe(kind: String, name: String, fields: List<String>, b: JsonObject, i: JsonObject): String {
        fun s(o: JsonObject, f: String) = (o[f] as? JsonPrimitive)?.content
        return when {
            kind == "commitments" && ("start" in fields || "end" in fields || "date" in fields) -> {
                fun whenOf(o: JsonObject) = listOfNotNull(
                    s(o, "date")?.toDate()?.label(),
                    listOfNotNull(s(o, "start"), s(o, "end")).joinToString("–").ifEmpty { null },
                ).joinToString(" ")
                "$name: moved from ${whenOf(b)} to ${whenOf(i)}"
            }
            kind == "stays" && ("arrive" in fields || "depart" in fields) ->
                "$name: now ${s(i, "arrive")} to ${s(i, "depart")} (was ${s(b, "arrive")} to ${s(b, "depart")})"
            kind == "days" -> "$name: " + fields.joinToString(" and ") { if (it == "plan") "suggested activities changed" else "${fieldLabel(kind, it)} changed" }
            kind == "trip" -> fields.joinToString(", ") { "${fieldLabel(kind, it)} changed" }.replaceFirstChar { it.uppercase() }
            else -> "$name: ${fields.joinToString(", ") { fieldLabel(kind, it) }} updated"
        }
    }

    private fun fieldLabel(kind: String, f: String): String = when (f) {
        "detail" -> "description"
        "note" -> if (kind == "days") "day note" else "note"
        "plan" -> "planned activities"
        "start", "end" -> "time"
        "arrive", "depart" -> "dates"
        "startDate", "endDate" -> "trip dates"
        "tripMap" -> "trip map"
        else -> f.replace(Regex("([A-Z])")) { " " + it.value.lowercase() }
    }

    /** A short, readable rendering of a field value for the conflict picker. */
    fun show(e: JsonElement?, names: Map<String, String> = emptyMap()): String = when (e) {
        null -> "(none)"
        is JsonPrimitive -> e.content
        is JsonArray -> e.joinToString("\n") { item ->
            when (item) {
                is JsonObject -> {
                    val id = (item["activityId"] as? JsonPrimitive)?.content
                    listOfNotNull(
                        (item["slot"] as? JsonPrimitive)?.content?.replaceFirstChar { it.uppercase() },
                        (item["time"] as? JsonPrimitive)?.content,
                        id?.let { names[it] ?: it },
                        (item["status"] as? JsonPrimitive)?.content?.let { "($it)" },
                    ).joinToString(" · ")
                }
                else -> show(item, names)
            }
        }
        is JsonObject -> e.entries.joinToString(", ") { "${it.key}: ${show(it.value, names)}" }
    }

    private fun stripLists(o: JsonObject) = JsonObject(o.filterKeys { it !in tripLists })

    private fun stripApp(o: JsonObject) = JsonObject(o.filterKeys { it !in APP_FIELDS })

    private fun isEmptyDay(o: JsonObject) = o.keys.all { it == "date" || it == "stayId" }

    /** Drops references the merge left dangling: plan items for removed activities, links to removed stays. */
    private fun repair(t: Trip): Trip {
        val activityIds = t.activities.map { it.id }.toSet()
        val stayIds = t.stays.map { it.id }.toSet()
        return t.copy(
            days = t.days.map { d ->
                d.copy(
                    plan = d.plan.filter { it.activityId in activityIds },
                    stayId = d.stayId?.takeIf { it in stayIds },
                )
            },
            commitments = t.commitments.map { c ->
                c.copy(activityId = c.activityId?.takeIf { it in activityIds }, stayId = c.stayId?.takeIf { it in stayIds })
            },
            activities = t.activities.filter { it.stayId in stayIds || it.isCustom },
            transfers = t.transfers.filter { it.from in stayIds && it.to in stayIds },
        )
    }

    /** How many items differ between the last import and the current plan — "N changes since import". */
    fun localChangeCount(base: Trip, local: Trip): Int {
        val b = TripJson.toElement(base.normalized())
        val l = TripJson.toElement(local.normalized())
        var n = if (stripLists(b).filterKeys { it !in ignoredTripFields } != stripLists(l).filterKeys { it !in ignoredTripFields }) 1 else 0
        for (kind in tripLists) {
            val bi = items(b, kind)
            val li = items(l, kind)
            for (key in (bi.keys + li.keys).distinct()) {
                val bo = bi[key]
                val lo = li[key]
                if (bo == null && lo != null && kind == "days" && isEmptyDay(lo)) continue
                if (bo != lo) n++
            }
        }
        return n
    }
}
