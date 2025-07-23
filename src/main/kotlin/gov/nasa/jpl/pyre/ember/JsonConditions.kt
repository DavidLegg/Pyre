package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.ember.InconProvidingContext.Companion.provide
import kotlinx.serialization.ExperimentalSerializationApi
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.reflect.KType

/**
 * JSON-based [Conditions], fulfilling both [FinconCollector] and [InconProvider] roles.
 *
 * Note that the jsonFormat is not preserved through serialization.
 * Use [JsonConditions.encodeToJsonElement] and [JsonConditions.decodeJsonConditionsFromJsonElement],
 * and their string- and stream-based counterparts, to preserve jsonFormat correctly.
 */
@Serializable(with = JsonConditions.JsonConditionsSerializer::class)
class JsonConditions private constructor(
    private var value: JsonElement?,
    private val children: MutableMap<String, JsonConditions>,
    private val jsonFormat: Json,
    private val locationDescription: List<String>,
) : Conditions {

    constructor(jsonFormat: Json = Json) : this(null, mutableMapOf(), jsonFormat, emptyList())

    fun copy(jsonFormat: Json = this.jsonFormat): JsonConditions = JsonConditions(
        value = value,
        children = children.mapValuesTo(mutableMapOf()) { it.value.copy(jsonFormat) },
        jsonFormat = jsonFormat,
        locationDescription = locationDescription,
    )

    override fun within(key: String): Conditions = children.getOrPut(key) {
        JsonConditions(null, mutableMapOf(), jsonFormat, locationDescription + key)
    }

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

                override fun inconExists(): Boolean = n in it.indices
            }.block()
        }
    }

    override fun <T> report(value: T, type: KType) {
        require(this.value == null) {
            "Duplicate final condition reported at " +
                    (locationDescription
                        .takeUnless { it.isEmpty() }
                        ?.joinToString(".")
                        ?: "<top level>")
        }
        this.value = encode(value, type)
    }

    override fun <T> provide(type: KType): T? {
        return value?.let { decode(it, type) }
    }

    override fun inconExists(): Boolean = value != null

    private fun <T> encode(value: T, type: KType): JsonElement =
        jsonFormat.encodeToJsonElement(jsonFormat.serializersModule.serializer(type), value)

    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(value: JsonElement, type: KType): T =
        jsonFormat.decodeFromJsonElement(jsonFormat.serializersModule.serializer(type), value) as T

    companion object {
        fun JsonConditions.encodeToJsonElement() =
            jsonFormat.encodeToJsonElement(this)

        @OptIn(ExperimentalSerializationApi::class)
        fun JsonConditions.encodeToStream(stream: OutputStream) =
            jsonFormat.encodeToStream(this, stream)

        fun JsonConditions.encodeToString() =
            jsonFormat.encodeToString(this)

        fun Json.decodeJsonConditionsFromJsonElement(json: JsonElement) =
            decodeFromJsonElement<JsonConditions>(json).copy(jsonFormat = this)

        @OptIn(ExperimentalSerializationApi::class)
        fun Json.decodeJsonConditionsFromStream(stream: InputStream) =
            decodeFromStream<JsonConditions>(stream).copy(jsonFormat = this)

        fun Json.decodeJsonConditionsFromString(string: String) =
            decodeFromString<JsonConditions>(string).copy(jsonFormat = this)

        // Since reading/writing to disk is so common, add methods to do that a "canonical" way here:
        /**
         * Canonical way to read [JsonConditions] from disk.
         *
         * Be sure to supply a [Json] format to handle contextually-serialized types like activities.
         */
        fun fromFile(conditionsFile: Path, jsonFormat: Json = Json): JsonConditions =
            conditionsFile.inputStream().use {
                jsonFormat.decodeJsonConditionsFromStream(it)
            }

        /**
         * Canonical way to write [JsonConditions] to disk.
         */
        fun JsonConditions.toFile(conditionsFile: Path) =
            conditionsFile.outputStream().use { encodeToStream(it) }
    }

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
                    children[key] = decoder.json.decodeFromJsonElement(this, element).prependLocation(key)
                }
            }

            return JsonConditions(value, children, Json, emptyList())
        }

        private fun JsonConditions.prependLocation(key: String): JsonConditions = JsonConditions(
            value = value,
            children = children.mapValuesTo(mutableMapOf()) { (_, c) -> c.prependLocation(key) },
            jsonFormat = jsonFormat,
            locationDescription = listOf(key) + locationDescription,
        )
    }
}