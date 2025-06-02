package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.withInverse
import gov.nasa.jpl.pyre.coals.compose
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer

object BasicSerializers {
    fun <T> nullable(serializer: Serializer<T>): Serializer<T?> = Serializer.of(InvertibleFunction.of(
        { if (it == null) JsonNull else serializer.serialize(it) },
        { if (it == JsonNull) null else serializer.deserialize(it) }
    ))

    val LONG_SERIALIZER = Serializer.of(::JsonInt withInverse { (it as JsonInt).value })
    val INT_SERIALIZER = Serializer.of({ i: Int -> JsonInt(i.toLong()) } withInverse { (it as JsonInt).value.toInt() })
    val STRING_SERIALIZER = Serializer.of(::JsonString withInverse { (it as JsonString).value })
    val BOOLEAN_SERIALIZER = Serializer.of(::JsonBoolean withInverse { (it as JsonBoolean).value })
    val DOUBLE_SERIALIZER = Serializer.of(::JsonDouble withInverse { (it as JsonDouble).value })
    val FLOAT_SERIALIZER = Serializer.of({ f: Float -> JsonDouble(f.toDouble()) } withInverse { (it as JsonDouble).value.toFloat() })

    inline fun <reified E : Enum<E>> enumSerializer() = Serializer.of(InvertibleFunction.of(
        { JsonString(it.name) },
        { enumValueOf<E>((it as JsonString).value) }
    ))

    fun <T> listSerializer(elementSerializer: Serializer<T>) = Serializer.of(
        { l: List<T> -> JsonArray(l.map(elementSerializer::serialize))}
        withInverse
        { (it as JsonArray).values.map { elementSerializer.deserialize(it) } }
    )

    fun <T> Serializer<T>.asFunction() = InvertibleFunction.of(this::serialize, this::deserialize)
    fun <T, S> Serializer<T>.alias(equivalence: InvertibleFunction<S, T>) = Serializer.of(this.asFunction() compose equivalence)
}
