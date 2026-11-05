package dev.tlong.traveler.data

import dev.tlong.traveler.Fixtures
import dev.tlong.traveler.model.TripReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanOnlyTest {
    private val file = TripReader.readActivities(Fixtures.text("demo.activities.json")).file!!
    private val trip = TripReader.read(Fixtures.text("demo.trip.json")).trip!!

    @Test fun `a file with day plans goes through with the day's trip chosen`() {
        val p = dayPlanOnly(PendingImport.Activities("f", file, listOf(trip)), trip.id)
        assertEquals(trip.id, (p as PendingImport.Activities).preferTrip)
    }

    @Test fun `activities without days, trip files and backups are refused`() {
        listOf(
            PendingImport.Activities("f", file.copy(days = emptyList()), listOf(trip)),
            PendingImport.NewTrip("f", trip, emptyList()),
            PendingImport.AlreadyImported("f", trip, deleted = false),
        ).forEach { assertTrue(it.toString(), dayPlanOnly(it, trip.id) is PendingImport.Invalid) }
    }
}
