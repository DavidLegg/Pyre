package gov.nasa.jpl.pyre.examples.sequencing.commands.seq

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SEQ_IF")
class SeqIf(
    val variable: GlobalIntVarName,
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        // No effects
    }
}