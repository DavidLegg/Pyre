package gov.nasa.jpl.pyre.examples.orbiter.power

import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import kotlinx.serialization.Serializable

@Serializable
class ChangePowerDemand(
    val increase: Double,
): Activity<StandaloneBatteryModel> {

    context (scope: SparkTaskScope<Unit>)
    override suspend fun effectModel(model: StandaloneBatteryModel) {
        model.powerDemand.increase(increase)
    }
}