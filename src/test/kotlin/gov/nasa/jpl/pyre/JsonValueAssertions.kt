package gov.nasa.jpl.pyre

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// The getter functions below use (_ as T?) instead of (_ as? T)
// This lets us return null if the requested value is missing,
// but fail loudly if it's the wrong type.

fun JsonElement.string(): String? = this.jsonPrimitive.content
fun JsonElement.boolean(): Boolean? = this.jsonPrimitive.boolean
fun JsonElement.int(): Long? = this.jsonPrimitive.long
fun JsonElement.double(): Double? = this.jsonPrimitive.double

fun JsonElement.get(vararg keys: String): JsonElement? {
    var result: JsonElement? = this
    for (key in keys) result = result?.jsonObject?.get(key)
    return result
}
fun JsonElement.string(vararg keys: String): String? = get(*keys)?.string()
fun JsonElement.boolean(vararg keys: String): Boolean? = get(*keys)?.boolean()
fun JsonElement.int(vararg keys: String): Long? = get(*keys)?.int()
fun JsonElement.double(vararg keys: String): Double? = get(*keys)?.double()
fun JsonElement.within(vararg keys: String, block: JsonElement.() -> Unit) {
    assertNotNull(get(*keys)).block()
}
fun JsonElement.assertNull(vararg keys: String) {
    requireNotNull(get(*keys)?.jsonNull)
}
fun JsonElement.assertNullOrMissing(vararg keys: String) {
    get(*keys)?.jsonNull
}

fun JsonElement.get(vararg indices: Int): JsonElement? {
    var result: JsonElement? = this
    for (index in indices) result = result?.jsonArray?.get(index)
    return result
}
fun JsonElement.string(vararg indices: Int): String? = get(*indices)?.string()
fun JsonElement.boolean(vararg indices: Int): Boolean? = get(*indices)?.boolean()
fun JsonElement.int(vararg indices: Int): Long? = get(*indices)?.int()
fun JsonElement.double(vararg indices: Int): Double? = get(*indices)?.double()
fun JsonElement.within(vararg indices: Int, block: JsonElement.() -> Unit) {
    get(*indices)!!.block()
}
fun JsonElement.forEach(block: JsonElement.() -> Unit) {
    jsonArray.forEach(block)
}
fun JsonElement.assertNull(vararg indices: Int) {
    requireNotNull(get(*indices)?.jsonNull)
}
fun JsonElement.assertNullOrMissing(vararg indices: Int) {
    get(*indices)?.jsonNull
}

class JsonArrayScope(private val subject: JsonArray) {
    var currentIndex: Int = 0
    fun element(block: JsonElement.() -> Unit) = subject[currentIndex++].block()
    fun atEnd() = subject.size <= currentIndex
}
fun JsonElement.array(block: JsonArrayScope.() -> Unit) {
    JsonArrayScope(this.jsonArray).block()
}
