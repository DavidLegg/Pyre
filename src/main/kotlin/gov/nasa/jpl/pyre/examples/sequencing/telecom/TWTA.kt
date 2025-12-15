package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class TWTA(
    context: InitScope
) {
    val poweredOn: MutableBooleanResource

    init {
        with (context) {
            poweredOn = discreteResource("powered_on", false).registered()
        }
    }
}