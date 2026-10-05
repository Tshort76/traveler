package dev.tlong.traveler.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object TripJson {
    /** Reading is forgiving: unknown fields are reported as warnings by [unknownFields], not refused. */
    val reader = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Writing omits nulls and empty defaults so an exported file reads like a hand-written one. */
    val writer = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = false
    }

    /** Compact, deterministic form used for hashing and for comparing documents. */
    val compact = Json {
        explicitNulls = false
        encodeDefaults = false
    }

    fun encode(trip: Trip): String = writer.encodeToString(Trip.serializer(), trip)

    fun encodeCompact(trip: Trip): String = compact.encodeToString(Trip.serializer(), trip)

    fun decode(text: String): Trip = reader.decodeFromString(Trip.serializer(), text)

    fun toElement(trip: Trip): JsonObject = compact.encodeToJsonElement(Trip.serializer(), trip).jsonObject

    fun fromElement(element: JsonElement): Trip = reader.decodeFromJsonElement(Trip.serializer(), element)

    /**
     * Pull the JSON object out of text pasted from a chat: a ```json fence, or prose around a
     * single top-level object.
     */
    fun extractJson(text: String): String {
        val fence = Regex("```(?:json)?\\s*(\\{.*\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(text)
        if (fence != null) return fence.groupValues[1]
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        return if (first in 0 until last) text.substring(first, last + 1) else text
    }

    /** Field paths present in [element] that the model does not know — likely typos. */
    fun unknownFields(element: JsonElement, descriptor: SerialDescriptor = Trip.serializer().descriptor, path: String = ""): List<String> {
        val out = mutableListOf<String>()
        when {
            element is JsonObject && descriptor.kind == StructureKind.CLASS -> {
                for ((key, value) in element) {
                    val index = descriptor.getElementIndex(key)
                    val where = if (path.isEmpty()) key else "$path.$key"
                    if (index < 0) out += where
                    else out += unknownFields(value, descriptor.getElementDescriptor(index), where)
                }
            }
            element is JsonArray && descriptor.kind == StructureKind.LIST -> {
                val item = descriptor.getElementDescriptor(0)
                element.forEachIndexed { i, e -> out += unknownFields(e, item, "$path[$i]") }
            }
        }
        return out
    }

    /** Turn a kotlinx message into something a traveler can act on. */
    fun plainError(e: SerializationException): String {
        val msg = e.message.orEmpty()
        Regex("Field '(\\w+)' is required .*? at path: \\$\\.?([^\\s]*)").find(msg)?.let {
            val (field, at) = it.destructured
            return if (at.isBlank()) "missing required field '$field'" else "$at: missing required field '$field'"
        }
        Regex("Fields \\[(.*?)\\] are required .*? at path: \\$\\.?([^\\s]*)").find(msg)?.let {
            val (fields, at) = it.destructured
            val where = if (at.isBlank()) "" else "$at: "
            return "${where}missing required fields $fields"
        }
        Regex("at path: \\$\\.?([^\\s]*)").find(msg)?.let {
            val first = msg.lineSequence().first().substringBefore(" at path").substringBefore(" at offset")
            return "${it.groupValues[1]}: $first"
        }
        return msg.lineSequence().firstOrNull() ?: "could not be read"
    }
}

/** `detail` may be one string or a list of paragraphs; the model always holds a list. */
object ParagraphsSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) JsonArray(listOf(element)) else element
}
