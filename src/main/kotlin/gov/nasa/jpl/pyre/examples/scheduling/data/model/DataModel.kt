package gov.nasa.jpl.pyre.examples.scheduling.data.model

import gov.nasa.jpl.pyre.general.units.quantity_resource.QuantityResource
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResource
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.constant
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.StandardUnits.BIT
import gov.nasa.jpl.pyre.general.units.StandardUnits.BYTE
import gov.nasa.jpl.pyre.general.units.StandardUnits.GIGABYTE
import gov.nasa.jpl.pyre.general.units.StandardUnits.MEGABYTE
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.minus
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.upcast
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.named
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.register
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware

val BITS_PER_SECOND = Unit.derived("bps", BIT / SECOND)

/**
 * Simplified data model
 */
class DataModel(
    context: InitScope,
    val config: Config,
    val inputs: Inputs,
) {
    data class Config(
        val dataCapacity: Quantity,
    )

    data class Inputs(
        /**
         * Data production rate
         *
         * Units: Information / Second
         */
        val dataRate: QuantityResource,

        /**
         * Overall data downlink rate
         *
         * Units: Information / Second
         */
        val downlinkDataRate: QuantityResource,
    )

    /**
     * Net data rate, production minus downlink
     *
     * Units: Information / Time
     */
    val netDataRate: QuantityResource

    /**
     * Total amount of data stored
     *
     * Units: Information
     */
    val storedData: PolynomialQuantityResource

    /**
     * Actual downlink data rate, taking into account availability of data to downlink.
     *
     * Units: Information / Time
     */
    val actualDownlinkRate: PolynomialQuantityResource

    /**
     * Total amount of data downlinked
     *
     * Units: Information
     */
    val dataDownlinked: PolynomialQuantityResource

    /**
     * Total amount of data lost due to storage overflow
     *
     * Units: Information
     */
    val dataLost: PolynomialQuantityResource

    init {
        with (context) {
            unitAware {
                netDataRate = (inputs.dataRate - inputs.downlinkDataRate)
                    .named { "net_data_rate" }.also { register(it, BITS_PER_SECOND) }
                val dataCapacity = constant(config.dataCapacity)
                    .named { "data_capacity" }
                    .also { register(it, GIGABYTE) }
                val storageIntegral = netDataRate.asPolynomial().clampedIntegral(
                    "stored_data",
                    constant(0.0 * BYTE),
                    dataCapacity,
                    0.0 * MEGABYTE,
                )
                storedData = storageIntegral.integral.also { register(it, GIGABYTE) }
                // Actual downlink rate is downlinkRate - underflow rate: I.e., when we're underflowing, we're failing to downlink by that rate.
                actualDownlinkRate = (inputs.downlinkDataRate.asPolynomial() - storageIntegral.underflow)
                    .named { "actual_downlink_rate" }.also { register(it, BITS_PER_SECOND) }
                dataDownlinked = actualDownlinkRate.registeredIntegral("data_downlinked", 0.0 * GIGABYTE).upcast()
                dataLost = storageIntegral.overflow.registeredIntegral("data_lost", 0.0 * GIGABYTE).upcast()
            }
        }
    }
}