package gov.nasa.jpl.pyre.examples.sequencing.commands.seq

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel.GlobalUIntVarName
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SEQ_SET_GLOBAL_UINT")
class SeqSetGlobalUInt(
    val variable: GlobalUIntVarName,
    val value: UInt,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.fsw.globals[SideIndicator.PRIME].uints[variable.ordinal].set(value)
    }
}