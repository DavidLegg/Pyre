package gov.nasa.jpl.pyre.examples.scheduling.gnc.activities

import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.MRAD_PER_SECOND
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.set
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("GncSetAgility")
class GncSetAgility(
    /**
     * Units: mrad / s
     */
    val agility: Double,
) : Activity<GncModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: GncModel) {
        model.agility.set(agility * MRAD_PER_SECOND)
    }
}