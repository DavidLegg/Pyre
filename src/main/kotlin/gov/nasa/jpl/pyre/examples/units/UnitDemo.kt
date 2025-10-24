package gov.nasa.jpl.pyre.examples.units

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.examples.units.DeviceIndicator.*
import gov.nasa.jpl.pyre.examples.units.DeviceState.*
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.general.plans.GroundedActivity
import gov.nasa.jpl.pyre.general.plans.PlanSimulation
import gov.nasa.jpl.pyre.general.plans.activities
import gov.nasa.jpl.pyre.general.reporting.CsvReportHandler
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.plus
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResource
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.constant
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.VsQuantity.div
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.minus
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.register
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.valueIn
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.StandardUnits.HOUR
import gov.nasa.jpl.pyre.general.units.StandardUnits.JOULE
import gov.nasa.jpl.pyre.general.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

// Recommended practice: Derive all the named units you're going to use in this file once, up front.
// This gives names to all the units, which will make them print prettier when debugging.
// It may also improve performance a little, since you do the unit computation once up front when loading the classes.
val MILLIWATT = Unit.derived("mW", 1e-3 * WATT)
val KILOWATT = Unit.derived("kW", 1e3 * WATT)
val KILOWATT_HOUR = Unit.derived("kWh", KILOWATT * HOUR)

fun main(args: Array<String>) {
    // Sample main function that basically hard-codes a plan
    // The point here is just to exercise the demo model, not to fully hook everything up.

    val jsonFormat = Json {
        serializersModule = SerializersModule {
            contextual(Instant::class, String.serializer()
                .alias(InvertibleFunction.of(Instant::parse, Instant::toString)))

            activities {
                activity(SwitchDevice::class)
            }
        }
    }
    System.out.use { out ->
        CsvReportHandler(out, jsonFormat).use { reportHandler ->
            val epoch = Instant.parse("2000-01-01T00:00:00Z")

            val simulation = PlanSimulation.withoutIncon(
                reportHandler,
                epoch,
                epoch,
                ::UnitDemo,
            )

            simulation.addActivities(listOf(
                GroundedActivity(
                    epoch + (10 * MINUTE).toKotlinDuration(),
                    SwitchDevice(HEATER_A, ON)
                ),
                GroundedActivity(
                    epoch + (20 * MINUTE).toKotlinDuration(),
                    SwitchDevice(HEATER_B, STANDBY)
                ),
                GroundedActivity(
                    epoch + (30 * MINUTE).toKotlinDuration(),
                    SwitchDevice(CAMERA, STANDBY)
                ),
                GroundedActivity(
                    epoch + (40 * MINUTE).toKotlinDuration(),
                    SwitchDevice(CAMERA, ON)
                ),
                GroundedActivity(
                    epoch + (45 * MINUTE).toKotlinDuration(),
                    SwitchDevice(CAMERA, OFF)
                ),
                GroundedActivity(
                    epoch + (50 * MINUTE).toKotlinDuration(),
                    SwitchDevice(HEATER_A, STANDBY)
                ),
                GroundedActivity(
                    epoch + (55 * MINUTE).toKotlinDuration(),
                    SwitchDevice(HEATER_B, OFF)
                ),
            ))

            simulation.runUntil(epoch + (2 * Duration.HOUR).toKotlinDuration())
        }
    }
}

class UnitDemo(
    context: InitScope,
) {
    val heaterA: Device
    val heaterB: Device
    val camera: Device
    val totalPowerDraw: QuantityResource
    val totalEnergyUsed: PolynomialQuantityResource

    val powerProduction: PolynomialQuantityResource
    val batteryEnergy: PolynomialQuantityResource
    val batterySOC: PolynomialResource

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

            // Unit-awareness also "plays nicely" with continuous resources. This lets us do unit-aware integration:
            // Note that specifying the starting value in kWh means we'll register the results in that unit too.
            totalEnergyUsed = totalPowerDraw.asPolynomial().registeredIntegral("total_energy_used", 0.0 * KILOWATT_HOUR)

            // We can even do complex things like clamped integration, all in a unit-aware way:
            powerProduction = constant(1.0 * WATT)
            // Note that powerProduction is in W; totalPowerDraw, in kW.
            // totalPowerDraw will implicitly be converted to W to make the derivation below sensible.
            val netPower = powerProduction - totalPowerDraw.asPolynomial()
            val maxBatteryEnergy = 1000.0 * JOULE
            batteryEnergy = netPower.clampedIntegral(
                "battery_energy",
                constant(0.0 * JOULE),
                constant(maxBatteryEnergy),
                500.0 * JOULE,
                ).integral.also { register(it, JOULE) }
            // Finally, when we expect a pure scalar (a dimensionless quantity), we can say so.
            // The framework will ensure we've cancelled all dimensions correctly, and apply any remaining scaling
            // left over from doing unit conversions (e.g., "hr / s" requires multiplying by 3600).
            batterySOC = ((batteryEnergy / maxBatteryEnergy).valueIn(Unit.SCALAR) named { "battery_soc" })
                .also { register(it) }
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

@Serializable
@SerialName("SwitchDevice")
class SwitchDevice(
    val device: DeviceIndicator,
    val state: DeviceState,
) : Activity<UnitDemo> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: UnitDemo) {
        val selectedDevice = when (device) {
            HEATER_A -> model.heaterA
            HEATER_B -> model.heaterB
            CAMERA -> model.camera
        }
        selectedDevice.state.set(state)
    }
}

enum class DeviceIndicator { HEATER_A, HEATER_B, CAMERA }
enum class DeviceState { OFF, STANDBY, ON }
