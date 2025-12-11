package gov.nasa.jpl.pyre.examples.scheduling.telecom.model

import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.general.units.quantity_resource.MutableQuantityResource
import gov.nasa.jpl.pyre.general.units.quantity_resource.QuantityResource
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.upcast
import gov.nasa.jpl.pyre.general.units.discrete_unit_aware_resource.UnitAwareDiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.named
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.register

class TelecomModel(
    context: InitScope,
    config: Config,
    inputs: Inputs,
) {
    class Config

    class Inputs(
        val isEarthPointed: BooleanResource,
    )

    val commandedDataRate: MutableQuantityResource
    val radioPoweredOn: MutableBooleanResource
    val transmittingToEarth: BooleanResource
    val realizedDataRate: QuantityResource

    init {
        with (context) {
            unitAware {
                commandedDataRate = registeredDiscreteResource("commanded_data_rate", 0.0 * BITS_PER_SECOND)
                radioPoweredOn = DiscreteResourceOperations.registeredDiscreteResource("radio_powered_on", false)
                transmittingToEarth = (inputs.isEarthPointed and radioPoweredOn)
                    .named { "transmitting_to_earth" }.also { register(it) }
                realizedDataRate = ((map(transmittingToEarth) { if (it) 1.0 else 0.0 } * Unit.SCALAR) * commandedDataRate.upcast())
                    .named { "realized_data_rate" }.also { register(it, BITS_PER_SECOND) }
            }
        }
    }
}