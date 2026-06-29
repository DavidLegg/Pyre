package gov.nasa.jpl.pyre.utilities

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
object Serialization {
    fun <T> Json.encodeToJsonElement(type: KType, value: T): JsonElement =
        encodeToJsonElement(serializersModule.serializer(type), value)

    fun <T> Json.decodeFromJsonElement(type: KType, jsonElement: JsonElement) =
        decodeFromJsonElement(serializersModule.serializer(type) as DeserializationStrategy<T>, jsonElement)

    fun <T> StringFormat.encodeToString(type: KType, value: T): String =
        encodeToString(serializersModule.serializer(type), value)

    fun <T> StringFormat.decodeFromString(type: KType, string: String) =
        decodeFromString(serializersModule.serializer(type) as DeserializationStrategy<T>, string)

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> Json.encodeToStream(type: KType, value: T, stream: OutputStream): Unit =
        encodeToStream(serializersModule.serializer(type), value, stream)

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> Json.decodeFromStream(type: KType, stream: InputStream) =
        decodeFromStream(serializersModule.serializer(type) as DeserializationStrategy<T>, stream)

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Json.encodeToFile(value: T, file: Path) =
        file.outputStream().use { encodeToStream(value, it) }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Json.decodeFromFile(file: Path) =
        file.inputStream().use { decodeFromStream<T>(it) }

    inline fun <T, reified S> KSerializer<T>.alias(isomporphism: InvertibleFunction<T, S>): KSerializer<S> {
        return object : KSerializer<S> {
            override val descriptor: SerialDescriptor =
                SerialDescriptor(requireNotNull(S::class.qualifiedName), this@alias.descriptor)

            override fun serialize(encoder: Encoder, value: S) =
                this@alias.serialize(encoder, isomporphism.inverse(value))

            override fun deserialize(decoder: Decoder): S =
                isomporphism(this@alias.deserialize(decoder))
        }
    }
}