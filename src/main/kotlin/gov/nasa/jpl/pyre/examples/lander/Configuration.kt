package gov.nasa.jpl.pyre.examples.lander

import gov.nasa.jpl.pyre.examples.lander.config.CommParameters
import gov.nasa.jpl.pyre.examples.lander.config.EngDataParams
import gov.nasa.jpl.pyre.examples.lander.config.MasterActivityDurations
import gov.nasa.jpl.pyre.examples.lander.config.OrbiterParams
import gov.nasa.jpl.pyre.examples.lander.config.SchedulingParams

data class Configuration(
    val engDataParams: EngDataParams = EngDataParams(),
    val schedulingParams: SchedulingParams = SchedulingParams(),
    val masterActivityDurations: MasterActivityDurations = MasterActivityDurations(),
    val commParameters: CommParameters = CommParameters(),
    val orbiterParams: OrbiterParams = OrbiterParams(),
)