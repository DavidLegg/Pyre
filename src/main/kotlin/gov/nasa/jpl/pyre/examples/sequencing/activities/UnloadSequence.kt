package gov.nasa.jpl.pyre.examples.sequencing.activities

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("UnloadSequence")
class UnloadSequence(
    val sequenceName: String
) : Activity<SequencingDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        val engine = model.sequencing.engines
            .firstOrNull { it.loadedSequenceName.getValue() == sequenceName }
        requireNotNull(engine) {
            "No engine is loaded with a sequence named $sequenceName"
        }

        engine.unload()
        // Ensure the engine actually unloads
        await(!engine.isLoaded)
    }
}