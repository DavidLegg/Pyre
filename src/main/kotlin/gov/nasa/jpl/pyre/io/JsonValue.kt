package org.example.gov.nasa.jpl.pyre.io

sealed interface JsonValue {
    data object JsonNull : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonInt(val value: Int) : JsonValue
    data class JsonDouble(val value: Double) : JsonValue
    data class JsonArray(val values: List<JsonValue>) : JsonValue {
        companion object {
            fun empty() = JsonArray(emptyList())
        }
    }
    data class JsonMap(val values: Map<String, JsonValue>) : JsonValue {
        companion object {
            fun empty() = JsonMap(emptyMap())
        }
    }
}