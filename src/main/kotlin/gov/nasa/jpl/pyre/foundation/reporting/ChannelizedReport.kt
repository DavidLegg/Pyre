package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ChannelizedReport<T>(
    val channel: Name,
    @Contextual
    val time: Instant,
    val data: T,
)