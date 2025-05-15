package org.example.gov.nasa.jpl.pyre.io

interface Serializer<T> {
    fun serialize(obj: T): JsonValue
    fun deserialize(jsonValue: JsonValue): Result<T>
}