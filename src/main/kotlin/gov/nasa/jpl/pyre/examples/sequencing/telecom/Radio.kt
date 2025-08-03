package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class Radio(
    context: SparkInitContext,
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