package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.identity
import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.withInverse
import gov.nasa.jpl.pyre.coals.compose
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer

object BasicSerializers {
    fun <T> nullable(serializer: Serializer<T>): Serializer<T?> = Serializer.of(
        InvertibleFunction.Companion.of(
        { if (it == null) JsonNull else serializer.serialize(it) },
        { if (it == JsonNull) null else serializer.deserialize(it) }
    ))

    val LONG_SERIALIZER = Serializer.of(::JsonInt withInverse { (it as JsonInt).value })
    val INT_SERIALIZER = Serializer.of({ i: Int -> JsonInt(i.toLong()) } withInverse { (it as JsonInt).value.toInt() })
    val STRING_SERIALIZER = Serializer.of(::JsonString withInverse { (it as JsonString).value })
    val BOOLEAN_SERIALIZER = Serializer.of(::JsonBoolean withInverse { (it as JsonBoolean).value })
    val DOUBLE_SERIALIZER = Serializer.of(::JsonDouble withInverse { (it as JsonDouble).value })
    val FLOAT_SERIALIZER = Serializer.of({ f: Float -> JsonDouble(f.toDouble()) } withInverse { (it as JsonDouble).value.toFloat() })

    inline fun <reified E : Enum<E>> enumSerializer() = Serializer.of(
        InvertibleFunction.Companion.of(
        { JsonString(it.name) },
        { enumValueOf<E>((it as JsonString).value) }
    ))

    fun <T> listSerializer(elementSerializer: Serializer<T>) = Serializer.of(
        { l: List<T> -> JsonArray(l.map(elementSerializer::serialize)) }
        withInverse
        { (it as JsonArray).values.map { elementSerializer.deserialize(it) } }
    )

    inline fun <reified T> arraySerializer(elementSerializer: Serializer<T>): Serializer<Array<T>> =
        listSerializer(elementSerializer).alias(Array<T>::toList withInverse List<T>::toTypedArray)

    val INT_ARRAY_SERIALIZER = listSerializer(INT_SERIALIZER)
        .alias(IntArray::toList withInverse List<Int>::toIntArray)
    val LONG_ARRAY_SERIALIZER = listSerializer(LONG_SERIALIZER)
        .alias(LongArray::toList withInverse List<Long>::toLongArray)
    val FLOAT_ARRAY_SERIALIZER = listSerializer(FLOAT_SERIALIZER)
        .alias(FloatArray::toList withInverse List<Float>::toFloatArray)
    val DOUBLE_ARRAY_SERIALIZER = listSerializer(DOUBLE_SERIALIZER)
        .alias(DoubleArray::toList withInverse List<Double>::toDoubleArray)
    val BOOLEAN_ARRAY_SERIALIZER = listSerializer(BOOLEAN_SERIALIZER)
        .alias(BooleanArray::toList withInverse List<Boolean>::toBooleanArray)

    fun <K, V> mapSerializer(keySerializer: InvertibleFunction<K, String>, valueSerializer: Serializer<V>): Serializer<Map<K, V>> =
        Serializer.of(
            InvertibleFunction.of(
            {
                JsonMap(it.entries.associate { (k, v) ->
                    Pair(keySerializer(k), valueSerializer.serialize(v))
                })
            },
            {
                (it as JsonMap).values.entries.associate { (k, v) ->
                    Pair(keySerializer.inverse()(k), valueSerializer.deserialize(v))
                }
            }
        ))

    fun <V> mapSerializer(valueSerializer: Serializer<V>): Serializer<Map<String, V>> =
        mapSerializer(identity(), valueSerializer)

    fun <T> Serializer<T>.asFunction() = InvertibleFunction.of(::serialize, ::deserialize)
    fun <T, S> Serializer<T>.alias(equivalence: InvertibleFunction<S, T>) = Serializer.of(this.asFunction() compose equivalence)
}