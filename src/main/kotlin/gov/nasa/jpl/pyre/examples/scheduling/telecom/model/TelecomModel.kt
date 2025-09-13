package gov.nasa.jpl.pyre.examples.scheduling.telecom.model

import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.MutableQuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.registeredQuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.times
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope

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
            commandedDataRate = registeredQuantityResource("commanded_data_rate", 0.0 * BITS_PER_SECOND)
            radioPoweredOn = registeredDiscreteResource("radio_powered_on", false)
            transmittingToEarth = (inputs.isEarthPointed and radioPoweredOn)
                .named { "transmitting_to_earth" }.also { register(it) }
            realizedDataRate = ((map(transmittingToEarth) { if (it) 1.0 else 0.0 } * Unit.SCALAR) * commandedDataRate)
                .named { "realized_data_rate" }.also { register(it, BITS_PER_SECOND) }
        }
    }
}