package gov.nasa.jpl.pyre.examples.scheduling.gnc.activities

import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.MRAD_PER_SECOND
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.set
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

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