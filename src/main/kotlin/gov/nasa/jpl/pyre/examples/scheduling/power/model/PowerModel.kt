package gov.nasa.jpl.pyre.examples.scheduling.power.model

import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.GncControlMode
import gov.nasa.jpl.pyre.examples.units.MILLIWATT
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.plus
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.VsQuantity.div
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.constant
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.derivative
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.minus
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.valueIn
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.StandardUnits.HOUR
import gov.nasa.jpl.pyre.flame.units.StandardUnits.JOULE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext

val WATT_HOUR = Unit.derived("Wh", WATT * HOUR)

/**
 * Highly simplified power model, assuming constant power production.
 */
class PowerModel(
    context: InitScope,
    config: Config,
    inputs: Inputs,
) {
    data class Config(
        /**
         * Units: Power
         */
        val rtgPowerProduction: Quantity,

        /**
         * Units: Energy
         */
        val batteryCapacity: Quantity,
    )

    data class Inputs(
        val gncControlMode: DiscreteResource<GncControlMode>,
        val dataSystemMode: DiscreteResource<OnOff>,
        val imagerMode: DiscreteResource<OnOff>,
        val heater1Mode: DiscreteResource<OnOff>,
        val heater2Mode: DiscreteResource<OnOff>,
    )

    // For simplicity, I'm hard-coding the power tables.
    // One could easily imagine reading these in from file.

    /**
     * Units: Power
     */
    val gncPowerTable: Map<GncControlMode, Quantity> = mapOf(
        GncControlMode.IDLE to (0.0 * MILLIWATT),
        GncControlMode.HOLD to (300.0 * MILLIWATT),
        GncControlMode.TURN to (45.0 * WATT),
    )
    val dataPowerTable: Map<OnOff, Quantity> = mapOf(
        OnOff.OFF to (0.0 * WATT),
        OnOff.ON to (10.0 * WATT),
    )
    val imagerPowerTable: Map<OnOff, Quantity> = mapOf(
        OnOff.OFF to (0.0 * WATT),
        OnOff.ON to (30.0 * WATT),
    )
    val heaterPowerTable: Map<OnOff, Quantity> = mapOf(
        OnOff.OFF to (0.0 * WATT),
        OnOff.ON to (120.0 * WATT),
    )

    val gncSubsystem: Device<GncControlMode>
    val dataSubsystem: Device<OnOff>
    val imager: Device<OnOff>
    val heater1: Device<OnOff>
    val heater2: Device<OnOff>

    enum class OnOff { ON, OFF }

    val totalPowerDraw: QuantityResource
    val netPowerProduction: PolynomialQuantityResource
    val batteryEnergy: PolynomialQuantityResource
    val batterySOC: PolynomialResource
    val energyOverdrawn: PolynomialQuantityResource
    val powerOverdrawn: PolynomialQuantityResource

    init {
        with (context) {
            // Devices
            gncSubsystem = Device(subContext("gnc_subsystem"), inputs.gncControlMode, gncPowerTable)
            dataSubsystem = Device(subContext("data_subsystem"), inputs.dataSystemMode, dataPowerTable)
            imager = Device(subContext("imager"), inputs.imagerMode, imagerPowerTable)
            heater1 = Device(subContext("heater_1"), inputs.heater1Mode, heaterPowerTable)
            heater2 = Device(subContext("heater_2"), inputs.heater1Mode, heaterPowerTable)

            // Totals and battery level
            totalPowerDraw = ((gncSubsystem.powerDraw
                    + dataSubsystem.powerDraw
                    + imager.powerDraw
                    + heater1.powerDraw
                    + heater2.powerDraw
                    ) named { "total_power_draw" }).also { register(it, WATT) }
            netPowerProduction = ((constant(config.rtgPowerProduction) - totalPowerDraw.asPolynomial()) named { "net_power_production" })
                .also { register(it, WATT) }
            val batteryIntegral = netPowerProduction.clampedIntegral(
                "battery_energy",
                constant(0.0 * JOULE),
                constant(config.batteryCapacity),
                config.batteryCapacity
            )
            batteryEnergy = batteryIntegral.integral.also { register(it, WATT_HOUR) }
            batterySOC = ((batteryEnergy / config.batteryCapacity).valueIn(Unit.SCALAR) named { "battery_soc" })
                .also { register(it) }
            energyOverdrawn = (batteryIntegral.underflow named { "energy_overdrawn" })
                .also { register(it, WATT_HOUR) }
            powerOverdrawn = (energyOverdrawn.derivative() named { "power_overdrawn" })
                .also { register(it, WATT) }
        }
    }
}