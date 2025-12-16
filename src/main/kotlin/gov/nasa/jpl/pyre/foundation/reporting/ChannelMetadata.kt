package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KType

@Serializable
data class ChannelMetadata(
    val name: Name,
    val metadata: Map<String, String>,
    @Transient
    val reportType: KType? = null,
)
