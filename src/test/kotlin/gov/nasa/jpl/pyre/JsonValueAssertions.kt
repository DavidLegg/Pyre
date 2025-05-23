package gov.nasa.jpl.pyre

import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*

// The getter functions below use (_ as T?) instead of (_ as? T)
// This lets us return null if the requested value is missing,
// but fail loudly if it's the wrong type.

fun JsonValue.string(): String? = (this as JsonString?)?.value
fun JsonValue.boolean(): Boolean? = (this as JsonBoolean?)?.value
fun JsonValue.int(): Long? = (this as JsonInt?)?.value
fun JsonValue.double(): Double? = (this as JsonDouble?)?.value

fun JsonValue.get(vararg keys: String): JsonValue? {
    var result: JsonValue? = this
    for (key in keys) result = (result as JsonMap?)?.values?.get(key)
    return result
}
fun JsonValue.string(vararg keys: String): String? = (get(*keys) as JsonString?)?.value
fun JsonValue.boolean(vararg keys: String): Boolean? = (get(*keys) as JsonBoolean?)?.value
fun JsonValue.int(vararg keys: String): Long? = (get(*keys) as JsonInt?)?.value
fun JsonValue.double(vararg keys: String): Double? = (get(*keys) as JsonDouble?)?.value
fun JsonValue.within(vararg keys: String, block: JsonValue.() -> Unit) {
    get(*keys)!!.block()
}

fun JsonValue.get(vararg indices: Int): JsonValue? {
    var result: JsonValue? = this
    for (index in indices) result = (result as JsonArray?)?.values?.get(index)
    return result
}
fun JsonValue.string(vararg indices: Int): String? = (get(*indices) as JsonString?)?.value
fun JsonValue.boolean(vararg indices: Int): Boolean? = (get(*indices) as JsonBoolean?)?.value
fun JsonValue.int(vararg indices: Int): Long? = (get(*indices) as JsonInt?)?.value
fun JsonValue.double(vararg indices: Int): Double? = (get(*indices) as JsonDouble?)?.value
fun JsonValue.within(vararg indices: Int, block: JsonValue.() -> Unit) {
    get(*indices)!!.block()
}
fun JsonValue.forEach(block: JsonValue.() -> Unit) {
    (this as JsonArray).values.forEach(block)
}

class JsonArrayScope(private val subject: JsonArray) {
    var currentIndex: Int = 0
    fun element(block: JsonValue.() -> Unit) = subject.values[currentIndex++].block()
    fun atEnd() = subject.values.size <= currentIndex
}
fun JsonValue.array(block: JsonArrayScope.() -> Unit) {
    JsonArrayScope(this as JsonArray).block()
}
