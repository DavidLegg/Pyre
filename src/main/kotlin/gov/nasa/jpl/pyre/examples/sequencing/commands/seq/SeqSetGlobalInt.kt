package gov.nasa.jpl.pyre.examples.sequencing.commands.seq

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
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