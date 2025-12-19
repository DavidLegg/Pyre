package gov.nasa.jpl.pyre.kernel

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
import kotlin.reflect.typeOf

/*
 * Why is this file so complicated?
 *
 * Basically, it's for performance and ergonomics.
 * When copying a simulation in memory, I want to avoid serializing each value, even to the in-memory JsonElement representation.
 * Doing that serialization wastes time and memory if I'm about to immediately ask for the value back again.
 *
 * To support this in-memory copying use case, we have MemoryMutableSnapshot.
 * Since I don't want to think about what "kind" of conditions I should collect, MemoryMutableSnapshot can be serialized to disk too.
 * When deserializing, I don't have type information for individual values, so I can't deserialize to MemoryMutableSnapshot.
 * Instead, I deserialize to JsonMutableSnapshot, which defers the last step of deserializing values until provide is called,
 * which supplies us with the type information we need.
 *
 * The two motivating use cases thus look like this:
 * 1. In-memory copying: Simulation --save--> MemoryMutableSnapshot --restore--> Simulation
 * 2. On-disk save/restore: Simulation --save--> MemoryMutableSnapshot --encode--> file --decode--> JsonMutableSnapshot --restore--> Simulation
 *
 * Finally, for ergonomics, all of this is wrapped in a single MutableSnapshot interface.
 * We get to save, restore, encode, and decode freely, and the system automatically uses a correct and performant type.
 * By and large, you should just use Snapshot, constructed by calling "save" on a simulation.
 */

/**
 * Read-only view of [MutableSnapshot], a snapshot of a simulation we can use to restore a functionally identical simulation.
 */
@Serializable(with = MutableSnapshot.SnapshotSerializer::class)
sealed interface Snapshot {
    fun within(key: String): Snapshot
    fun <T> provide(type: KType): T?
    fun inconExists(): Boolean

    companion object {
        fun Snapshot.within(keys: Sequence<String>): Snapshot {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun Snapshot.within(vararg keys: String): Snapshot = within(keys.asSequence())
        inline fun <reified T> Snapshot.provide(vararg keys: String): T? = within(*keys).provide(typeOf<T>())
        inline fun <reified T> Snapshot.provide(): T? = provide(typeOf<T>())
    }
}

/**
 * A snapshot of the state of a simulation, sufficient to fully restore that simulation.
 * Organized as a hierarchical map storing values of any type.
 */
sealed interface MutableSnapshot : Snapshot {
    override fun within(key: String) : MutableSnapshot
    fun <T> report(value: T, type: KType)

    /**
     * A [MutableSnapshot] tree which stores the in-memory object.
     * When copying simulations in memory, using this type avoids serialization altogether.
     */
    private class MemoryMutableSnapshot(
        var value: Any? = null,
        var type: KType? = null,
        val children: MutableMap<String, MemoryMutableSnapshot> = mutableMapOf(),
        var location: Name? = null,
    ): MutableSnapshot {
        override fun within(key: String): MutableSnapshot =
            children.getOrPut(key) { MemoryMutableSnapshot(location = location / key) }

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
     * A [MutableSnapshot] object which stores the half-serialized [JsonElement].
     * That is, it stores the structure of the serialized value.
     * Useful for deserializing conditions, since we must wait for [provide] to indicate the actual type expected
     * before we can fully deserialize each value.
     */
    private class JsonSnapshot(
        var value: JsonElement? = null,
        val children: Map<String, JsonSnapshot> = mapOf(),
        location: Name? = null,
        var json: Json,
    ) : Snapshot {
        var location = location
            set(value) {
                field = value
                for ((key, child) in children) child.location = value / key
            }
        override fun within(key: String): Snapshot =
            children.getOrElse(key) { JsonSnapshot(location = location / key, json = json) }

        override fun <T> provide(type: KType): T? =
            value?.let { json.decodeFromJsonElement(type, it) }

        override fun inconExists(): Boolean = value != null
    }

    class SnapshotSerializer : KSerializer<Snapshot> {
        // Because Snapshot is a recursive type, we have to go against the Kotlin team's advice and directly implement SerialDescriptor.
        // This is so we can inject "this" object into the descriptor itself, making a reference loop that the
        // sanctioned builder would not allow us to create.
        // This descriptor is patterned off of kotlinx.serialization.internal.MapLikeDescriptor
        @OptIn(SealedSerializationApi::class)
        private object DESCRIPTOR : SerialDescriptor {
            override val serialName: String = JsonSnapshot::class.qualifiedName!!
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

        override fun serialize(encoder: Encoder, value: Snapshot) = encoder.encodeStructure(descriptor) {
            val children = when (value) {
                is JsonSnapshot -> {
                    value.value?.let {
                        encodeStringElement(descriptor, 0, "$")
                        encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer<JsonElement>(), it)
                    }
                    value.children
                }
                is MemoryMutableSnapshot -> {
                    value.value?.let {
                        val t = checkNotNull(value.type)
                        encodeStringElement(descriptor, 0, "$")
                        encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(t), it)
                    }
                    value.children
                }
            }
            var index = 2
            for ((key, child) in children) {
                encodeStringElement(descriptor, index++, key)
                encodeSerializableElement(descriptor, index++, this@SnapshotSerializer, child)
            }
        }

        override fun deserialize(decoder: Decoder): Snapshot = decoder.decodeStructure(descriptor) {
            var value: JsonElement? = null
            val children: MutableMap<String, JsonSnapshot> = mutableMapOf()

            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val key = decodeStringElement(descriptor, index)
                require (decodeElementIndex(descriptor) == index + 1) { "Malformed serialization" }
                when (key) {
                    // For the special key "$", decode a generic JsonElement and store it
                    "$" -> value = decodeSerializableElement(descriptor, index + 1, decoder.serializersModule.serializer<JsonElement>())
                    // For a general element, decode a new JsonMutableSnapshot node, and apply a location update to tell it where it is.
                    else -> children[key] = (decodeSerializableElement(descriptor, index + 1, this@SnapshotSerializer) as JsonSnapshot)
                        .apply { location = Name(key) }
                }
            }

            // Finally assemble the JsonSnapshot node, squirreling away the Json to fully deserialize values later
            JsonSnapshot(value, children, json = (decoder as JsonDecoder).json)
        }

    }

    companion object {
        internal fun construct(): MutableSnapshot = MemoryMutableSnapshot()

        fun MutableSnapshot.within(keys: Sequence<String>): MutableSnapshot {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun MutableSnapshot.within(vararg keys: String): MutableSnapshot = within(keys.asSequence())
        inline fun <reified T> MutableSnapshot.report(value: T) = report(value, typeOf<T>())
    }
}

fun MutableSnapshot(): MutableSnapshot = MutableSnapshot.construct()
