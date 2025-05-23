package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.InvertibleFunction

interface Serializer<T> {
    fun serialize(obj: T): JsonValue
    // TODO: Consider making this return a T instead of a result, and letting the caller decide to catch exceptions
    fun deserialize(jsonValue: JsonValue): Result<T>

    companion object {
        fun <T> of(f: InvertibleFunction<T, JsonValue>) = object : Serializer<T> {
            override fun serialize(obj: T) = f(obj)
            override fun deserialize(jsonValue: JsonValue) =
                kotlin.runCatching { f.inverse()(jsonValue) }
        }
    }
}
