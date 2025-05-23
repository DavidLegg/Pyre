package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.JsonValue.JsonArray
import gov.nasa.jpl.pyre.ember.JsonValue.JsonMap
import kotlin.Result.Companion.success

class JsonConditions private constructor(private val conditions: ConditionsTreeNode) : Conditions {
    private class ConditionsTreeNode {
        var value: JsonValue? = null
        var accruedValue: MutableList<JsonValue>? = null
        val children: MutableMap<String, ConditionsTreeNode> = mutableMapOf()
    }

    constructor() : this(ConditionsTreeNode())

    override fun report(keys: Sequence<String>, value: JsonValue) {
        val targetConditions = getNode(keys)
        require(targetConditions.value == null) {
            "Final condition ${keys.joinToString(separator=".")} was already reported, cannot report a new value."
        }
        require(targetConditions.accruedValue == null) {
            "Final condition ${keys.joinToString(separator=".")} is already being accrued, cannot report a value."
        }
        targetConditions.value = value
    }

    override fun accrue(keys: Sequence<String>, value: JsonValue) {
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

    override fun get(keys: Sequence<String>): JsonValue? {
        var targetConditions: ConditionsTreeNode? = conditions
        for (key in keys) {
            targetConditions = targetConditions?.children?.get(key)
        }
        return targetConditions?.value ?: targetConditions?.accruedValue?.let { JsonArray(it) }
    }

    companion object {
        const val VALUE_KEY: String = "value"

        fun serializer(): Serializer<JsonConditions> = object: Serializer<JsonConditions> {
            override fun serialize(obj: JsonConditions) = serialize(obj.conditions)

            private fun serialize(node: ConditionsTreeNode): JsonValue {
                var result = node.children.mapValues { serialize(it.value) }
                node.value?.let { result += VALUE_KEY to it }
                node.accruedValue?.let { result += VALUE_KEY to JsonArray(it.toList()) }
                return JsonMap(result)
            }

            override fun deserialize(jsonValue: JsonValue) = JsonConditions(deserializeNode(jsonValue))

            private fun deserializeNode(jsonValue: JsonValue): ConditionsTreeNode {
                val result = ConditionsTreeNode()
                for ((key, value) in (jsonValue as JsonMap).values) {
                    if (key == VALUE_KEY) {
                        result.value = value
                    } else {
                        result.children[key] = deserializeNode(value)
                    }
                }
                return result
            }
        }
    }
}