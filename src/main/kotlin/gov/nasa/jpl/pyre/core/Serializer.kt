package org.example.gov.nasa.jpl.pyre.core

interface Serializer<T> {
    fun serialize(obj: T): JsonValue
    fun deserialize(jsonValue: JsonValue): Result<T>
}