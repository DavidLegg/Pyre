package gov.nasa.jpl.pyre.examples.lander.models.wake

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.register
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class WakeModel(
    context: SparkInitContext,
) {
    enum class WakeType {
        NONE,
        FULL,
        DIAGNOSTIC,
    }

    val wakeType: DiscreteResource<WakeType>

    init {
        with (context) {
            wakeType = discreteResource("wakeType", WakeType.NONE)

            register("wakeType", wakeType)
        }
    }
}