package gov.nasa.jpl.parakeet.examples.sequencing.telecom

import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope

class Radio(
    context: InitScope,
) {
    val poweredOn: MutableBooleanResource
    val downlinkRate: MutableDoubleResource

    init {
        with (context) {
            poweredOn = discreteResource("powered_on", false).registered()
            downlinkRate = discreteResource("downlink_rate", 0.0).registered()
        }
    }
}