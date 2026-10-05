package dev.tlong.traveler.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * The validator's stamp: `validate_trip.py --stamp` writes `validated.hash`, a fingerprint of the
 * rest of the file, when the file passes `--complete`. Recomputing it here tells the traveler
 * whether the assistant really ran the validator on this exact file, which no prompt can enforce.
 * The canonical form must match `_canonical` in tools/validate_trip.py character for character:
 * no whitespace, keys sorted, numbers exactly as written, the same few string escapes.
 */
object Stamp {
    enum class Check { NONE, MATCHES, CHANGED }

    fun check(doc: JsonObject): Check {
        val stamped = ((doc["validated"] as? JsonObject)?.get("hash") as? JsonPrimitive)?.content ?: return Check.NONE
        return if (stamped == hash(doc)) Check.MATCHES else Check.CHANGED
    }

    fun hash(doc: JsonObject): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(JsonObject(doc - "validated")).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    internal fun canonical(e: JsonElement): String = when (e) {
        is JsonObject -> e.keys.sorted().joinToString(",", "{", "}") { quote(it) + ":" + canonical(e.getValue(it)) }
        is JsonArray -> e.joinToString(",", "[", "]") { canonical(it) }
        JsonNull -> "null"
        is JsonPrimitive -> if (e.isString) quote(e.content) else e.content
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' || c == '\\' -> append('\\').append(c)
            c.code < 0x20 -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}
