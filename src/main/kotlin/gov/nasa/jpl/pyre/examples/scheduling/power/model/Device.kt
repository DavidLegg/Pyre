package gov.nasa.jpl.pyre.examples.scheduling.power.model

import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.flame.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.tasks.InitScope

/**
 * A device, as viewed through the lens of the power model.
 */
class Device<M>(
    context: InitScope,
    val mode: DiscreteResource<M>,
    private val powerTable: Map<M, Quantity>,
) {
    val powerDraw: QuantityResource

    init {
        with (context) {
            // Register the mode as such, even if it was declared / derived elsewhere too.
            register("mode", mode)
            // Pre-convert all power values to watts for performance
            val powerTable_W = powerTable.mapValues { it.value.valueIn(WATT) }
            // Then apply unit WATT to the resource as a whole, instead of each value.
            powerDraw = ((map(mode, powerTable_W::getValue) * WATT) named { "power_draw" })
                .also { register(it, WATT) }
        }
    }

}