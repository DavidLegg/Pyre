package gov.nasa.jpl.pyre.examples.scheduling.imager.activities

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
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
        scope.delay(duration)
        model.mode.set(ImagerMode.STANDBY)
    }
}