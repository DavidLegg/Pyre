package gov.nasa.jpl.pyre.examples.sequencing.commands.seq

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator.PRIME
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SEQ_ADD")
class SeqAdd(
    val variable1: GlobalIntVarName,
    val variable2: GlobalIntVarName,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        model.fsw.globals[PRIME].ints[variable1.ordinal].increment(
            model.fsw.globals[PRIME].ints[variable2.ordinal].getValue())
    }
}