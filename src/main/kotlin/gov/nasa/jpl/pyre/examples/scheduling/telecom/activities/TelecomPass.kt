package gov.nasa.jpl.pyre.examples.scheduling.telecom.activities

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.general.plans.ActivityActions.call
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delayUntil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        delayUntil(startTime + duration - 6 * SECOND)
        call(RadioSetDownlinkRate(10.0), model)
        call(RadioPowerOff(), model)
    }
}