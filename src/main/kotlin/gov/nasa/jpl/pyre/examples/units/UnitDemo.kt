package gov.nasa.jpl.pyre.examples.units

import gov.nasa.jpl.pyre.examples.units.DeviceState.*
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.plus
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.flame.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext

val MILLIWATT = Unit.derived("mW", 1e-3 * WATT)
val KILOWATT = Unit.derived("kW", 1e3 * WATT)

class UnitDemo(
    context: InitScope,
) {
    val heaterA: Device
    val heaterB: Device
    val camera: Device
    val totalPowerDraw: QuantityResource
    // TODO: Implement unit-awareness on polynomial resources, then integrate and report energy used in watt-hours.

    init {
        with (context) {
            // Note that we're free to mix units here, so long as we don't mix dimensions:
            heaterA = Device(subContext("heater_a"),
                OFF to 0.0 * WATT,
                STANDBY to 10.0 * MILLIWATT,
                ON to 1.2 * WATT,
            )
            heaterB = Device(subContext("heater_b"),
                OFF to 0.0 * WATT,
                STANDBY to 10.0 * MILLIWATT,
                ON to 1.2 * WATT,
            )
            camera = Device(subContext("camera"),
                OFF to 0.0 * WATT,
                STANDBY to 120.0 * MILLIWATT,
                ON to 3.4 * WATT
            )

            // When we define this sum, the framework will do dimension-checks to ensure this sum is sensible.
            // Then, it'll decide on any scaling factors needed to make the units agree, and build those into the derivation.
            // During simulation, it doesn't need to repeat these steps. It just applies the scaling factor and adds the double values.
            totalPowerDraw = (heaterA.powerDraw + heaterB.powerDraw + camera.powerDraw) named { "total_power_draw" }
            // Note that we *must* choose a unit to register the resource in.
            // While we could technically choose totalPowerDraw.unit, it's clearer and easier to choose a specific unit.
            // If totalPowerDraw isn't already in this unit, it'll be converted automatically for registration.
            // This also serves to double-check our derivation by indicating an expected dimension.
            // Just like with derivation, dimension-checking is done once when building the model, and a fixed scale factor
            // is baked in and used during simulation.
            register(totalPowerDraw, KILOWATT)
        }
    }
}

class Device(
    context: InitScope,
    vararg powerUsage: Pair<DeviceState, Quantity>,
) {
    val state: MutableDiscreteResource<DeviceState>
    val powerDraw: QuantityResource

    init {
        // For efficiency, pre-convert all the power usages to one unit, and apply that unit at the resource level
        val powerUsageMap = powerUsage.toMap().mapValues { (_, q) -> q.valueIn(MILLIWATT) }
        with (context) {
            state = registeredDiscreteResource("state", OFF)
            powerDraw = map(state, powerUsageMap::getValue) * MILLIWATT named { "power_draw" }
            register(powerDraw, MILLIWATT)
        }
    }
}

enum class DeviceState { OFF, STANDBY, ON }
