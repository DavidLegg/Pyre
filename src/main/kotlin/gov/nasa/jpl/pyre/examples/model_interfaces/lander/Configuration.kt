package gov.nasa.jpl.pyre.examples.model_interfaces.lander

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.config.CommParameters
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.config.EngDataParams
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.config.MasterActivityDurations
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.config.OrbiterParams
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.config.SchedulingParams

data class Configuration(
    val engDataParams: EngDataParams = EngDataParams(),
    val schedulingParams: SchedulingParams = SchedulingParams(),
    val masterActivityDurations: MasterActivityDurations = MasterActivityDurations(),
    val commParameters: CommParameters = CommParameters(),
    val orbiterParams: OrbiterParams = OrbiterParams(),
)