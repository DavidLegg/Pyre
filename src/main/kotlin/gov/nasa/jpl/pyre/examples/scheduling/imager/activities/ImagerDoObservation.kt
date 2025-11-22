package gov.nasa.jpl.pyre.examples.scheduling.imager.activities

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.general.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ImagerDoObservation")
class ImagerDoObservation(
    val duration: Duration
) : Activity<ImagerModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: ImagerModel) {
        if (model.mode.getValue() == ImagerMode.OFF) {
            spawn(ImagerPowerOn(), model)
        }
        // Regardless of what state we're in, await a return to standby.
        // This includes awaiting another imaging activity.
        await(model.mode equals ImagerMode.STANDBY)

        model.mode.set(ImagerMode.IMAGING)
        delay(duration)
        model.mode.set(ImagerMode.STANDBY)
    }
}