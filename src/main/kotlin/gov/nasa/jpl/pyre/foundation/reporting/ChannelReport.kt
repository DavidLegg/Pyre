package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
        val metadata: Map<String, String>,
        @Transient
        val dataType: KType = typeOf<Any?>(),
        @Transient
        val reportType: KType = typeOf<ChannelData<*>>(),
        @Transient
        val metadataType: KType = typeOf<ChannelMetadata<*>>(),
    ) : ChannelReport<T>
}
