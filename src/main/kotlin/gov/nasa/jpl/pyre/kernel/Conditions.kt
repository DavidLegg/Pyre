package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.foundation.reporting.Serialization.encodeToJsonElement
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.utilities.Serialization.decodeFromJsonElement
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.serialDescriptor
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

/*
 * Why is this file so complicated?
 *
 * Basically, it's for performance and ergonomics.
 * When copying a simulation in memory, I want to avoid serializing each value, even to the in-memory JsonElement representation.
 * Doing that serialization wastes time and memory if I'm about to immediately ask for the value back again.
 *
 * To support this in-memory copying use case, we have MemoryConditions.
 * Since I don't want to think about what "kind" of conditions I should collect, MemoryConditions can be serialized to disk too.
 * When deserializing, I don't have type information for individual values, so I can't deserialize to MemoryConditions.
 * Instead, I deserialize to JsonConditions, which defers the last step of deserializing values until provide is called,
 * which supplies us with the type information we need.
 *
 * The two motivating use cases thus look like this:
 * 1. In-memory copying: Simulation --save--> MemoryConditions --restore--> Simulation
 * 2. On-disk save/restore: Simulation --save--> MemoryConditions --encode--> file --decode--> JsonConditions --restore--> Simulation
 *
 * Finally, for ergonomics, all of this is wrapped in a single Conditions interface.
 * We get to save, restore, encode, and decode freely, and the system automatically uses a correct and performant type.
 */

/**
 * A snapshot of the state of a simulation, sufficient to fully restore that simulation.
 * Organized as a hierarchical map storing values of any type.
 */
@Serializable(with = Conditions.ConditionsSerializer::class)
sealed interface Conditions : FinconCollector, InconProvider {
    override fun within(key: String) : Conditions

    /**
     * A [Conditions] tree which stores the in-memory object.
     * When copying simulations in memory, using this type avoids serialization altogether.
     */
    private class MemoryConditions(
        var value: Any? = null,
        var type: KType? = null,
        val children: MutableMap<String, MemoryConditions> = mutableMapOf(),
        var location: Name? = null,
    ): Conditions {
        override fun within(key: String): Conditions =
            children.getOrPut(key) { MemoryConditions(location = location / key) }

        override fun <T> report(value: T, type: KType) {
            require(this.value == null) {
                "Duplicate fincon value reported at ${location ?: "<top level>"}"
            }
            this.value = value
            this.type = type
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> provide(type: KType): T? = value as T?

        override fun inconExists(): Boolean = value != null
    }

    /**
     * A [Conditions] object which stores the half-serialized [JsonElement].
     * That is, it stores the structure of the serialized value.
     * Useful for deserializing conditions, since we must wait for [provide] to indicate the actual type expected
     * before we can fully deserialize each value.
     */
    private class JsonConditions(
        var value: JsonElement? = null,
        val children: Map<String, JsonConditions> = mapOf(),
        location: Name? = null,
        var json: Json,
    ) : Conditions {
        var location = location
            set(value) {
                field = value
                for ((key, child) in children) child.location = value / key
            }
        override fun within(key: String): Conditions =
            children.getOrElse(key) { JsonConditions(location = location / key, json = json) }

        override fun <T> report(value: T, type: KType) {
            require(this.value == null) {
                "Duplicate fincon value reported at ${location ?: "<top level>"}"
            }
            this.value = json.encodeToJsonElement(type, value)
        }

        override fun <T> provide(type: KType): T? =
            value?.let { json.decodeFromJsonElement(type, it) }

        override fun inconExists(): Boolean = value != null
    }

    class ConditionsSerializer : KSerializer<Conditions> {
        // Because Conditions is a recursive type, we have to go against the Kotlin team's advice and directly implement SerialDescriptor.
        // This is so we can inject "this" object into the descriptor itself, making a reference loop that the
        // sanctioned builder would not allow us to create.
        // This descriptor is patterned off of kotlinx.serialization.internal.MapLikeDescriptor
        @OptIn(SealedSerializationApi::class)
        private object DESCRIPTOR : SerialDescriptor {
            override val serialName: String = JsonConditions::class.qualifiedName!!
            override val kind: SerialKind = StructureKind.MAP
            override val elementsCount: Int = 4

            override fun getElementName(index: Int): String = index.toString()
            override fun getElementIndex(name: String): Int =
                name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid map index")
            override fun getElementAnnotations(index: Int): List<Annotation> {
                require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
                return emptyList()
            }
            @OptIn(ExperimentalSerializationApi::class)
            override fun getElementDescriptor(index: Int): SerialDescriptor {
                require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
                return when (index) {
                    // The second key/value pair is "$": value
                    0 -> serialDescriptor<String>()
                    1 -> ContextualSerializer(Any::class).descriptor
                    // The other keys are all the children of this node, collapsed into this node.
                    else -> if (index % 2 == 0) serialDescriptor<String>() else this
                }
            }
            override fun isElementOptional(index: Int): Boolean {
                require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
                return index <= 1
            }
        }
        override val descriptor: SerialDescriptor = DESCRIPTOR

        override fun serialize(encoder: Encoder, value: Conditions) = encoder.encodeStructure(descriptor) {
            when (value) {
                is JsonConditions -> {
                    value.value?.let {
                        encodeStringElement(descriptor, 0, "$")
                        encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer<JsonElement>(), it)
                    }
                    var index = 2
                    for ((key, child) in value.children) {
                        encodeStringElement(descriptor, index++, key)
                        encodeSerializableElement(descriptor, index++, this@ConditionsSerializer, child)
                    }
                }
                is MemoryConditions -> {
                    value.value?.let {
                        val t = checkNotNull(value.type)
                        encodeStringElement(descriptor, 0, "$")
                        encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(t), it)
                    }
                    var index = 2
                    for ((key, child) in value.children) {
                        encodeStringElement(descriptor, index++, key)
                        encodeSerializableElement(descriptor, index++, this@ConditionsSerializer, child)
                    }
                }
            }
        }

        override fun deserialize(decoder: Decoder): Conditions = decoder.decodeStructure(descriptor) {
            var value: JsonElement? = null
            val children: MutableMap<String, JsonConditions> = mutableMapOf()

            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val key = decodeStringElement(descriptor, index)
                require (decodeElementIndex(descriptor) == index + 1) { "Malformed serialization" }
                when (key) {
                    // For the special key "$", decode a generic JsonElement and store it
                    "$" -> value = decodeSerializableElement(descriptor, index + 1, decoder.serializersModule.serializer<JsonElement>())
                    // For a general element, decode a new JsonConditions node, and apply a location update to tell it where it is.
                    else -> children[key] = (decodeSerializableElement(descriptor, index + 1, this@ConditionsSerializer) as JsonConditions)
                        .apply { location = Name(key) }
                }
            }

            // Finally assemble the JsonConditions node, squirreling away the Json to fully deserialize values later
            JsonConditions(value, children, json = (decoder as JsonDecoder).json)
        }

    }

    companion object {
        internal fun construct(): Conditions = MemoryConditions()

        fun Conditions.within(keys: Sequence<String>): Conditions {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun Conditions.within(vararg keys: String): Conditions = within(keys.asSequence())
    }
}

fun Conditions(): Conditions = Conditions.construct()
