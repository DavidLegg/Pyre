package gov.nasa.jpl.parakeet.foundation.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

class ResultSerializer<T>(val tSerializer: KSerializer<T>) : KSerializer<Result<T>> {
    // The design of this serializer is based on the 'Either' example given in the docs for JsonDecoder.
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(Result::class.qualifiedName!!, PolymorphicKind.SEALED)

    private val failureDescriptor = buildClassSerialDescriptor(Result::class.qualifiedName!! + "(failure)") {
        element("error", serialDescriptor<String>())
    }

    override fun serialize(encoder: Encoder, value: Result<T>) {
        // Encode directly, without going to JsonElement first.
        // I'm pretty confident this will work for Json, but I think it could break Protobuf if we ever tried it.
        value.fold(
            // Success values are encoded directly, without noting anything about this result
            { tSerializer.serialize(encoder, it) },
            // Failures are encoded as a structure with a single argument
            {
                encoder.encodeStructure(failureDescriptor) {
                    encodeStringElement(failureDescriptor, 0, it.message ?: "")
                }
            }
        )
    }

    override fun deserialize(decoder: Decoder): Result<T> {
        // Demand JSON encoding only
        decoder as JsonDecoder
        val jsonElement = decoder.decodeJsonElement()
        return if (jsonElement is JsonObject && "error" in jsonElement.keys) {
            // Treat this as a 'failure' result
            require(jsonElement.keys.size == 1) {
                "Failure results may only have key 'error'."
            }
            Result.failure(RuntimeException(
                jsonElement.getValue("error").jsonPrimitive.contentOrNull))
        } else {
            // Otherwise, treat this as a 'success' result and decode using tSerializer
            // Use the Json format in decoder, in case further contextual deserializers are needed
            Result.success(decoder.json.decodeFromJsonElement(tSerializer, jsonElement))
        }
    }
}
