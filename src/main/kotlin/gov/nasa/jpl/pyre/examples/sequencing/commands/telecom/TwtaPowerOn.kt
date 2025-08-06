package gov.nasa.jpl.pyre.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(TwtaPowerOn.COMMAND_STEM)
class TwtaPowerOn(
    val side: SideIndicator,
) : Activity<SequencingDemo> {

    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.twtas[side].poweredOn.set(true)
    }

    companion object {
        const val COMMAND_STEM = "TWTA_POWER_ON"
    }
}