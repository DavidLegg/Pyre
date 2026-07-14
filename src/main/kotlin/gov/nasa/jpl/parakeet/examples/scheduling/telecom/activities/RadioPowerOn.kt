package gov.nasa.jpl.parakeet.examples.scheduling.telecom.activities

import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
@SerialName("RadioPowerOn")
class RadioPowerOn : Activity<TelecomModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: TelecomModel) {
        model.radioPoweredOn.set(true)
        delay(5.seconds)
    }
}