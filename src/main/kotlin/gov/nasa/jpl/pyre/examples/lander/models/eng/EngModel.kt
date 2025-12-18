package gov.nasa.jpl.pyre.examples.lander.models.eng

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

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
            subContext("safe_mode") {
                safeMode = Component.entries.associateWith {
                    discreteResource(it.toString(), false).registered()
                }
            }
        }
    }
}