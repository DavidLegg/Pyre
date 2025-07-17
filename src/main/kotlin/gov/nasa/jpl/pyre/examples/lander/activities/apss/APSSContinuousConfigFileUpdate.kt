package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import kotlinx.serialization.Serializable

@Serializable
class APSSContinuousConfigFileUpdate(
    val duration: Duration = 20 * MINUTE,
    val componentInRates: Map<APSSModel.Component, APSSModel.ComponentRate> = mapOf(),
    val componentOutRates: Map<APSSModel.Component, APSSModel.ComponentRate> = mapOf(),
    val transferCoef: Double = 0.2088, // Mbit/s
): Activity<Mission> {

    context (scope: SparkTaskScope<Unit>)
    override suspend fun effectModel(model: Mission) {
        model.apssModel.transferRate.set(transferCoef)

        // Set all defined in and out rates
        for ((component, rate) in componentInRates) {
            model.apssModel.components.getValue(component).inRate.set(rate)
        }
        for ((component, rate) in componentOutRates) {
            model.apssModel.components.getValue(component).outRate.set(rate)
        }
    }
}