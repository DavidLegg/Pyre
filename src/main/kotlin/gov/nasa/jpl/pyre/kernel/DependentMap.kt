package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.Serialization.decodeFromJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/*
 * Why is this file needed? Why is it so complicated?
 *
 * Basically, it's for performance and ergonomics.
 * When copying a simulation in memory, I want to avoid serializing each value, even to the in-memory JsonElement representation.
 * Doing that serialization wastes time and memory if I'm about to immediately ask for the value back again.
 *
 * MemoryDependentMap supports the in-memory only use case.
 * Since I don't want to think about what "kind" of conditions I should collect, MemoryDependentMap can be serialized too.
 * When deserializing, I don't have type information for individual values, so I can't deserialize to MemoryDependentMap.
 * Instead, I deserialize to JsonDependentMap, which defers the last step of deserializing values until `get` is called,
 * which supplies us with the type information we need.
 *
 * The two motivating use cases thus look like this:
 * 1. In-memory copying: Simulation --save--> MemoryDependentMap --restore--> Simulation
 * 2. On-disk save/restore: Simulation --save--> MemoryDependentMap --encode--> file --decode--> JsonDependentMap --restore--> Simulation
 *
 * Finally, for ergonomics, all of this is wrapped in a single DependentMap interface.
 * We get to save, restore, encode, and decode freely, and the system automatically uses a correct and performant type.
 * By and large, you should just use DependentMap, constructed by calling "save" on a simulation.
 */

/**
 * A map where the value varies between keys.
 *
 * Supports efficient (de)serialization.
 */
@Serializable(with = DependentMap.DependentMapSerializer::class)
sealed interface DependentMap {
    fun <V> get(key: Name, valueType: KType): V?

    companion object {
        inline fun <reified V> DependentMap.get(key: Name): V? = get(key, typeOf<V>())

        internal fun construct(): MutableDependentMap = MemoryDependentMap()
    }

    /**
     * A [MutableDependentMap] which stores the in-memory object.
     * When copying simulations in memory, using this type avoids serialization altogether.
     */
    private class MemoryDependentMap : MutableDependentMap {
        val base: MutableMap<Name, Pair<Any?, KType>> = mutableMapOf()

        override fun <V> put(key: Name, value: V, valueType: KType) {
            base[key] = value to valueType
        }

        @Suppress("UNCHECKED_CAST")
        override fun <V> get(key: Name, valueType: KType): V? {
            return base[key]?.first as V?
        }
    }

    /**
     * A [DependentMap] which stores the half-serialized [kotlinx.serialization.json.JsonElement].
     * That is, it stores the structure of the serialized value.
     * Useful for deserializing cells, since we must wait for [get] to indicate the actual type expected
     * before we can fully deserialize each value.
     */
    private class JsonDependentMap(
        val base: Map<Name, JsonElement>,
        val json: Json,
    ) : DependentMap {
        override fun <V> get(key: Name, valueType: KType): V? =
            base[key]?.let { json.decodeFromJsonElement(valueType, it) }
    }

    class DependentMapSerializer : KSerializer<DependentMap> {
        override val descriptor: SerialDescriptor = SerialDescriptor(
            DependentMap::class.qualifiedName!!,
            serializer<Map<Name, JsonElement>>().descriptor)

        override fun serialize(encoder: Encoder, value: DependentMap) {
            encoder.encodeStructure(descriptor) {
                when (value) {
                    is JsonDependentMap -> {
                        for ((name, json) in value.base.entries.sortedBy { it.key.toString() }) {
                            encodeStringElement(descriptor, 0, name.toString())
                            encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer<JsonElement>(), json)
                        }
                    }
                    is MemoryDependentMap -> {
                        for ((name, valuePair) in value.base.entries.sortedBy { it.key.toString() }) {
                            val (value, type) = valuePair
                            encodeStringElement(descriptor, 0, name.toString())
                            encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(type), value)
                        }
                    }
                }
            }
        }

        override fun deserialize(decoder: Decoder): DependentMap = decoder.decodeStructure(descriptor) {
            val result: MutableMap<Name, JsonElement> = mutableMapOf()
            val nameSerializer = decoder.serializersModule.serializer<Name>()
            val jsonSerializer = decoder.serializersModule.serializer<JsonElement>()
            while (true) {
                val keyIndex = decodeElementIndex(descriptor)
                if (keyIndex == CompositeDecoder.DECODE_DONE) break
                val key = decodeSerializableElement(descriptor, keyIndex, nameSerializer)
                val valueIndex = decodeElementIndex(descriptor)
                require (valueIndex == keyIndex + 1) { "Malformed serialization" }
                val value = decodeSerializableElement(descriptor, valueIndex, jsonSerializer)
                result[key] = value
            }
            JsonDependentMap(result, json = (decoder as JsonDecoder).json)
        }
    }
}

sealed interface MutableDependentMap : DependentMap {
    fun <V> put(key: Name, value: V, valueType: KType)

    companion object {
        inline fun <reified V> MutableDependentMap.put(key: Name, value: V) = put(key, value, typeOf<V>())
    }
}

fun MutableDependentMap(): MutableDependentMap = DependentMap.construct()
