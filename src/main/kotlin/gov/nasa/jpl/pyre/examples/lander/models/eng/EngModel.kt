package gov.nasa.jpl.pyre.examples.lander.models.eng

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext

class EngModel(
    context: InitScope,
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