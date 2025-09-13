package gov.nasa.jpl.pyre.examples.scheduling.support_files

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CommPass(
    @Contextual
    val start: Instant,
    @Contextual
    val end: Instant,
    val critical: Boolean,
    @SerialName("downlink_rate")
    val downlinkRate: Double,
)
