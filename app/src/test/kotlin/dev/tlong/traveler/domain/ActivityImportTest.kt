package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.PlanItem
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ActivityImportTest {
    private val demo = Fixtures.trip("demo.trip.json").normalized()
    private val file = TripReader.readActivities(Fixtures.text("demo.activities.json")).file!!
    private val before = LocalDate.of(2027, 9, 1)

    @Test
    fun `each activity joins the stay it names by id or name, a duplicate is skipped, an unknown stay takes the fallback`() {
        val plan = ActivityImport.plan(demo, file, fallbackStayId = "palermo", today = before)
        assertEquals(
            listOf("punta-tombo" to "madryn", "feria-san-telmo" to "san-telmo", "tigre-delta" to "palermo"),
            plan.add.map { it.id to it.stayId },
        )
        assertEquals(listOf("Palermo Soho"), plan.skipped.map { it.name })
        assertEquals(setOf("tigre-delta"), plan.unmatched)
    }

    @Test
    fun `a file's day keeps what was done, booked or the traveler's own, and replaces the rest`() {
        listOf(
            PlanItem("avenida-alvear", "morning") to false,
            PlanItem("avenida-alvear", "morning", status = "done") to true,
            PlanItem("ride-along", "morning") to true,
            PlanItem("don-julio", "evening") to true,
            PlanItem("valdes-whales", "allday") to true,
        ).forEach { (item, kept) -> assertEquals(item.toString(), kept, ActivityImport.keeps(demo, item)) }
    }

    @Test
    fun `a file's day plans a duplicate as the existing activity and replaces that date's suggestions`() {
        val plan = ActivityImport.plan(demo, file, fallbackStayId = "palermo", today = before)
        assertEquals(listOf("palermo-soho"), plan.days.first { it.date == "2027-10-19" }.items.map { it.activityId })
        val after = ActivityImport.applyDays(demo.copy(activities = demo.activities + plan.add), plan.days)
        assertEquals(setOf("ride-along", "palermo-soho"), after.day("2027-10-19")!!.plan.map { it.activityId }.toSet())
        assertEquals(listOf("punta-tombo" to "07:30"), after.day("2027-10-24")!!.plan.map { it.activityId to it.time })
    }

    @Test
    fun `a city visited twice matches the visit still to come`() {
        val during = LocalDate.of(2027, 10, 21)
        assertEquals("palermo", ActivityImport.stayFor(demo, "buenos-aires", before)?.id)
        assertEquals("san-telmo", ActivityImport.stayFor(demo, "buenos-aires", during)?.id)
    }

    @Test
    fun `a taken id is renamed, never overwritten`() {
        val taken = demo.copy(activities = demo.activities + demo.activities.first().copy(id = "punta-tombo", name = "Something else"))
        val plan = ActivityImport.plan(taken, file, null, before)
        assertEquals("punta-tombo-2", plan.add.first { it.name == "Punta Tombo penguin colony" }.id)
    }

    @Test
    fun `without a trip id the trip whose stays fit wins`() {
        val other = Fixtures.iguazu
        assertEquals(demo.id, ActivityImport.pickTrip(file, listOf(other, demo), before)?.id)
    }
}
