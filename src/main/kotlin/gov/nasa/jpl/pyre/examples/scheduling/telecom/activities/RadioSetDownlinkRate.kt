package gov.nasa.jpl.pyre.examples.scheduling.telecom.activities

import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.set
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("RadioSetDownlinkRate")
class RadioSetDownlinkRate(
    val downlinkRate_bps: Double,
) : Activity<TelecomModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: TelecomModel) {
        model.commandedDataRate.set(downlinkRate_bps * BITS_PER_SECOND)
        scope.delay(1 * SECOND)
    }
}