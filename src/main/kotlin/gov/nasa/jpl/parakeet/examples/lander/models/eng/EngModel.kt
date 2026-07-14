package gov.nasa.jpl.parakeet.examples.lander.models.eng

import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope.Companion.subContext

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