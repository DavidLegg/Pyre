package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Serialization {
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