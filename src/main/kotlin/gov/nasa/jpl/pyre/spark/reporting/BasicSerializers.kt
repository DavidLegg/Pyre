package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.withInverse
import gov.nasa.jpl.pyre.coals.compose
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.Serializer

object BasicSerializers {
    fun <T> nullable(serializer: Serializer<T>): Serializer<T?> = Serializer.Companion.of(
        InvertibleFunction.Companion.of(
        { if (it == null) JsonValue.JsonNull else serializer.serialize(it) },
        { if (it == JsonValue.JsonNull) null else serializer.deserialize(it) }
    ))

    val LONG_SERIALIZER = Serializer.Companion.of(JsonValue::JsonInt withInverse { (it as JsonValue.JsonInt).value })
    val INT_SERIALIZER = Serializer.Companion.of({ i: Int -> JsonValue.JsonInt(i.toLong()) } withInverse { (it as JsonValue.JsonInt).value.toInt() })
    val STRING_SERIALIZER = Serializer.Companion.of(JsonValue::JsonString withInverse { (it as JsonValue.JsonString).value })
    val BOOLEAN_SERIALIZER = Serializer.Companion.of(JsonValue::JsonBoolean withInverse { (it as JsonValue.JsonBoolean).value })
    val DOUBLE_SERIALIZER = Serializer.Companion.of(JsonValue::JsonDouble withInverse { (it as JsonValue.JsonDouble).value })
    val FLOAT_SERIALIZER = Serializer.Companion.of({ f: Float -> JsonValue.JsonDouble(f.toDouble()) } withInverse { (it as JsonValue.JsonDouble).value.toFloat() })

    inline fun <reified E : Enum<E>> enumSerializer() = Serializer.Companion.of(
        InvertibleFunction.Companion.of(
        { JsonValue.JsonString(it.name) },
        { enumValueOf<E>((it as JsonValue.JsonString).value) }
    ))

    fun <T> listSerializer(elementSerializer: Serializer<T>) = Serializer.Companion.of(
        { l: List<T> -> JsonValue.JsonArray(l.map(elementSerializer::serialize)) }
        withInverse
        { (it as JsonValue.JsonArray).values.map { elementSerializer.deserialize(it) } }
    )

    fun <T> Serializer<T>.asFunction() = InvertibleFunction.Companion.of(this::serialize, this::deserialize)
    fun <T, S> Serializer<T>.alias(equivalence: InvertibleFunction<S, T>) = Serializer.Companion.of(this.asFunction() compose equivalence)
}