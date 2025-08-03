package gov.nasa.jpl.pyre.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(RadioPowerOff.COMMAND_STEM)
class RadioPowerOff(
    val side: SideIndicator,
) : Activity<SequencingDemo> {

    context(scope: SparkTaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.radios[side].poweredOn.set(false)
    }
    
    companion object {
        const val COMMAND_STEM: String = "RADIO_POWER_OFF"
    }
}