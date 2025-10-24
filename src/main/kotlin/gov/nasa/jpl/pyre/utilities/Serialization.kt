package gov.nasa.jpl.pyre.utilities

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
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

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Json.encodeToFile(value: T, file: Path) =
        file.outputStream().use { this.encodeToStream(value, it) }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Json.decodeFromFile(file: Path) =
        file.inputStream().use { this.decodeFromStream<T>(it) }
}