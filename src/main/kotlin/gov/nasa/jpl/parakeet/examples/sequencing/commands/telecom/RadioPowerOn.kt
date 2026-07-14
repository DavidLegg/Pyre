package gov.nasa.jpl.parakeet.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
@SerialName("RADIO_POWER_ON")
class RadioPowerOn(
    val side: SideIndicator,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        delay(10.seconds)
        model.telecom.radios[side].poweredOn.set(true)
    }
}