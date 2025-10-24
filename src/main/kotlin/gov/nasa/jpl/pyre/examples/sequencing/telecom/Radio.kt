package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class Radio(
    context: InitScope,
) {
    val poweredOn: MutableBooleanResource
    val downlinkRate: MutableDoubleResource

    init {
        with (context) {
            poweredOn = registeredDiscreteResource("powered_on", false)
            downlinkRate = registeredDiscreteResource("downlink_rate", 0.0)
        }
    }
}