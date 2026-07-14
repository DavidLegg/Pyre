package gov.nasa.jpl.parakeet.examples.sequencing.activities

import gov.nasa.jpl.parakeet.examples.sequencing.SequencingDemo
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.tasks.Reactions.await
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
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