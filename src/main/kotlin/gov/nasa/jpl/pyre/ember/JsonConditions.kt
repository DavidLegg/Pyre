package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.ember.InconProvidingContext.Companion.provide
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.reflect.KType

// Paradoxically, JsonConditions is not directly @Serializable
// The caller wishing to serialize/deserialize it must provide the JsonFormat to use
/**
 * JSON-based [Conditions], fulfilling both [FinconCollector] and [InconProvider] roles.
 *
 * Note that the jsonFormat is not preserved through serialization.
 * Use [gov.nasa.jpl.pyre.ember.JsonConditions.copy] to add jsonFormat to deserialized conditions.
 */
@Serializable(with = JsonConditions.JsonConditionsSerializer::class)
class JsonConditions private constructor(
    private var value: JsonElement?,
    private val children: MutableMap<String, JsonConditions>,
    private val jsonFormat: Json,
) : Conditions {

    constructor(jsonFormat: Json = Json) : this(null, mutableMapOf(), jsonFormat)

    fun copy(jsonFormat: Json = this.jsonFormat): JsonConditions {
        return JsonConditions(
            value = value,
            children = children.mapValuesTo(mutableMapOf()) { it.value.copy(jsonFormat) },
            jsonFormat = jsonFormat,
        )
    }

    override fun within(key: String): Conditions = children.getOrPut(key) { JsonConditions(jsonFormat) }

    override fun incremental(block: FinconCollectingContext.() -> Unit) {
        val incrementalReports = mutableListOf<JsonElement>()
        object : FinconCollectingContext {
            override fun <T> report(value: T, type: KType) {
                incrementalReports += encode(value, type)
            }
        }.block()
        report(incrementalReports)
    }

    override fun <R> incremental(block: InconProvidingContext.() -> R): R? {
        return provide<List<JsonElement>>()?.let {
            var n = 0
            object : InconProvidingContext {
                override fun <T> provide(type: KType): T? =
                    it.getOrNull(n++)?.let { decode(it, type) }
            }.block()
        }
    }

    override fun <T> report(value: T, type: KType) {
        // TODO: Track the location being reported to, for better error reporting.
        require(this.value == null) { "Duplicate final condition reported" }
        this.value = encode(value, type)
    }

    override fun <T> provide(type: KType): T? {
        return value?.let { decode(it, type) }
    }

    private fun <T> encode(value: T, type: KType): JsonElement =
        jsonFormat.encodeToJsonElement(jsonFormat.serializersModule.serializer(type), value)

    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(value: JsonElement, type: KType): T =
        jsonFormat.decodeFromJsonElement(jsonFormat.serializersModule.serializer(type), value) as T

    private class JsonConditionsSerializer: KSerializer<JsonConditions> {
        private val baseSerializer = serializer<JsonObject>()

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
            JsonConditions::class.qualifiedName!!,
            baseSerializer.descriptor)

        override fun serialize(encoder: Encoder, value: JsonConditions) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(buildJsonObject {
                value.value?.let { put("$", it) }
                for ((key, child) in value.children) {
                    put(key, encoder.json.encodeToJsonElement(this@JsonConditionsSerializer, child))
                }
            })
        }

        override fun deserialize(decoder: Decoder): JsonConditions {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject

            var value: JsonElement? = null
            val children = mutableMapOf<String, JsonConditions>()

            for ((key, element) in jsonObject) {
                if (key == "$") {
                    value = element
                } else {
                    children[key] = decoder.json.decodeFromJsonElement(this, element)
                }
            }

            return JsonConditions(value, children, Json)
        }
    }
}