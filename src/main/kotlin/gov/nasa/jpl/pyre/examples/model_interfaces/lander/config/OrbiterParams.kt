package gov.nasa.jpl.pyre.examples.model_interfaces.lander.config

data class OrbiterParams(
    val ODY: OrbiterConfiguration = OrbiterConfiguration(),
    val MRO: OrbiterConfiguration = OrbiterConfiguration(),
    val TGO: OrbiterConfiguration = OrbiterConfiguration(),
    val MVN: OrbiterConfiguration = OrbiterConfiguration(),
    val MEX: OrbiterConfiguration = OrbiterConfiguration(),
)