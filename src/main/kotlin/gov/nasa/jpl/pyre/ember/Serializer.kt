package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.InvertibleFunction

interface Serializer<T> {
    fun serialize(obj: T): JsonValue
    fun deserialize(jsonValue: JsonValue): T

    companion object {
        fun <T> of(f: InvertibleFunction<T, JsonValue>) = object : Serializer<T> {
            override fun serialize(obj: T) = f(obj)
            override fun deserialize(jsonValue: JsonValue) = f.inverse()(jsonValue)
        }
    }
}
