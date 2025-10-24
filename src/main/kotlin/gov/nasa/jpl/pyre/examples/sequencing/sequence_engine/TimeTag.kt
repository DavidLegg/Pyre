package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.kernel.Duration
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface TimeTag {
    @Serializable
    @SerialName("C")
    data object CommandComplete : TimeTag
    @Serializable
    @SerialName("R")
    data class Relative(val duration: Duration) : TimeTag
    @Serializable
    @SerialName("A")
    data class Absolute(@Contextual val time: Instant) : TimeTag
}