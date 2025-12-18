package gov.nasa.jpl.pyre.foundation.reporting

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import kotlin.reflect.KType

object Serialization {
    fun <T> Json.encodeToString(type: KType, value: T): String =
        encodeToString(serializersModule.serializer(type), value)

    fun <T> Json.encodeToJsonElement(type: KType, value: T): JsonElement =
        encodeToJsonElement(serializersModule.serializer(type), value)

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> Json.encodeToStream(type: KType, value: T, stream: OutputStream): Unit =
        encodeToStream(serializersModule.serializer(type), value, stream)
}