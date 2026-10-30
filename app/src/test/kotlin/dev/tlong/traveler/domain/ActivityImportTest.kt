package dev.tlong.traveler.domain

import dev.tlong.traveler.Fixtures
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
