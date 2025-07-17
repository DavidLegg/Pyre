package gov.nasa.jpl.pyre.examples.lander.config

import kotlinx.serialization.Serializable

@Serializable
data class OrbiterParams(
    val ODY: OrbiterConfiguration = OrbiterConfiguration(),
    val MRO: OrbiterConfiguration = OrbiterConfiguration(),
    val TGO: OrbiterConfiguration = OrbiterConfiguration(),
    val MVN: OrbiterConfiguration = OrbiterConfiguration(),
    val MEX: OrbiterConfiguration = OrbiterConfiguration(),
)