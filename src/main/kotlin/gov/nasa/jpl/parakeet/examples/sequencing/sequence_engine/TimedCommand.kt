package gov.nasa.jpl.parakeet.examples.sequencing.sequence_engine

import kotlinx.serialization.Serializable

@Serializable
data class TimedCommand(
    val timeTag: TimeTag,
    val command: Command,
)
