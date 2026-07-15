package gov.nasa.jpl.parakeet.examples.sequencing.commands.seq

import gov.nasa.jpl.parakeet.examples.sequencing.SequencingDemo
import gov.nasa.jpl.parakeet.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.parakeet.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SEQ_SET_GLOBAL_INT")
class SeqSetGlobalInt(
    val variable: GlobalIntVarName,
    val value: Int,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        val x = model.fsw.globals[SideIndicator.PRIME].ints[variable.ordinal]
        x.set(value)
    }
}