package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.core.JsonValue.JsonMap
import kotlin.Result.Companion.success

class JsonConditions private constructor(private val conditions: ConditionsTreeNode) : Conditions {
    private class ConditionsTreeNode {
        var value: JsonValue? = null
        val children: MutableMap<String, ConditionsTreeNode> = mutableMapOf()
    }

    override fun report(keys: Sequence<String>, value: JsonValue) {
        var targetConditions = conditions
        for (key in keys) {
            require(key != VALUE_KEY) { "\"$VALUE_KEY\" is a reserved key which cannot be used for condition identifiers" }
            targetConditions = targetConditions.children.getOrPut(key) { ConditionsTreeNode() }
        }

        require(targetConditions.value == null) { "Final condition ${keys.joinToString(separator=".")} is already set!" }
        targetConditions.value = value
    }

    override fun get(keys: Sequence<String>): JsonValue? {
        var targetConditions: ConditionsTreeNode? = conditions
        for (key in keys) {
            targetConditions = targetConditions?.children?.get(key)
        }
        return targetConditions?.value
    }

    fun serialize(): JsonValue = serializer().serialize(this)

    companion object {
        const val VALUE_KEY: String = "value"

        fun serializer(): Serializer<JsonConditions> = object: Serializer<JsonConditions> {
            override fun serialize(obj: JsonConditions) = serialize(obj.conditions)

            private fun serialize(node: ConditionsTreeNode): JsonValue {
                var result = node.children.mapValues { serialize(it.value) }
                node.value?.let { result += VALUE_KEY to it }
                return JsonMap(result)
            }

            override fun deserialize(jsonValue: JsonValue) =
                deserializeNode(jsonValue).map { JsonConditions(it) }

            private fun deserializeNode(jsonValue: JsonValue): Result<ConditionsTreeNode> = kotlin.runCatching {
                val result = ConditionsTreeNode()
                for ((key, value) in (jsonValue as JsonMap).values) {
                    if (key == VALUE_KEY) {
                        result.value = value
                    } else {
                        result.children[key] = deserializeNode(value).getOrThrow()
                    }
                }
                return success(result)
            }
        }

        fun deserialize(value: JsonValue): Result<JsonConditions> = serializer().deserialize(value)
    }
}