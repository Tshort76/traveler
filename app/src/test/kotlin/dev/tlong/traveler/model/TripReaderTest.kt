package dev.tlong.traveler.model

import dev.tlong.traveler.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class TripReaderTest {

    @Test
    fun `every example reads without errors`() {
        val files = File(Fixtures.schemaDir, "examples").listFiles()!!.filter { it.name.endsWith(".trip.json") }
        assertTrue(files.size >= 4)
        files.forEach { f ->
            val r = TripReader.read(f.readText())
            assertTrue("${f.name}: ${r.errors}", r.ok)
        }
    }

    @Test
    fun `the demo activities file reads, passes the strict checks and carries a current stamp`() {
        val r = TripReader.readActivities(Fixtures.text("demo.activities.json"))
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(4, r.file!!.activities.size)
        assertEquals(emptyList<String>(), r.forAssistant)
        assertEquals(Stamp.Check.MATCHES, r.stamp)
    }

    @Test
    fun `an activities file is held to the activity checks, not to its stays`() {
        val bad = Fixtures.text("demo.activities.json").replace("\"stars\": 3", "\"stars\": 4")
        val r = TripReader.readActivities(bad)
        assertTrue(r.errors.toString(), r.errors.any { "stars: must be 1, 2 or 3" in it })
        assertTrue(r.errors.none { "unknown stay" in it })
    }

    @Test
    fun `every invalid fixture is refused for its own reason`() {
        val reasons = mapOf(
            "not-json" to "JSON is broken",
            "wrong-format" to "not a Traveler trip",
            "missing-stays" to "stays",
            "bad-slot" to "must be one of morning",
            "bad-timezone" to "not an IANA time zone",
            "unknown-activity-in-plan" to "unknown activity 'nope'",
            "bad-date" to "startDate",
            "depart-before-arrive" to "depart is before arrive",
            "duplicate-activity-id" to "duplicate id 'cherry-creek'",
            "unknown-stay" to "unknown stay 'boulder'",
            "bad-price" to "max is below amount",
            "price-not-object" to "stays[0] (denver).lodging.price: must be an object",
            "price-without-amount" to "lodging.price: missing required field 'amount'",
            "night-price-off-lodging" to "\"night\" is only for lodging",
            "negative-book-days" to "bookDaysBefore: a number of days, 0 or more",
        )
        val names = File(Fixtures.schemaDir, "invalid").listFiles()!!.map { it.name.removeSuffix(".trip.json") }.toSet()
        assertEquals("a new invalid fixture needs its reason here", reasons.keys, names)
        reasons.forEach { (name, expected) ->
            val r = TripReader.read(File(Fixtures.schemaDir, "invalid/$name.trip.json").readText())
            assertFalse(name, r.ok)
            assertTrue("$name: ${r.errors}", r.errors.any { expected in it })
        }
    }

    @Test
    fun `a written file carries format, version and revision`() {
        val o = TripJson.reader.parseToJsonElement(TripJson.encode(Fixtures.trip("minimal.trip.json"))).jsonObject
        assertEquals(TRIP_FORMAT, o["format"]?.jsonPrimitive?.content)
        assertTrue(o.containsKey("formatVersion") && o.containsKey("revision"))
    }

    @Test
    fun `json pasted from a chat with a fence reads`() {
        val pasted = "Here is the trip:\n```json\n" + Fixtures.text("minimal.trip.json") + "\n```\nEnjoy!"
        assertEquals("weekend-denver", TripReader.read(pasted).trip?.id)
    }

    @Test
    fun `unknown fields warn instead of failing`() {
        val text = Fixtures.text("minimal.trip.json").replace("\"timezone\"", "\"timezon\": \"x\", \"timezone\"")
        val r = TripReader.read(text)
        assertTrue(r.ok)
        assertTrue(r.forAssistant.toString(), r.forAssistant.any { "timezon" in it })
    }

    @Test
    fun `a field invented in many places is one warning with a count`() {
        val text = Fixtures.text("iguazu-short.trip.json").replace("\"currency\": \"USD\"", "\"currency\": \"USD\", \"per\": \"x\"")
        val lodging = TripReader.read(text).forAssistant.filter { it.startsWith("stays[].lodging.price.per:") }
        assertEquals(lodging.toString(), 1, lodging.size)
        assertTrue(lodging.single(), lodging.single().contains("(3 places)"))
    }

    @Test
    fun `an old priority goes back to the assistant as bookDaysBefore`() {
        val text = Fixtures.text("iguazu-short.trip.json").replaceFirst("\"bookDaysBefore\": 21", "\"priority\": 1")
        val notes = TripReader.read(text).forAssistant
        assertTrue(notes.toString(), notes.any { it.startsWith("priority is replaced by bookDaysBefore") && "stay bsas-1.lodging" in it })
        assertTrue(notes.toString(), "stay bsas-1.lodging: needs bookDaysBefore" in notes)
    }

    @Test
    fun `the strict checks find what validate_trip --complete finds in the examples`() {
        // validate_trip.py --complete passes demo and both iguazu files, fails minimal twice and long-synthetic 33 times.
        listOf("demo.trip.json", "iguazu-short.trip.json", "iguazu-short.r2.trip.json").forEach {
            assertEquals(it, emptyList<String>(), TripReader.read(Fixtures.text(it)).forAssistant)
        }
        assertEquals(
            listOf("stay denver.lodging: needs price and bookDaysBefore", "stay denver: needs climate {highC, lowC, flags?}: the typical daytime high and night low there for those dates"),
            TripReader.read(Fixtures.text("minimal.trip.json")).forAssistant,
        )
        val synthetic = TripReader.read(Fixtures.text("long-synthetic.trip.json")).forAssistant
        assertEquals(synthetic.toString(), 33, synthetic.size)
        assertEquals(13, synthetic.count { it.startsWith("transfer ") && "a ticketed transfer needs a booking" in it })
        assertEquals(5, synthetic.count { it.startsWith("stay ") && it.endsWith(".lodging: needs price and bookDaysBefore") })
        assertTrue(synthetic.toString(), "activities: 322 have no stars (1–3): lima-a01, lima-a02, lima-a03, lima-a04, lima-a05, lima-a06 and 316 more" in synthetic)
    }

    @Test
    fun `an unbooked flight written as a commitment, and a trip with no home airport, go back to the assistant`() {
        val trip = Fixtures.iguazu
        val text = TripJson.encode(trip.copy(
            startDate = "2026-11-04",
            commitments = trip.commitments + Commitment("fly-out", "Flight to Buenos Aires", "2026-11-04", kind = "booking"),
        ))
        val problems = TripReader.read(text).forAssistant
        assertTrue(problems.toString(), problems.any { it.startsWith("stays: the trip starts before the first stay") })
        assertTrue(problems.toString(), problems.any { "not a commitment: fly-out" in it })
    }

    @Test
    fun `an activity placed only by a search phrase goes back to the assistant, since the trip map needs coordinates`() {
        val trip = Fixtures.iguazu
        val first = trip.activities[0]
        val text = TripJson.encode(trip.copy(activities = listOf(first.copy(place = Place(query = "Somewhere, Puerto Iguazú"))) + trip.activities.drop(1)))
        val problems = TripReader.read(text).forAssistant
        assertTrue(problems.toString(), problems.any { it.startsWith("activities: 1 have no place.lat/lng") && first.id in it })
    }

    @Test
    fun `the validator's stamp matches the demo, and stops matching when the file changes`() {
        val demo = Fixtures.text("demo.trip.json")
        assertEquals(Stamp.Check.MATCHES, TripReader.read(demo).stamp)
        assertEquals(Stamp.Check.MATCHES, TripReader.read(demo.replace("\n", " ").replace("  ", " ")).stamp)
        assertEquals(Stamp.Check.CHANGED, TripReader.read(demo.replace("\"travelers\": 2", "\"travelers\": 3")).stamp)
        assertEquals(Stamp.Check.NONE, TripReader.read(Fixtures.text("iguazu-short.trip.json")).stamp)
    }

    @Test
    fun `an exported file drops the stamp, since the traveler's edits change what it vouched for`() {
        val demo = TripReader.read(Fixtures.text("demo.trip.json")).trip!!
        assertTrue(demo.validated?.hash != null)
        assertEquals(null, dev.tlong.traveler.domain.Export.tripFile(demo, demo).validated)
    }

    @Test
    fun `detail may be one string`() {
        val text = Fixtures.text("minimal.trip.json").replace(
            "\"stays\"", "\"activities\": [{\"id\": \"a\", \"stayId\": \"denver\", \"name\": \"A\", \"detail\": \"One paragraph.\"}], \"stays\"")
        assertEquals(listOf("One paragraph."), TripReader.read(text).trip!!.activities.single().detail)
    }

    @Test
    fun `a round trip through the writer is lossless`() {
        val trip = Fixtures.iguazu
        assertEquals(trip, TripJson.decode(TripJson.encode(trip)))
    }

    @Test
    fun `a flight's airports survive the writer`() {
        val written = TripJson.decode(TripJson.encode(TripReader.read(Fixtures.text("demo.trip.json")).trip!!))
        val leg = written.transfers.single { it.id == "fly-south" }
        assertEquals("Aeroparque Jorge Newbery (AEP)" to "Trelew Airport (REL)", leg.fromPlace?.name to leg.toPlace?.name)
    }
}
