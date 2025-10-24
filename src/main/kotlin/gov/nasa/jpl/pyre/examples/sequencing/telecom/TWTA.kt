package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class TWTA(
    context: InitScope
) {
    val poweredOn: MutableBooleanResource

    init {
        with (context) {
            poweredOn = registeredDiscreteResource("powered_on", false)
        }
    }
}