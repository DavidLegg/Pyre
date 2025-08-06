package gov.nasa.jpl.pyre.examples.lander.models.wake

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope

class WakeModel(
    context: SparkInitScope,
) {
    enum class WakeType {
        NONE,
        FULL,
        DIAGNOSTIC,
    }

    val wakeType: DiscreteResource<WakeType>

    init {
        with (context) {
            wakeType = registeredDiscreteResource("wakeType", WakeType.NONE)
        }
    }
}