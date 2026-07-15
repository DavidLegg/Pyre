package gov.nasa.jpl.parakeet.examples.sequencing.commands.telecom

import gov.nasa.jpl.parakeet.examples.sequencing.SequencingDemo
import gov.nasa.jpl.parakeet.examples.sequencing.primeness.Side
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SET_PRIME_RADIO")
class SetPrimeRadio(
    val side: Side,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.radios.primeSide.set(side)
    }
}