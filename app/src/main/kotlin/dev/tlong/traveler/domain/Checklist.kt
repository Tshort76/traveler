package dev.tlong.traveler.domain

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The traveler's to-dos and packing list for a trip, or the contents of a reusable template. Kept
 * beside the trip file, never in it, so no assistant sees it and no revision changes it. Sections
 * are the traveler's own headings ("Day before", "Clothes"); an item without one sits at the top.
 */
@Serializable
data class Checklist(val items: List<CheckItem> = emptyList())

@Serializable
data class CheckItem(
    val id: String,
    val kind: Kind,
    val text: String,
    val section: String? = null,
    val done: Boolean = false,
) {
    @Serializable
    enum class Kind { TODO, PACK }
}

/** A saved collection of to-dos and packing items, copied into a trip when applied. */
@Serializable
data class ChecklistTemplate(val id: String, val name: String, val items: List<CheckItem> = emptyList())

fun newItemId(): String = UUID.randomUUID().toString().take(8)

object Checklists {
    private fun key(i: CheckItem) = i.kind to i.text.trim().lowercase()

    fun of(list: Checklist, kind: CheckItem.Kind) = list.items.filter { it.kind == kind }

    /** Done and total for one kind. */
    fun tally(list: Checklist, kind: CheckItem.Kind): Pair<Int, Int> = of(list, kind).let { k -> k.count { it.done } to k.size }

    /** Open items grouped under their sections, in the order each section first appears. */
    fun sections(items: List<CheckItem>): List<Pair<String?, List<CheckItem>>> =
        items.groupBy { it.section?.takeIf(String::isNotBlank) }.toList()
            .sortedBy { (s, _) -> if (s == null) 0 else 1 }

    /** Adds [text] after the last item in its section, so a new item lands beside its neighbours. */
    fun add(list: Checklist, kind: CheckItem.Kind, text: String, section: String? = null): Checklist {
        val t = text.trim().ifEmpty { return list }
        val item = CheckItem(newItemId(), kind, t, section?.trim()?.ifEmpty { null })
        val at = list.items.indexOfLast { it.kind == kind && it.section == item.section }
        return list.copy(items = if (at < 0) list.items + item else list.items.toMutableList().apply { add(at + 1, item) })
    }

    fun update(list: Checklist, id: String, f: (CheckItem) -> CheckItem) =
        list.copy(items = list.items.map { if (it.id == id) f(it) else it })

    fun toggle(list: Checklist, id: String) = update(list, id) { it.copy(done = !it.done) }

    fun remove(list: Checklist, id: String) = list.copy(items = list.items.filterNot { it.id == id })

    /** Puts a removed item back where it was, for Undo. */
    fun insert(list: Checklist, index: Int, item: CheckItem) =
        list.copy(items = list.items.toMutableList().apply { add(index.coerceIn(0, size), item) })

    fun uncheckAll(list: Checklist, kind: CheckItem.Kind) =
        list.copy(items = list.items.map { if (it.kind == kind) it.copy(done = false) else it })

    /**
     * Copies [incoming] in, unchecked and with fresh ids, skipping any whose text (ignoring case)
     * is already on the list for the same kind. Returns the new list and how many were added.
     */
    fun merge(list: Checklist, incoming: List<CheckItem>): Pair<Checklist, Int> {
        val seen = list.items.map(::key).toMutableSet()
        val added = incoming.filter { seen.add(key(it)) }.map { it.copy(id = newItemId(), done = false) }
        return list.copy(items = list.items + added) to added.size
    }

    /** How many of [incoming] [merge] would add. */
    fun newCount(list: Checklist, incoming: List<CheckItem>): Int {
        val seen = list.items.map(::key).toMutableSet()
        return incoming.count { seen.add(key(it)) }
    }

    /**
     * Pasted lines as items: one per line, list markers and "[ ]" boxes dropped, and a line
     * starting with "#" (or ending with ":") names the section for the lines below it.
     */
    fun parse(text: String, kind: CheckItem.Kind): List<CheckItem> {
        var section: String? = null
        return text.lines().mapNotNull { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> null
                line.startsWith("#") -> { section = line.trimStart('#').trim().ifEmpty { null }; null }
                line.endsWith(":") && !line.startsWith("-") -> { section = line.dropLast(1).trim().ifEmpty { null }; null }
                else -> {
                    val done = Regex("""^[-*•]?\s*\[[xX]]""").containsMatchIn(line)
                    val t = line.replace(Regex("""^([-*•]|\d+[.)])?\s*(\[[ xX]?]\s*)?"""), "").trim()
                    t.takeIf { it.isNotEmpty() }?.let { CheckItem(newItemId(), kind, it, section, done) }
                }
            }
        }
    }

    /** Moves a section's block of [kind] items one place up or down in the order [sections] shows. */
    fun moveSection(list: Checklist, kind: CheckItem.Kind, section: String, by: Int): Checklist {
        val blocks = sections(of(list, kind)).toMutableList()
        val i = blocks.indexOfFirst { it.first == section }
        val j = i + by
        // Items without a section always come first, so nothing moves above them.
        if (i < 0 || j !in blocks.indices || blocks[j].first == null) return list
        blocks[i] = blocks[j].also { blocks[j] = blocks[i] }
        return list.copy(items = blocks.flatMap { it.second } + list.items.filter { it.kind != kind })
    }

    /** Renames a section, or merges it into another of that name. */
    fun renameSection(list: Checklist, kind: CheckItem.Kind, from: String, to: String) = list.copy(
        items = list.items.map { if (it.kind == kind && it.section == from) it.copy(section = to.trim().ifEmpty { null }) else it },
    )

    /** Sections already used for [kind], for the section picker. */
    fun sectionNames(list: Checklist, kind: CheckItem.Kind) = of(list, kind).mapNotNull { it.section }.distinct()

    /** Generic starters, offered once on a new install; the traveler edits or deletes them. */
    val starters: List<ChecklistTemplate> by lazy {
        fun t(id: String, name: String, todo: String, pack: String) =
            ChecklistTemplate(id, name, parse(todo, CheckItem.Kind.TODO) + parse(pack, CheckItem.Kind.PACK))
        listOf(
            t(
                "starter-every-trip", "Every trip",
                """
                # Week before
                Confirm bookings and check-in times
                Check passport or ID expiry
                Arrange pet, plant and mail care
                # Day before
                Check in for flights
                Download boarding passes
                Charge devices
                Download music, podcasts and shows
                """.trimIndent(),
                """
                # Documents
                Passport or ID
                Wallet and cards
                # Electronics
                Phone charger
                Headphones
                Power bank
                # Toiletries
                Toothbrush and toothpaste
                Medications
                Sunscreen
                # Clothes
                Underwear
                Socks
                Sleepwear
                Comfortable walking shoes
                Rain jacket
                """.trimIndent(),
            ),
            t(
                "starter-international", "International",
                """
                # Weeks before
                Check visa or entry requirements
                Get a cellular plan or eSIM
                Tell the bank about travel
                Check travel insurance
                # Week before
                Download Google Maps offline areas
                Download Google Translate languages
                Get some local cash
                """.trimIndent(),
                """
                # Electronics
                Plug adapter
                # Documents
                Copies of passport and insurance
                Card with no foreign transaction fee
                """.trimIndent(),
            ),
        )
    }
}
