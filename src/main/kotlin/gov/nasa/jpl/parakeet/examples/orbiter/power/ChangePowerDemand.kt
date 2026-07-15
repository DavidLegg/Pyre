package gov.nasa.jpl.parakeet.examples.orbiter.power

import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable

@Serializable
class ChangePowerDemand(
    val increase: Double,
): Activity<StandaloneBatteryModel> {

    context (scope: TaskScope)
    override suspend fun effectModel(model: StandaloneBatteryModel) {
        model.powerDemand.increase(increase)
    }
}