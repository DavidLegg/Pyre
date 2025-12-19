package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * The umbrella type for all reports on a [Channel]
 */
@Serializable
sealed interface ChannelReport<T> {
    /**
     * A "data" report on this channel, reporting a result produced by running the simulation.
     */
    @Serializable
    data class ChannelData<T>(
        val channel: Name,
        @Contextual
        val time: Instant,
        val data: T,
    ) : ChannelReport<T>

    /**
     * A "metadata" report on this channel, describing the channel itself or how to interpret its data.
     */
    @Serializable
    data class ChannelMetadata<T>(
        val channel: Name,
        val metadata: Map<String, Metadatum>,
        @Transient
        val dataType: KType = typeOf<Any?>(),
        @Transient
        val reportType: KType = typeOf<ChannelData<*>>(),
        @Transient
        val metadataType: KType = typeOf<ChannelMetadata<*>>(),
    ) : ChannelReport<T>

    /**
     * Generic metadata value, with both an in-memory [value] and on-disk [text] representation.
     */
    @Serializable(with = Metadatum.MetadatumSerializer::class)
    data class Metadatum(
        val value: Any?,
        val text: String = value.toString(),
    ) {
        class MetadatumSerializer : KSerializer<Metadatum> by String.serializer().alias(
            InvertibleFunction.of({ Metadatum(null, it) }, { it.text }))
    }
}
