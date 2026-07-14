package gov.nasa.jpl.parakeet.examples.sequencing.commands.seq

import gov.nasa.jpl.parakeet.examples.sequencing.SequencingDemo
import gov.nasa.jpl.parakeet.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
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