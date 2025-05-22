package gov.nasa.jpl.pyre.ember


sealed interface JsonValue {
    data object JsonNull : JsonValue {
        override fun toString() = "null"
    }
    data class JsonBoolean(val value: Boolean) : JsonValue {
        override fun toString() = if (value) "true" else "false"
    }
    data class JsonString(val value: String) : JsonValue {
        private val ESCAPE_CHARS: Set<String> = setOf("\"", "\\", "/", "\u0008", "\u0009", "\u000A", "\u000B", "\u000C", "\u000D")
        override fun toString(): String {
            // TODO: Find a better way to do this, preferably using a JSON library or something...
            var result = value
            for (c in ESCAPE_CHARS) result = result.replace(c, "\\" + c)
            return "\"$result\""
        }
    }
    data class JsonInt(val value: Long) : JsonValue {
        override fun toString() = value.toString()
    }
    data class JsonDouble(val value: Double) : JsonValue {
        override fun toString() = value.toString()
    }
    data class JsonArray(val values: List<JsonValue>) : JsonValue {
        override fun toString() = values.joinToString(",", prefix="[", postfix="]")

        companion object {
            fun empty() = JsonArray(emptyList())
        }
    }
    data class JsonMap(val values: Map<String, JsonValue>) : JsonValue {
        override fun toString(): String {
            return values.map { (k, v) -> "${JsonString(k)}:${v}" }.joinToString(",", "{", "}")
        }

        companion object {
            fun empty() = JsonMap(emptyMap())
        }
    }
}