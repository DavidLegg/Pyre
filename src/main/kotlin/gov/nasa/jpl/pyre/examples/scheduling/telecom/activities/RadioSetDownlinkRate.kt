package gov.nasa.jpl.pyre.examples.scheduling.telecom.activities

import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.set
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
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
        delay(1 * SECOND)
    }
}