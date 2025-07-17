package gov.nasa.jpl.pyre.examples.lander.config

import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig
import kotlinx.serialization.Serializable

@Serializable
data class OrbiterConfiguration(
    val blockName: String = "uhf",
    val vcsDownlinked: List<DataConfig.ChannelName> = enumValues<DataConfig.ChannelName>().toList(),
    val dvAddedMbits: Double = 0.0,
    val delayForVC00: Boolean = false,
)