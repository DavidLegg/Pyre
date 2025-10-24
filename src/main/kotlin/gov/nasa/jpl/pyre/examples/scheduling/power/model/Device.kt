package gov.nasa.jpl.pyre.examples.scheduling.power.model

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A device, as viewed through the lens of the power model.
 */
class Device<M>(
    context: InitScope,
    val mode: DiscreteResource<M>,
    private val powerTable: Map<M, Quantity>,
    private val modeType: KType,
) {
    val powerDraw: QuantityResource

    init {
        with (context) {
            // Register the mode as such, even if it was declared / derived elsewhere too.
            register("mode", mode, Discrete::class.withArg(modeType))
            // Pre-convert all power values to watts for performance
            val powerTable_W = powerTable.mapValues { it.value.valueIn(WATT) }
            // Then apply unit WATT to the resource as a whole, instead of each value.
            powerDraw = ((map(mode, powerTable_W::getValue) * WATT) named { "power_draw" })
                .also { register(it, WATT) }
        }
    }

    companion object {
        inline fun <reified M> Device(
            context: InitScope,
            mode: DiscreteResource<M>,
            powerTable: Map<M, Quantity>,
        ) = Device(context, mode, powerTable, typeOf<M>())
    }
}
