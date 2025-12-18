package gov.nasa.jpl.pyre.examples.scheduling.power.model

import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.GncControlMode
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel.ImagerMode
import gov.nasa.jpl.pyre.examples.scheduling.power.model.Device.Companion.Device
import gov.nasa.jpl.pyre.examples.units.MILLIWATT
import gov.nasa.jpl.pyre.general.units.quantity_resource.QuantityResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResource
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.constant
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.StandardUnits.HOUR
import gov.nasa.jpl.pyre.general.units.StandardUnits.JOULE
import gov.nasa.jpl.pyre.general.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.VsQuantity.div
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.minus
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.plus
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.upcast
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialQuantityResourceOperations.integral
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.named
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.registered
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware
import kotlinx.serialization.Serializable

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
        val radioPowerMode: DiscreteResource<OnOff>,
        val imagerMode: DiscreteResource<ImagerMode>,
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
    val radioPowerTable: Map<OnOff, Quantity> = mapOf(
        OnOff.OFF to (0.0 * WATT),
        OnOff.ON to (150.0 * WATT),
    )
    val imagerPowerTable: Map<ImagerMode, Quantity> = mapOf(
        ImagerMode.OFF to (0.0 * WATT),
        ImagerMode.WARMUP to (30.0 * WATT),
        ImagerMode.STANDBY to (5.0 * WATT),
        ImagerMode.IMAGING to (20.0 * WATT),
    )
    val heaterPowerTable: Map<OnOff, Quantity> = mapOf(
        OnOff.OFF to (0.0 * WATT),
        OnOff.ON to (120.0 * WATT),
    )

    val gncSubsystem: Device<GncControlMode>
    val dataSubsystem: Device<OnOff>
    val radio: Device<OnOff>
    val imager: Device<ImagerMode>
    val heater1: Device<OnOff>
    val heater2: Device<OnOff>

    @Serializable
    enum class OnOff { ON, OFF }

    val totalPowerDraw: QuantityResource
    val netPowerProduction: PolynomialQuantityResource
    val batteryEnergy: PolynomialQuantityResource
    val batterySOC: PolynomialResource
    val energyOverdrawn: PolynomialQuantityResource
    val powerOverdrawn: PolynomialQuantityResource

    init {
        with (context) {
            unitAware {
                // Devices
                gncSubsystem = Device(subContext("gnc_subsystem"), inputs.gncControlMode, gncPowerTable)
                dataSubsystem = Device(subContext("data_subsystem"), inputs.dataSystemMode, dataPowerTable)
                radio = Device(subContext("radio"), inputs.radioPowerMode, radioPowerTable)
                imager = Device(subContext("imager"), inputs.imagerMode, imagerPowerTable)
                heater1 = Device(subContext("heater_1"), inputs.heater1Mode, heaterPowerTable)
                heater2 = Device(subContext("heater_2"), inputs.heater1Mode, heaterPowerTable)

                // Totals and battery level
                totalPowerDraw = (
                        gncSubsystem.powerDraw
                        + dataSubsystem.powerDraw
                        + radio.powerDraw
                        + imager.powerDraw
                        + heater1.powerDraw
                        + heater2.powerDraw
                ).named { "total_power_draw" }
                    .registered(WATT)
                netPowerProduction = (constant(config.rtgPowerProduction) - totalPowerDraw.asPolynomial())
                    .named { "net_power_production" }
                    .registered(WATT)
                val batteryIntegral = netPowerProduction.clampedIntegral(
                    "battery_energy",
                    constant(0.0 * JOULE),
                    constant(config.batteryCapacity),
                    config.batteryCapacity
                )
                batteryEnergy = batteryIntegral.integral
                    .registered(WATT_HOUR)
                batterySOC = (batteryEnergy / config.batteryCapacity).valueIn(Unit.SCALAR)
                    .named { "battery_soc" }
                    .registered()
                powerOverdrawn = batteryIntegral.underflow
                    .named { "power_overdrawn" }
                    .registered(WATT)
                energyOverdrawn = powerOverdrawn.integral("energy_overdrawn", 0.0 * WATT_HOUR)
                    .registered(WATT_HOUR)
                    .upcast()
            }
        }
    }
}