package gov.nasa.jpl.pyre.examples.lander.models.eng

import gov.nasa.jpl.pyre.flame.composition.subContext
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class EngModel(
    context: SparkInitContext,
) {
    enum class Component {
        Lander,
        APSS,
        SEIS,
        HeatProbe,
        IDS
    }

    val safeMode: Map<Component, MutableBooleanResource>

    init {
        with (context) {
            with (subContext("safe_mode")) {
                safeMode = Component.entries.associateWith {
                    registeredDiscreteResource(it.toString(), false)
                }
            }
        }
    }
}