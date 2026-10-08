package dev.tlong.traveler.domain

import dev.tlong.traveler.domain.CheckItem.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistTest {
    private fun item(text: String, kind: Kind = Kind.TODO, section: String? = null, done: Boolean = false) =
        CheckItem(text, kind, text, section, done)

    @Test
    fun `pasted lines become items under their sections`() {
        // line to (text, section, done) of the item it makes, or null for none
        val parsed = Checklists.parse(
            """
            Passport
            # Day before
            - Charge devices
            * [ ] Print tickets
            - [x] Book the taxi
            2. Water bottle

            Electronics:
            • Plug adapter
            ##
            Snacks
            """.trimIndent(),
            Kind.PACK,
        )
        assertEquals(
            listOf(
                Triple("Passport", null, false),
                Triple("Charge devices", "Day before", false),
                Triple("Print tickets", "Day before", false),
                Triple("Book the taxi", "Day before", true),
                Triple("Water bottle", "Day before", false),
                Triple("Plug adapter", "Electronics", false),
                Triple("Snacks", null, false),
            ),
            parsed.map { Triple(it.text, it.section, it.done) },
        )
        assertEquals(setOf(Kind.PACK), parsed.map { it.kind }.toSet())
    }

    @Test
    fun `applying a template adds only what is missing, unticked`() {
        val trip = Checklist(listOf(item("Passport", Kind.PACK, done = true), item("Get eSIM")))
        val (after, added) = Checklists.merge(
            trip,
            listOf(item("passport ", Kind.PACK), item("Passport"), item("Socks", Kind.PACK, done = true), item("Get eSIM")),
        )
        assertEquals(2, added)
        assertEquals(listOf("Passport", "Get eSIM", "Passport", "Socks"), after.items.map { it.text })
        assertEquals(listOf(true, false, false, false), after.items.map { it.done })
        assertEquals(2, Checklists.newCount(trip, listOf(item("passport ", Kind.PACK), item("Passport"), item("Socks", Kind.PACK))))
    }

    @Test
    fun `a new item lands at the end of its section`() {
        val list = Checklist(listOf(item("a", section = "Week"), item("b", section = "Day"), item("c", section = "Week"), item("p", Kind.PACK)))
        listOf(
            "Week" to listOf("a", "b", "c", "new", "p"),
            "Day" to listOf("a", "b", "new", "c", "p"),
            null to listOf("a", "b", "c", "p", "new"),
            "Later" to listOf("a", "b", "c", "p", "new"),
        ).forEach { (section, expected) ->
            assertEquals("$section", expected, Checklists.add(list, Kind.TODO, " new ", section).items.map { it.text })
        }
        assertEquals(list, Checklists.add(list, Kind.TODO, "  "))
    }

    @Test
    fun `items without a section come first, then sections in order of appearance`() {
        val sections = Checklists.sections(listOf(item("a", section = "Week"), item("b"), item("c", section = "Day"), item("d", section = " "), item("e", section = "Week")))
        assertEquals(listOf(null to listOf("b", "d"), "Week" to listOf("a", "e"), "Day" to listOf("c")), sections.map { (s, i) -> s to i.map { it.text } })
    }

    @Test
    fun `tallies, uncheck all and undoing a delete work per kind`() {
        val list = Checklist(listOf(item("a", done = true), item("p", Kind.PACK, done = true), item("q", Kind.PACK)))
        assertEquals(1 to 1, Checklists.tally(list, Kind.TODO))
        assertEquals(1 to 2, Checklists.tally(list, Kind.PACK))
        assertEquals(listOf(true, false, false), Checklists.uncheckAll(list, Kind.PACK).items.map { it.done })
        val gone = list.items[1]
        assertEquals(list, Checklists.insert(Checklists.remove(list, gone.id), 1, gone))
    }

    @Test
    fun `a section moves past its neighbour but never above the unsectioned items`() {
        val list = Checklist(listOf(item("a", section = "Week"), item("b"), item("c", section = "Day"), item("p", Kind.PACK), item("d", section = "Weeks")))
        listOf(
            ("Weeks" to -1) to listOf("b", "a", "d", "c", "p"),
            ("Week" to 1) to listOf("b", "c", "a", "d", "p"),
            ("Week" to -1) to listOf("a", "b", "c", "p", "d"),
            ("Weeks" to 1) to listOf("a", "b", "c", "p", "d"),
        ).forEach { (move, expected) ->
            assertEquals("$move", expected, Checklists.moveSection(list, Kind.TODO, move.first, move.second).items.map { it.text })
        }
        assertEquals(listOf("Day", null, "Day", "Weeks"), Checklists.renameSection(list, Kind.TODO, "Week", "Day").items.filter { it.kind == Kind.TODO }.map { it.section })
    }
}
