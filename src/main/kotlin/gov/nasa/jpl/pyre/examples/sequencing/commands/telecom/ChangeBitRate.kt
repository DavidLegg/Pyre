package gov.nasa.jpl.pyre.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.ember.Duration.Companion.MILLISECOND
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CHANGE_BIT_RATE")
class ChangeBitRate(
    val side: SideIndicator,
    val bitRate: Double,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.telecom.radios[side].downlinkRate.set(bitRate)
        delay(500 * MILLISECOND)
    }
}