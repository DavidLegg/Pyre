package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import kotlinx.serialization.Serializable

@Serializable
data class Sequence(
    val name: String,
    val loadAndGo: Boolean,
    val commands: List<TimedCommand>,
)
