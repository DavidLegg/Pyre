package gov.nasa.jpl.pyre.examples.sequencing.activities

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.tasks.await
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ActivateSequence")
class ActivateSequence(
    val sequenceName: String
): Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        val engine = model.sequencing.engines
            .firstOrNull { it.loadedSequenceName.getValue() == sequenceName }
        requireNotNull(engine) {
            "No engine is loaded with a sequence named $sequenceName"
        }

        engine.activate()
        // Await the unloading of the sequence
        await(engine.loadedSequenceName notEquals sequenceName)
    }
}