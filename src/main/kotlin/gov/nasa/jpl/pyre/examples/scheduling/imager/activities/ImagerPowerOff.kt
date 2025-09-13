package gov.nasa.jpl.pyre.examples.scheduling.imager.activities

import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ImagerPowerOff")
class ImagerPowerOff : Activity<ImagerModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: ImagerModel) {
        while (true) {
            when (model.mode.getValue()) {
                ImagerMode.OFF -> break
                // If the imager is warming up or imaging, another activity is happening.
                // Let that activity wrap up before we shut down.
                // A more sophisticated model might have an "interrupt" capability in its activities.
                ImagerMode.WARMUP -> await(model.mode notEquals ImagerMode.WARMUP)
                ImagerMode.IMAGING -> await(model.mode notEquals ImagerMode.IMAGING)
                ImagerMode.STANDBY -> {
                    model.mode.set(ImagerMode.WARMUP)
                    scope.delay(5 * MINUTE)
                    model.mode.set(ImagerMode.OFF)
                }
            }
        }
    }
}