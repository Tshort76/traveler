package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Link
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The brief's validation scenario: a revision arrives after the traveler has edited the plan. */
class MergeTest {
    private val base = Fixtures.iguazu
    private val incoming = Fixtures.iguazuR2
    private val myMondayNote = "Work; then the Feirinha with a book."

    private val customId = "my-laundry"
    private val local = run {
        var t = base.normalized()
        t = Edits.setDayNote(t, "2026-11-09", myMondayNote)                       // conflicts with r2's Monday note
        val hito = Edits.ItemRef("2026-11-07", t.day("2026-11-07")!!.plan.indexOfFirst { it.activityId == "hito-tres-fronteras" })
        t = Edits.move(t, hito, "2026-11-08", Slot.EVENING)                         // Sunday plan: both sides change it
        t = Edits.setStatus(t, Edits.ItemRef("2026-11-07", 0), ItemStatus.DONE)    // Saturday: only mine
        val (withCustom, id) = Edits.addCustom(t, "iguazu", "Laundry", note = "Hotel does it by weight")
        t = Edits.place(withCustom, id, "2026-11-10", Slot.MORNING)
        t = Edits.place(t, "isla-san-martin", "2026-11-12", Slot.MORNING)          // r2 drops it; I scheduled it
        t = Edits.deleteActivity(t, "la-aripuca")                                   // r2 leaves it unchanged
        t = Edits.setUserNote(t, "feirinha", "Try the provoleta")
        check(id == customId)
        t
    }

    private val plan = Merge.plan(base, local, incoming)

    @Test
    fun `only the items both sides changed need a choice`() {
        assertEquals(setOf("days:2026-11-09:note", "days:2026-11-08:plan"), plan.conflicts.map { it.key }.toSet())
    }

    @Test
    fun `the summary covers bookings, activities, plans and lodging`() {
        val text = plan.changes.joinToString("\n") { "${it.category}: ${it.text}" }
        listOf("Gran Aventura boat ride: moved from", "Added: Itaipu Dam", "Garganta del Diablo walkway: description", "Mon 9 Nov",
            "lodging").forEach { assertTrue("missing '$it' in\n$text", it.lowercase() in text.lowercase()) }
    }

    @Test
    fun `a dropped suggestion I scheduled defaults to keep`() {
        val r = plan.removals.single()
        assertEquals("activities:isla-san-martin" to false, r.key to r.defaultRemove)
    }

    @Test
    fun `accepting the defaults keeps every personal change and applies the rest`() {
        val merged = Merge.apply(plan)
        assertEquals(myMondayNote, merged.day("2026-11-09")!!.note)
        assertEquals("jardin-picaflores", merged.day("2026-11-09")!!.plan.first().activityId) // r2's Monday plan, which I never touched
        assertTrue(merged.day("2026-11-08")!!.plan.any { it.activityId == "hito-tres-fronteras" })
        assertEquals("done", merged.day("2026-11-07")!!.plan[0].status)
        assertTrue(merged.activity(customId)!!.isCustom)
        assertEquals(customId, merged.day("2026-11-10")!!.plan.first().activityId)
        assertNotNull(merged.activity("isla-san-martin"))
        assertNull(merged.activity("la-aripuca"))
        assertEquals("Try the provoleta", merged.activity("feirinha")!!.userNote)
        assertEquals("10:30", merged.commitments.first { it.id == "boat-gran-aventura" }.start)
        assertNotNull(merged.activity("itaipu-dam"))
        assertEquals(2, merged.revision)
        assertTrue(TripReader.read(dev.tlong.traveler.model.TripJson.encode(merged)).ok)
    }

    @Test
    fun `choosing theirs and accepting a removal take effect`() {
        val merged = Merge.apply(plan, Merge.Decisions(takeTheirs = setOf("days:2026-11-08:plan"), remove = setOf("activities:isla-san-martin")))
        assertEquals(incoming.normalized().day("2026-11-08")!!.plan, merged.day("2026-11-08")!!.plan)
        assertNull(merged.activity("isla-san-martin"))
        assertFalse("isla-san-martin" in merged.scheduledIds())
    }

    @Test
    fun `freshness flags newer, same and older files`() {
        assertEquals(Merge.Freshness.NEWER, plan.freshness)
        assertEquals(Merge.Freshness.UP_TO_DATE, Merge.freshness(base, Fixtures.iguazu))
        assertEquals(Merge.Freshness.OLDER, Merge.freshness(incoming, base))
    }

    @Test
    fun `an untouched trip takes a revision without questions`() {
        val p = Merge.plan(base, base, incoming)
        assertTrue(p.conflicts.isEmpty())
        assertEquals(incoming.normalized().days, Merge.apply(p).days)
    }

    @Test
    fun `my export coming back unchanged merges to the same plan`() {
        val exported = TripReader.read(Export.text(base, local)).trip!!
        val p = Merge.plan(base, local, exported)
        assertTrue(p.conflicts.isEmpty() && p.removals.isEmpty() && p.changes.isEmpty())
        assertEquals(local.normalized().days, Merge.apply(p).days)
        assertEquals(local.activities, Merge.apply(p).activities)
    }

    @Test
    fun `a booking added on the phone stays scheduled even when I take the file's plan for that day`() {
        val (withBooking, id) = Edits.addBooking(local, "2026-11-08", "Macuco guided walk", "16:00", "18:30", null, null, "sendero-macuco")
        val merged = Merge.apply(Merge.plan(base, withBooking, incoming), Merge.Decisions(takeTheirs = setOf("days:2026-11-08:plan")))
        assertEquals("sendero-macuco", merged.commitments.first { it.id == id }.activityId)
        assertEquals("16:00", merged.day("2026-11-08")!!.plan.single { it.activityId == "sendero-macuco" }.time)
    }

    @Test
    fun `my trip notes survive a revision that changes or drops them`() {
        val mine = Edits.setTripNote(base, "- Bring the adapter")
        listOf(incoming, incoming.copy(userNote = "assistant wrote this")).forEach { file ->
            assertEquals("- Bring the adapter", Merge.apply(Merge.plan(base, mine, file)).userNote)
        }
    }

    @Test
    fun `local change count reflects edits`() {
        assertEquals(0, Merge.localChangeCount(base, base.normalized()))
        assertEquals(9, Merge.localChangeCount(base, local))
    }

    @Test
    fun `a link I added survives a revision that adds its own, and a link I deleted stays deleted`() {
        val sheet = Link("My sheet", "https://docs.google.com/spreadsheets/d/mine", "spreadsheet")
        val chat = Link("Planning chat", "https://claude.ai/chat/x", "chat")
        val dropped = base.links.first()
        val mine = Edits.setLink(Edits.setLink(base.normalized(), dropped, null), null, sheet)
        val theirs = incoming.copy(links = incoming.links + chat)
        val p = Merge.plan(base, mine, theirs)
        assertTrue(p.conflicts.none { it.field == "links" })
        assertEquals(theirs.links.filter { it.url != dropped.url } + sheet, Merge.apply(p).links)
    }

    @Test
    fun `a link I edited keeps my version, and a link the file dropped goes unless I edited it`() {
        val (sheet, chat) = base.links
        val renamedSheet = sheet.copy(label = "Budget")
        assertEquals(listOf(renamedSheet, chat), Merge.apply(Merge.plan(base, Edits.setLink(base.normalized(), sheet, renamedSheet), incoming)).links)
        val renamedChat = chat.copy(label = "Main chat")
        val dropsBoth = incoming.copy(links = emptyList())
        assertEquals(listOf(renamedChat), Merge.apply(Merge.plan(base, Edits.setLink(base.normalized(), chat, renamedChat), dropsBoth)).links)
    }
}
