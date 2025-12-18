package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.provide
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.collections.iterator
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType

/**
 * A version of [Conditions] which keeps all values as-is in memory, rather than serializing them.
 *
 * Note that this will preserve object instances, which may cause poor behavior if those instances enclose references
 * to the simulation.
 * Put another way, conditions objects should be "pure data", immutable objects storing their own values.
 */
class JsonConditions private constructor(
    private var value: Any? = null,
    private var type: KType? = null,
    private val children: MutableMap<String, JsonConditions> = mutableMapOf(),
    private var locationDescription: Name? = null,
) : Conditions {
    constructor() : this(null)

    override fun <T> report(value: T, type: KType) {
        require(this.value == null) {
            "Duplicate fincon value reported at ${locationDescription ?: "<top level>"}"
        }
        this.value = value
        this.type = type
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> provide(type: KType): T? = this.value as T?

    override fun within(key: String): Conditions = children.getOrPut(key) {
        JsonConditions(locationDescription = locationDescription / key)
    }

    override fun incremental(block: FinconCollectingContext.() -> Unit) {
        val incrementalReports = mutableListOf<Any?>()
        object : FinconCollectingContext {
            override fun <T> report(value: T, type: KType) {
                incrementalReports += value
            }
        }.block()
        report<List<Any?>>(incrementalReports)
    }

    override fun <R> incremental(block: InconProvidingContext.() -> R): R? {
        return provide<List<Any?>>()?.let {
            var n = 0
            object : InconProvidingContext {
                @Suppress("UNCHECKED_CAST")
                override fun <T> provide(type: KType): T? =
                    it.getOrNull(n++) as T?

                override fun inconExists(): Boolean = n in it.indices
            }.block()
        }
    }

    override fun inconExists(): Boolean = value != null

    /**
     * Set this node's [locationDescription], and propagate the change to all children
     */
    private fun setLocation(location: Name) {
        locationDescription = location
        for ((key, child) in children) {
            child.setLocation(location / key)
        }
    }

    private class MemoryConditionsSerializer() : KSerializer<JsonConditions> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor(JsonConditions::class.qualifiedName!!) {
                // Type is looked up statically and resolved at runtime
                element("type", InvariantKTypeSerializer().descriptor, isOptional = true)
                // That type is used to find a deserializer for value itself
                element("value", ContextualSerializer(Any::class).descriptor, isOptional = true)
                element<Map<String, JsonConditions>>("children", isOptional = true)
            }

        @OptIn(ExperimentalStdlibApi::class)
        override fun serialize(encoder: Encoder, value: JsonConditions) {
            val structEncoder = encoder.beginStructure(descriptor)
            value.type?.let {
                structEncoder.encodeSerializableElement(descriptor, 0, InvariantKTypeSerializer(), it)
            }
            value.value?.let {
                structEncoder.encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(checkNotNull(value.type)), it)
            }
            value.children.takeUnless { it.isEmpty() }?.let {
                structEncoder.encodeSerializableElement(descriptor, 2, encoder.serializersModule.serializer(), it)
            }
            structEncoder.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): JsonConditions {
            val structDecoder = decoder.beginStructure(descriptor)
            var type: KType? = null
            var value: Any? = null
            var children: MutableMap<String, JsonConditions> = mutableMapOf()
            while (true) {
                when (val index = structDecoder.decodeElementIndex(descriptor)) {
                    0 -> type = structDecoder.decodeSerializableElement(descriptor, index, InvariantKTypeSerializer())
                    // TODO: This serializer inappropriately demands that "type" appear before "value"
                    // Use concrete type deserialized earlier to deserialize value
                    1 -> value = structDecoder.decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer(requireNotNull(type) { "type must be specified before value" }))
                    2 -> children = structDecoder.decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                    CompositeDecoder.Companion.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index $index")
                }
            }
            structDecoder.endStructure(descriptor)

            // Check that this node is consistent:
            require((type == null) == (value == null)) {
                "type and value must both be absent or both be present"
            }
            // Propagate a name change to all children, telling them where they are in the global conditions
            for ((key, child) in children) {
                child.setLocation(Name(key))
            }
            // Finally return the fully-constructed node
            return JsonConditions(value, type, children)
        }

    }

    private class InvariantKTypeSerializer() : KSerializer<KType> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(KType::class.qualifiedName!!) {
            element<String>("name")
            element<List<KType>>("typeArgs", isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: KType) {
            val structEncoder = encoder.beginStructure(descriptor)
            structEncoder.encodeStringElement(descriptor, 0, value.javaClass.name)
            if (value.arguments.isNotEmpty()) {
                structEncoder.encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(), value.arguments.map {
                    require(it.variance == KVariance.INVARIANT) {
                        "Only fully-invariant KTypes can be serialized."
                    }
                    // Null safety: it.type == null iff this argument is star-projected iff it.variance == null
                    // In such cases, require() above would have already failed.
                    it.type!!
                })
            }
            structEncoder.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): KType {
            val structDecoder = decoder.beginStructure(descriptor)
            var name: String? = null
            var typeArgs: List<KType> = emptyList()
            while (true) {
                when (val index = structDecoder.decodeElementIndex(descriptor)) {
                    0 -> name = structDecoder.decodeStringElement(descriptor, 0)
                    1 -> typeArgs = structDecoder.decodeSerializableElement(descriptor, 1, decoder.serializersModule.serializer())
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index $index")
                }
            }
            structDecoder.endStructure(descriptor)

            // Require a class name, and construct the concrete type using invariant projections.
            return Class.forName(requireNotNull(name) { "name is required" }).kotlin.createType(
                typeArgs.map { KTypeProjection(KVariance.INVARIANT, it) }
            )
        }
    }
}