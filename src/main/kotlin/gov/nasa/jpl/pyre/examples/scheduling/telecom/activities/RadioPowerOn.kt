package gov.nasa.jpl.pyre.examples.scheduling.telecom.activities

import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("RadioPowerOn")
class RadioPowerOn : Activity<TelecomModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: TelecomModel) {
        model.radioPoweredOn.set(true)
        scope.delay(5 * SECOND)
    }
}