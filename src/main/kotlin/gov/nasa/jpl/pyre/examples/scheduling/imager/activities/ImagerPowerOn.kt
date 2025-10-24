package gov.nasa.jpl.pyre.examples.scheduling.imager.activities

import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ImagerPowerOn")
class ImagerPowerOn : Activity<ImagerModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: ImagerModel) {
        if (model.mode.getValue() == ImagerMode.OFF) {
            model.mode.set(ImagerMode.WARMUP)
            scope.delay(15 * MINUTE)
            model.mode.set(ImagerMode.STANDBY)
        }
        // Otherwise, the instrument is already on / warming up, so reject this command
    }
}