package gov.nasa.jpl.pyre.examples.orbiter.power

import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.constant
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.derivative
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.div
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope

class BatteryModel(
    context: InitScope,
    val simConfig: BatterySimConfig,
    val powerDemand: DoubleResource,
    val powerProduction: DoubleResource,
) {
    val busVoltage: Double
    val batteryCapacityAH: Double
    val batteryCapacityWH: Double

    val batteryCurrent: PolynomialResource
    val batterySOC: PolynomialResource

    val batteryCurrentUnclamped: DoubleResource
    val batteryFull: BooleanResource
    val batteryEmpty: BooleanResource
    val batteryChargeSec: PolynomialResource
    val batteryCharge: PolynomialResource

    init {
        with (context) {
            busVoltage = simConfig.busVoltage
            batteryCapacityAH = simConfig.batteryCapacity
            batteryCapacityWH = batteryCapacityAH * busVoltage

            batteryCurrentUnclamped = map(powerProduction, powerDemand, ::computeBatteryCurrent)
                .named { "batteryCurrentUnclamped" }
                .registered()

            // Integrated states compute in units of seconds, but battery charge is in units of Amp-hours, hence the
            // factor of 3600
            var clampedIntegrate = batteryCurrentUnclamped.asPolynomial().clampedIntegral(
                "batteryChargeSec",
                constant(0.0),
                constant(batteryCapacityAH * 3600.0),
                batteryCapacityAH * simConfig.initialSOC * 3600.0 / 100.0)

            batteryChargeSec = clampedIntegrate.integral
                .named { "batteryChargeSec" }
                .registered()
            batteryCurrent = batteryChargeSec.derivative()
                .named { "batteryCurrent" }
                .registered()

            // Conversion from units of seconds back to hours
            batteryCharge = (batteryChargeSec / 3600.0)
                .named { "batteryCharge" }
                .registered()
            batterySOC = (batteryCharge / (batteryCapacityAH * 100.0))
                .named { "batterySOC" }
                .registered()
            batteryFull = (batterySOC greaterThanOrEquals 100.0)
                .named { "batteryFull" }
                .registered()
            batteryEmpty = (batterySOC lessThanOrEquals 0.0)
                .named { "batteryEmpty" }
                .registered()
        }
    }

    /**
     * Calculates the battery current into or out of the battery based on the difference between the current power
     * production and demand. Battery current is computed by simply dividing the net power between source and demand by
     * bus voltage (I = P/V).
     *
     * @return batteryCurrent
     */
    fun computeBatteryCurrent(powerProd: Double, powerDemand: Double): Double {
        return (powerProd - powerDemand) / this.busVoltage
    }
}