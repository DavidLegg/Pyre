package gov.nasa.jpl.parakeet.examples.sequencing.commands.telecom

import gov.nasa.jpl.parakeet.examples.sequencing.SequencingDemo
import gov.nasa.jpl.parakeet.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.parakeet.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
@SerialName("RADIO_POWER_OFF")
class RadioPowerOff(
    val side: SideIndicator,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.radios[side].poweredOn.set(false)
        delay(1.seconds)
    }
}