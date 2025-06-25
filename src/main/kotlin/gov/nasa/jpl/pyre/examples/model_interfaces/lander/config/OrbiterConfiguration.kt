package gov.nasa.jpl.pyre.examples.model_interfaces.lander.config

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig

data class OrbiterConfiguration(
    val blockName: String = "uhf",
    val vcsDownlinked: List<DataConfig.ChannelName> = enumValues<DataConfig.ChannelName>().toList(),
    val dvAddedMbits: Double = 0.0,
    val delayForVC00: Boolean = false,
)