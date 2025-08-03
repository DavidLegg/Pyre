package gov.nasa.jpl.pyre.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(TwtaPowerOff.COMMAND_STEM)
class TwtaPowerOff(
    val side: SideIndicator,
) : Activity<SequencingDemo> {

    context(scope: SparkTaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.twtas[side].poweredOn.set(false)
    }

    companion object {
        const val COMMAND_STEM = "TWTA_POWER_OFF"
    }
}