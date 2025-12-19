package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.provide
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlin.collections.emptyList
import kotlin.collections.iterator
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

/**
 * A version of [Conditions] which keeps all values as-is in memory, rather than serializing them.
 *
 * Note that this will preserve object instances, which may cause poor behavior if those instances enclose references
 * to the simulation.
 * Put another way, conditions objects should be "pure data", immutable objects storing their own values.
 */
@Serializable(with = JsonConditions.JsonConditionsSerializer::class)
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

    class JsonConditionsSerializer() : KSerializer<JsonConditions> {
        // Because JsonConditions is a recursive type, we have to go against the Kotlin team's advice and directly implement SerialDescriptor.
        // This is so we can inject "this" object into the descriptor itself, making a reference loop that the
        // sanctioned builder would not allow us to create.
        // This descriptor is patterned off of kotlinx.serialization.internal.MapLikeDescriptor
        @OptIn(SealedSerializationApi::class)
        private object DESCRIPTOR : SerialDescriptor {
            override val serialName: String = JsonConditions::class.qualifiedName!!
            override val kind: SerialKind = StructureKind.MAP
            override val elementsCount: Int = 6

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
                    // TODO: See if we can avoid serializing type, and defer until providing the value.
                    //   This may require an asymmetric serialization scheme.
                    // The first key/value pair is "__type": type
                    0 -> serialDescriptor<String>()
                    1 -> InvariantKTypeSerializer().descriptor
                    // The second key/value pair is "$": value
                    2 -> serialDescriptor<String>()
                    3 -> ContextualSerializer(Any::class).descriptor
                    // The other keys are all the children of this node, collapsed into this node.
                    else -> if (index % 2 == 0) serialDescriptor<String>() else this
                }
            }
            override fun isElementOptional(index: Int): Boolean {
                require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
                return index <= 3
            }
        }

        override val descriptor: SerialDescriptor = DESCRIPTOR

        override fun serialize(encoder: Encoder, value: JsonConditions) = encoder.encodeStructure(descriptor) {
            if (value.value != null) {
                val t = checkNotNull(value.type)
                val v = checkNotNull(value.value)
                encodeStringElement(descriptor, 0, "__type")
                encodeSerializableElement(descriptor, 1, InvariantKTypeSerializer(), t)
                encodeStringElement(descriptor, 2, "$")
                encodeSerializableElement(descriptor, 3, encoder.serializersModule.serializer(t), v)
            }
            var index = 4
            for ((key, child) in value.children) {
                encodeStringElement(descriptor, index++, key)
                encodeSerializableElement(descriptor, index++, this@JsonConditionsSerializer, child)
            }
        }

        override fun deserialize(decoder: Decoder): JsonConditions = decoder.decodeStructure(descriptor) {
            var type: KType? = null
            var value: Any? = null
            val children: MutableMap<String, JsonConditions> = mutableMapOf()

            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val key = decodeStringElement(descriptor, index)
                require (decodeElementIndex(descriptor) == index + 1) { "Malformed serialization" }
                when (key) {
                    "__type" -> type = decodeSerializableElement(descriptor, index + 1, InvariantKTypeSerializer())
                    // TODO: This serializer inappropriately demands that "__type" appear before "value"
                    // Use concrete type deserialized earlier to deserialize value
                    "$" -> value = decodeSerializableElement(descriptor, index + 1, decoder.serializersModule.serializer(
                        requireNotNull(type) { "type (__type) must be specified before value ($)" }))
                    // For a general element, decode a new JsonConditions node, and apply a location update to tell it where it is.
                    else -> children[key] = decodeSerializableElement(descriptor, index + 1, this@JsonConditionsSerializer)
                            .apply { setLocation(Name(key)) }
                }
            }

            // Check that this node is consistent:
            require((type == null) == (value == null)) {
                "type and value must both be absent or both be present"
            }
            // Finally return the fully-constructed node
            JsonConditions(value, type, children)
        }
    }

    private class InvariantKTypeSerializer() : KSerializer<KType> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(KType::class.qualifiedName!!) {
            element<String>("name")
            element<List<KType>>("typeArgs", isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: KType) {
            val structEncoder = encoder.beginStructure(descriptor)
            structEncoder.encodeStringElement(descriptor, 0, requireNotNull(value.jvmErasure.java.name) {
                "Only concrete classes can be serialized."
            })
            if (value.arguments.isNotEmpty()) {
                structEncoder.encodeSerializableElement(descriptor, 1, ListSerializer(this), value.arguments.map {
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
                    1 -> typeArgs = structDecoder.decodeSerializableElement(descriptor, 1, ListSerializer(this))
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