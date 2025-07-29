package gov.nasa.jpl.pyre.coals

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.InputStream
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
object Serialization {
    fun <T> Json.decodeFromJsonElement(type: KType, jsonElement: JsonElement) =
        this.decodeFromJsonElement(this.serializersModule.serializer(type) as DeserializationStrategy<T>, jsonElement)

    fun <T> Json.decodeFromString(type: KType, string: String) =
        this.decodeFromString(this.serializersModule.serializer(type) as DeserializationStrategy<T>, string)

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> Json.decodeFromStream(type: KType, stream: InputStream) =
        this.decodeFromStream(this.serializersModule.serializer(type) as DeserializationStrategy<T>, stream)
}