package gov.nasa.jpl.pyre.examples.scheduling.data.model

import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.minus
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.constant
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.derivative
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.minus
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.StandardUnits.BIT
import gov.nasa.jpl.pyre.flame.units.StandardUnits.BYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.GIGABYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.MEGABYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.tasks.InitScope

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
            dataDownlinked = actualDownlinkRate.registeredIntegral("data_downlinked", 0.0 * GIGABYTE)
            dataLost = storageIntegral.overflow.registeredIntegral("data_lost", 0.0 * GIGABYTE)
        }
    }
}