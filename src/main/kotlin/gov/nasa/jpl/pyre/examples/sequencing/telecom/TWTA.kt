package gov.nasa.jpl.pyre.examples.sequencing.telecom

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class TWTA(
    context: SparkInitContext
) {
    val poweredOn: MutableBooleanResource

    init {
        with (context) {
            poweredOn = registeredDiscreteResource("powered_on", false)
        }
    }
}