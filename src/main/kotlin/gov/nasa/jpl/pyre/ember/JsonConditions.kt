package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.collections.set
import kotlin.reflect.KType


class JsonConditions private constructor(
    private val conditions: ConditionsTreeNode,
    private val jsonFormat: Json,
) : Conditions {
    @Serializable(with = NodeSerializer::class)
    private class ConditionsTreeNode(
        var value: JsonElement? = null,
        var accruedValue: MutableList<JsonElement>? = null,
        val children: MutableMap<String, ConditionsTreeNode> = mutableMapOf(),
    )

    constructor(jsonFormat: Json = Json) : this(ConditionsTreeNode(), jsonFormat)

    override fun <T> report(keys: Sequence<String>, value: T, type: KType) {
        val targetConditions = getNode(keys)
        require(targetConditions.value == null) {
            "Final condition ${keys.joinToString(separator=".")} was already reported, cannot report a new value."
        }
        require(targetConditions.accruedValue == null) {
            "Final condition ${keys.joinToString(separator=".")} is already being accrued, cannot report a value."
        }
        targetConditions.value = encode(value, type)
    }

    override fun <T> accrue(keys: Sequence<String>, value: T, type: KType) {
        val targetConditions = getNode(keys)
        require(targetConditions.value == null) {
            "Final condition ${keys.joinToString(separator=".")} was reported, cannot accrue another value."
        }
        targetConditions.accruedValue = (targetConditions.accruedValue ?: mutableListOf()).apply { add(encode(value, type)) }
    }

    private fun getNode(keys: Sequence<String>): ConditionsTreeNode {
        var targetConditions = conditions
        for (key in keys) {
            require(key != VALUE_KEY) { "\"$VALUE_KEY\" is a reserved key which cannot be used for condition keys" }
            targetConditions = targetConditions.children.getOrPut(key) { ConditionsTreeNode() }
        }
        return targetConditions
    }

    override fun <T> get(keys: Sequence<String>, type: KType): T? {
        var targetConditions: ConditionsTreeNode? = conditions
        for (key in keys) {
            targetConditions = targetConditions?.children?.get(key)
        }
        var json = targetConditions?.value ?: targetConditions?.accruedValue?.let { JsonArray(it) }
        return json?.let { decode(it, type) }
    }

    private fun <T> encode(value: T, type: KType): JsonElement =
        jsonFormat.encodeToJsonElement(jsonFormat.serializersModule.serializer(type), value)

    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(json: JsonElement, type: KType): T =
        jsonFormat.decodeFromJsonElement(jsonFormat.serializersModule.serializer(type), json) as T

    companion object {
        const val VALUE_KEY: String = "$"

        fun serializer(jsonFormat: Json): KSerializer<JsonConditions> = JsonConditionsSerializer(jsonFormat)
    }

    private class JsonConditionsSerializer(
        private val jsonFormat: Json,
    ): KSerializer<JsonConditions> by NodeSerializer().alias(
        InvertibleFunction.of(
            { node -> JsonConditions(node, jsonFormat) },
            { it.conditions }
        )
    )

    private class NodeSerializer: KSerializer<ConditionsTreeNode> {
        private val baseSerializer = kotlinx.serialization.serializer<JsonObject>()

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
            ConditionsTreeNode::class.qualifiedName!!,
            baseSerializer.descriptor)

        override fun serialize(encoder: Encoder, value: ConditionsTreeNode) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(buildJsonObject {
                value.value?.let { put("$", it) }
                value.accruedValue?.let { put("$", JsonArray(it)) }
                for ((key, child) in value.children) {
                    put(key, encoder.json.encodeToJsonElement(this@NodeSerializer, child))
                }
            })
        }

        override fun deserialize(decoder: Decoder): ConditionsTreeNode {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject

            var nodeValue: JsonElement? = null
            val children = mutableMapOf<String, ConditionsTreeNode>()

            for ((key, element) in jsonObject) {
                if (key == "$") {
                    nodeValue = element
                } else {
                    children[key] = decoder.json.decodeFromJsonElement(this, element)
                }
            }

            return ConditionsTreeNode(value=nodeValue, children=children)
        }
    }
}
