package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.ItemStatus
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private val trip = Fixtures.iguazu.normalized()
    private val sat = "2026-11-07"
    private val sun = "2026-11-08"

    @Test
    fun `moving Saturday's activity to Sunday evening removes it from Saturday`() {
        val ref = Edits.ItemRef(sat, trip.day(sat)!!.plan.indexOfFirst { it.activityId == "hito-tres-fronteras" })
        val moved = Edits.move(trip, ref, sun, Slot.EVENING)
        assertTrue(moved.day(sat)!!.plan.none { it.activityId == "hito-tres-fronteras" })
        val last = moved.day(sun)!!.plan.last()
        assertEquals("hito-tres-fronteras" to "evening", last.activityId to last.slot)
        assertTrue("plan" in moved.day(sun)!!.userEdited && "plan" in moved.day(sat)!!.userEdited)
    }

    @Test
    fun `returning an item to the pool keeps the activity`() {
        val removed = Edits.remove(trip, Edits.ItemRef(sat, 0))
        val id = trip.day(sat)!!.plan[0].activityId
        assertNotNull(removed.activity(id))
        assertTrue(id !in removed.scheduledIds())
    }

    @Test
    fun `deleting an activity removes every placement of it`() {
        val deleted = Edits.deleteActivity(trip, "gran-aventura")
        assertNull(deleted.activity("gran-aventura"))
        assertTrue("gran-aventura" !in deleted.scheduledIds())
        assertNull(deleted.commitments.first { it.id == "boat-gran-aventura" }.activityId)
    }

    @Test
    fun `two entries with the same title get distinct ids and the trip stays valid`() {
        val (t1, a) = Edits.addCustom(trip, "iguazu", "Laundry")
        val (t2, b) = Edits.addCustom(t1, "iguazu", "Laundry")
        assertTrue(a != b)
        val errors = mutableListOf<String>()
        TripReader.check(t2, errors, mutableListOf())
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `editing a suggestion records which fields carry the traveler's wording`() {
        val t = Edits.updateActivity(trip, "feirinha") { it.copy(name = "Feirinha dinner", short = "Cheese and wine") }
        assertEquals(setOf("name", "short"), t.activity("feirinha")!!.userEdited.toSet())
    }

    @Test
    fun `a day note edit is kept verbatim and marked`() {
        val words = "  my own words, exactly  "
        val day = Edits.setDayNote(trip, "2026-11-09", words).day("2026-11-09")!!
        assertEquals(words, day.note)
        assertTrue("note" in day.userEdited)
    }

    @Test
    fun `a personal entry goes where it is put among the untimed items`() {
        val (t, id) = Edits.addCustom(trip, "iguazu", "Coffee")
        assertTrue(t.activity(id)!!.isCustom)
        fun afternoon(pos: Int) = Edits.place(t, id, sun, Slot.AFTERNOON, positionInSlot = pos).day(sun)!!.plan
            .filter { Slot.of(it.slot) == Slot.AFTERNOON }.map { it.activityId }
        assertEquals(listOf("gran-aventura", id, "circuito-inferior"), afternoon(0))
        assertEquals(listOf("gran-aventura", "circuito-inferior", id), afternoon(99))
    }

    @Test
    fun `fixed-time items sort by time within their slot, ahead of untimed ones`() {
        val morning = { t: dev.tlong.traveler.model.Trip -> t.day(sun)!!.plan.filter { Slot.of(it.slot) == Slot.MORNING }.map { it.activityId } }
        // The file lists the afternoon untimed-first; loading sorts the 14:00 boat ahead.
        assertEquals(listOf("gran-aventura", "circuito-inferior"), trip.day(sun)!!.plan.filter { Slot.of(it.slot) == Slot.AFTERNOON }.map { it.activityId })
        val superior = Edits.ItemRef(sun, trip.day(sun)!!.plan.indexOfFirst { it.activityId == "circuito-superior" })
        assertEquals(listOf("circuito-superior", "garganta-del-diablo"), morning(Edits.setTime(trip, superior, "07:00")))
        // A drag that puts an untimed item above a timed one snaps back.
        val dragged = Edits.setPlan(trip, sun, trip.day(sun)!!.plan.sortedBy { it.activityId != "circuito-superior" })
        assertEquals(listOf("garganta-del-diablo", "circuito-superior"), morning(dragged))
    }

    @Test
    fun `a booking linked to a suggestion schedules it at the booked time on that day only`() {
        val (t, id) = Edits.addBooking(trip, "2026-11-09", "Macuco guided walk", "16:00", null, "ABC123", null, "sendero-macuco")
        val c = t.commitments.first { it.id == id }
        assertTrue(c.isBooked && c.origin == "user" && c.stayId == "iguazu")
        val item = t.day("2026-11-09")!!.plan.single { it.activityId == "sendero-macuco" }
        assertEquals("16:00" to Slot.AFTERNOON, item.time to Slot.of(item.slot))
        // Rebooking for another day moves it rather than leaving a copy behind.
        val moved = Edits.updateBooking(t, id) { it.copy(date = "2026-11-11", start = "17:00") }
        assertEquals(listOf("2026-11-11"), moved.days.filter { d -> d.plan.any { it.activityId == "sendero-macuco" } }.map { it.date })
        assertTrue(Edits.deleteBooking(moved, id).commitments.none { it.id == id })
    }

    @Test
    fun `two bookings with the same title get distinct ids and the trip stays valid`() {
        val (t1, a) = Edits.addBooking(trip, "2026-11-09", "Dinner", "20:00", null, null, null, null)
        val (t2, b) = Edits.addBooking(t1, "2026-11-10", "Dinner", "20:00", null, null, null, null)
        assertTrue(a != b)
        val errors = mutableListOf<String>()
        TripReader.check(t2, errors, mutableListOf())
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `changing the assistant's booking records which fields are mine`() {
        val t = Edits.updateBooking(trip, "boat-gran-aventura") { it.copy(start = "10:30", end = "12:00") }
        assertEquals(setOf("start", "end"), t.commitments.first { it.id == "boat-gran-aventura" }.userEdited.toSet())
        assertEquals("10:30", t.day(sun)!!.plan.single { it.activityId == "gran-aventura" }.time)
    }

    @Test
    fun `setting a time moves the item to that time's slot`() {
        val ref = Edits.ItemRef(sat, 0)
        val item = Edits.setTime(trip, ref, "19:30").day(sat)!!.plan.first { it.time == "19:30" }
        assertEquals("evening", item.slot)
    }

    @Test
    fun `status is stored only when it differs from planned`() {
        val ref = Edits.ItemRef(sat, 0)
        val done = Edits.setStatus(trip, ref, ItemStatus.DONE)
        assertEquals("done", done.day(sat)!!.plan[0].status)
        assertNull(Edits.setStatus(done, ref, ItemStatus.PROPOSED).day(sat)!!.plan[0].status)
    }
}
