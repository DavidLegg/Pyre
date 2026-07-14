package gov.nasa.jpl.parakeet.examples.lander.models.wake

import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope

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