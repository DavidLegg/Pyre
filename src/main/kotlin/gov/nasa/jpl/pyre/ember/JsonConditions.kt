package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.collections.set


@Serializable(with = JsonConditions.JsonConditionsSerializer::class)
class JsonConditions private constructor(private val conditions: ConditionsTreeNode) : Conditions {
    private class ConditionsTreeNode(
        var value: JsonElement? = null,
        var accruedValue: MutableList<JsonElement>? = null,
        val children: MutableMap<String, ConditionsTreeNode> = mutableMapOf(),
    )

    constructor() : this(ConditionsTreeNode())

    override fun report(keys: Sequence<String>, value: JsonElement) {
        val targetConditions = getNode(keys)
        require(targetConditions.value == null) {
            "Final condition ${keys.joinToString(separator=".")} was already reported, cannot report a new value."
        }
        require(targetConditions.accruedValue == null) {
            "Final condition ${keys.joinToString(separator=".")} is already being accrued, cannot report a value."
        }
        targetConditions.value = value
    }

    override fun accrue(keys: Sequence<String>, value: JsonElement) {
        val targetConditions = getNode(keys)
        require(targetConditions.value == null) {
            "Final condition ${keys.joinToString(separator=".")} was reported, cannot accrue another value."
        }
        targetConditions.accruedValue = (targetConditions.accruedValue ?: mutableListOf()).apply { add(value) }
    }

    private fun getNode(keys: Sequence<String>): ConditionsTreeNode {
        var targetConditions = conditions
        for (key in keys) {
            require(key != VALUE_KEY) { "\"$VALUE_KEY\" is a reserved key which cannot be used for condition keys" }
            targetConditions = targetConditions.children.getOrPut(key) { ConditionsTreeNode() }
        }
        return targetConditions
    }

    override fun get(keys: Sequence<String>): JsonElement? {
        var targetConditions: ConditionsTreeNode? = conditions
        for (key in keys) {
            targetConditions = targetConditions?.children?.get(key)
        }
        return targetConditions?.value ?: targetConditions?.accruedValue?.let { JsonArray(it) }
    }

    companion object {
        const val VALUE_KEY: String = "$"
    }

    private class JsonConditionsSerializer: KSerializer<JsonConditions> by NodeSerializer().alias(
        InvertibleFunction.of(
            ::JsonConditions,
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
