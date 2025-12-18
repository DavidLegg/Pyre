package gov.nasa.jpl.pyre.examples.lander.models.wake

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class WakeModel(
    context: InitScope,
) {
    enum class WakeType {
        NONE,
        FULL,
        DIAGNOSTIC,
    }

    val wakeType: DiscreteResource<WakeType>

    init {
        with (context) {
            wakeType = discreteResource("wakeType", WakeType.NONE).registered()
        }
    }
}