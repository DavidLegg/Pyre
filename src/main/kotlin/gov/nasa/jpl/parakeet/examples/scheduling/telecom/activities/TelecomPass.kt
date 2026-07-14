package gov.nasa.jpl.parakeet.examples.scheduling.telecom.activities

import gov.nasa.jpl.parakeet.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.plans.ActivityActions.call
import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.parakeet.foundation.tasks.TaskOperations.delayUntil
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
@SerialName("TelecomPass")
class TelecomPass(
    val duration: Duration,
    val downlinkRate_bps: Double,
) : Activity<TelecomModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: TelecomModel) {
        val startTime = simulationClock.getValue()
        call(RadioPowerOn(), model)
        call(RadioSetDownlinkRate(downlinkRate_bps), model)
        delayUntil(startTime + duration - 6.seconds)
        call(RadioSetDownlinkRate(10.0), model)
        call(RadioPowerOff(), model)
    }
}