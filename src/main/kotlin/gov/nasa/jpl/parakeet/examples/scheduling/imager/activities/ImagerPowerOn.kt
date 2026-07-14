package gov.nasa.jpl.parakeet.examples.scheduling.imager.activities

import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

@Serializable
@SerialName("ImagerPowerOn")
class ImagerPowerOn : Activity<ImagerModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: ImagerModel) {
        if (model.mode.getValue() == ImagerMode.OFF) {
            model.mode.set(ImagerMode.WARMUP)
            delay(15.minutes)
            model.mode.set(ImagerMode.STANDBY)
        }
        // Otherwise, the instrument is already on / warming up, so reject this command
    }
}