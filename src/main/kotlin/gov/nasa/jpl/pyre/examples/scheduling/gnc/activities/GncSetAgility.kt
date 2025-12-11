package gov.nasa.jpl.pyre.examples.scheduling.gnc.activities

import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.MRAD_PER_SECOND
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.units.discrete_unit_aware_resource.UnitAwareDiscreteResourceOperations.set
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware
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
        unitAware {
            model.agility.set(agility * MRAD_PER_SECOND)
        }
    }
}