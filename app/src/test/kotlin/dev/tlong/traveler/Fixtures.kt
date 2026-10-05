package dev.tlong.traveler

import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.model.TripReader
import java.io.File

/** The shared trip files in ../schema, also used by tools/test_validate_trip.py. */
object Fixtures {
    val schemaDir = File(System.getProperty("traveler.schemaDir") ?: "../schema")

    fun text(name: String) = File(schemaDir, "examples/$name").readText()

    fun trip(name: String): Trip = TripReader.read(text(name)).also {
        check(it.ok) { "$name: ${it.errors}" }
    }.trip!!

    val iguazu get() = trip("iguazu-short.trip.json")
    val iguazuR2 get() = trip("iguazu-short.r2.trip.json")
    val long get() = trip("long-synthetic.trip.json")
}
