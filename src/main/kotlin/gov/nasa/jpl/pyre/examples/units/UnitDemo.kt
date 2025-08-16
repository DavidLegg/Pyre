package gov.nasa.jpl.pyre.examples.units

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.examples.units.DeviceIndicator.*
import gov.nasa.jpl.pyre.examples.units.DeviceState.*
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.plans.activity
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.reporting.CsvReportHandler
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
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

val MILLIWATT = Unit.derived("mW", 1e-3 * WATT)
val KILOWATT = Unit.derived("kW", 1e3 * WATT)

fun main(args: Array<String>) {
    // Sample main function that basically hard-codes a plan
    // The point here is just to exercise the demo model, not to fully hook everything up.

    val jsonFormat = Json {
        serializersModule = SerializersModule {
            contextual(Instant::class, String.serializer()
                .alias(InvertibleFunction.of(Instant::parse, Instant::toString)))

            include(activitySerializersModule {
                activity(SwitchDevice::class)
            })
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
                    SwitchDevice(HEATER_A, STANDBY)
                ),
                GroundedActivity(
                    epoch + (50 * MINUTE).toKotlinDuration(),
                    SwitchDevice(HEATER_A, STANDBY)
                ),
            ))

            simulation.runUntil(epoch + (2 * HOUR).toKotlinDuration())
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
