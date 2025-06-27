package gov.nasa.jpl.pyre.flame.serialization

import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*

operator fun JsonValue.get(key: String): JsonValue = (this as JsonMap).values.getValue(key)
operator fun JsonValue.get(index: Int): JsonValue = (this as JsonArray).values[index]

fun JsonValue.asBoolean(): Boolean = (this as JsonBoolean).value
fun JsonValue.asString(): String = (this as JsonString).value
fun JsonValue.asLong(): Long = (this as JsonInt).value
fun JsonValue.asInt(): Int = (this as JsonInt).value.toInt()
fun JsonValue.asDouble(): Double = (this as JsonDouble).value
fun JsonValue.asFloat(): Float = (this as JsonDouble).value.toFloat()
