package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.int
import gov.nasa.jpl.pyre.spark.ChannelizedReports
import gov.nasa.jpl.pyre.spark.channel
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.JsonConditions.Companion.decodeJsonConditionsFromJsonElement
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.spark.activityEnd
import gov.nasa.jpl.pyre.spark.activityStart
import gov.nasa.jpl.pyre.spark.end
import gov.nasa.jpl.pyre.spark.log
import gov.nasa.jpl.pyre.flame.plans.PlanSimulationTest.ModelWithResources.DummyActivity
import gov.nasa.jpl.pyre.flame.plans.PlanSimulationTest.PowerState.*
import gov.nasa.jpl.pyre.flame.plans.PlanSimulationTest.TestModel.*
import gov.nasa.jpl.pyre.flame.tasks.await
import gov.nasa.jpl.pyre.flame.tasks.delay
import gov.nasa.jpl.pyre.flame.plans.ActivityActionsByContext.spawn
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.discardReports
import gov.nasa.jpl.pyre.spark.reporting.register
import gov.nasa.jpl.pyre.spark.reporting.report
import gov.nasa.jpl.pyre.spark.resources.discrete.*
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.plus
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.await
import gov.nasa.jpl.pyre.spark.tasks.whenever
import gov.nasa.jpl.pyre.spark.value
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.*
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.time.Instant

class PlanSimulationTest {
    class EmptyModel(context: SparkInitContext)

    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val epoch = Instant.parse("2020-01-01T00:00:00Z")
            val simulation = PlanSimulation.withoutIncon(
                reportHandler = discardReports,
                simulationStart = epoch,
                simulationEpoch = epoch,
                constructModel = ::EmptyModel,
            )
            simulation.runUntil(Instant.parse("2020-01-01T01:00:00Z"))
        }
    }

    class ModelWithResources {
        val x: MutableDiscreteResource<Int>
        val y: MutableDiscreteResource<String>

        constructor(context: SparkInitContext) {
            with(context) {
                x = registeredDiscreteResource("x", 0)
                y = registeredDiscreteResource("y", "XYZ")
            }
        }

        @Serializable
        class DummyActivity() : Activity<ModelWithResources> {
            context(scope: SparkTaskScope<Unit>)
            override suspend fun effectModel(model: ModelWithResources) {}
        }
    }

    @Test
    fun model_with_resources_can_be_created() {
        val reports = ChannelizedReports()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation.withoutIncon(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::ModelWithResources,
        )
        simulation.runUntil(Instant.parse("2020-01-01T01:00:00Z"))

        with (reports) {
            channel("x") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun activities_can_be_created() {
        val reports = ChannelizedReports()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation.withoutIncon(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::ModelWithResources,
        )
        simulation.runPlan(
            Plan(
                "Test Plan",
                epoch,
                epoch + HOUR.toKotlinDuration(),
                listOf(
                    GroundedActivity(Instant.parse("2020-01-01T00:05:00Z"), DummyActivity(), "Type A", "Activity 1"),
                    GroundedActivity(Instant.parse("2020-01-01T00:15:00Z"), DummyActivity(), "Type B", "Activity 2"),
                    GroundedActivity(Instant.parse("2020-01-01T00:45:00Z"), DummyActivity(), "Type C", "Activity 3"),
                )
            )
        )
        simulation.runUntil(Instant.parse("2020-01-01T01:00:00Z"))

        with (reports) {
            channel("x") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
            channel("activities") {
                at(Instant.parse("2020-01-01T00:05:00Z"))
                activityStart("Activity 1", "Type A")
                activityEnd("Activity 1", "Type A")
                at(Instant.parse("2020-01-01T00:15:00Z"))
                activityStart("Activity 2", "Type B")
                activityEnd("Activity 2", "Type B")
                at(Instant.parse("2020-01-01T00:45:00Z"))
                activityStart("Activity 3", "Type C")
                activityEnd("Activity 3", "Type C")
                end()
            }
        }
    }

    enum class PowerState { OFF, WARMUP, STANDBY, ON, SHUTDOWN }

    class TestModel {
        val deviceState: MutableDiscreteResource<PowerState>
        val powerTable: Map<PowerState, Double>
        val miscPower: MutableDoubleResource
        val totalPower: DoubleResource

        constructor(context: SparkInitContext) {
            with(context) {
                deviceState = registeredDiscreteResource("deviceState", OFF)
                powerTable = mapOf(
                    OFF to 0.0,
                    WARMUP to 5.0,
                    STANDBY to 1.0,
                    ON to 10.0,
                    SHUTDOWN to 1.0,
                )
                miscPower = registeredDiscreteResource("miscPower", 0.0)
                totalPower = DiscreteResourceMonad.map(deviceState) { s -> requireNotNull(powerTable[s]) } + miscPower

                register("totalPower", totalPower)

                spawn("Overheat Protection", whenever(
                    (totalPower greaterThan 15.0) and (deviceState notEquals OFF)) {
                    report("warning", JsonPrimitive("Overheat Protection triggered!"))
                    spawn(DeviceShutdown(), this@TestModel)
                    await(deviceState equals OFF)
                })
            }
        }

        @Serializable
        class DeviceBoot() : Activity<TestModel> {
            context(scope: SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                with (scope) {
                    when (model.deviceState.getValue()) {
                        OFF, WARMUP -> {
                            model.deviceState.set(WARMUP)
                            delay(5 * MINUTE)
                            model.deviceState.set(STANDBY)
                        }
                        else -> model.deviceState.set(STANDBY)
                    }
                }
            }
        }

        @Serializable
        class DeviceActivate(val duration: Duration) : Activity<TestModel> {
            context(scope: SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                if (model.deviceState.getValue() != STANDBY) {
                    // TODO: Spawn activity
                    //   - Put model in SparkTaskScope?
                    //   - Add name to Activity interface
                    //   - Add spawn(Activity) to SparkTaskScope, using that name and model
                    spawn(DeviceBoot(), model)
                    await(model.deviceState equals STANDBY)
                }
                model.deviceState.set(ON)
                delay(duration)
                model.deviceState.set(STANDBY)
            }
        }

        @Serializable
        class DeviceShutdown() : Activity<TestModel> {
            context(scope: SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.deviceState.set(SHUTDOWN)
                delay(5 * MINUTE)
                model.deviceState.set(OFF)
            }
        }

        @Serializable
        class AddMiscPower(val amount: Double) : Activity<TestModel> {
            context(scope: SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.miscPower.increase(amount)
            }
        }

        companion object {
            val activitySerializersModule = activitySerializersModule {
                activity(DeviceBoot::class)
                activity(DeviceActivate::class)
                activity(DeviceShutdown::class)
                activity(AddMiscPower::class)
            }
        }
    }

    @Test
    fun activities_can_interact_with_model() {
        val reports = ChannelizedReports()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation.withoutIncon(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::TestModel,
        )
        simulation.runPlan(
            Plan(
                "Test Plan",
                epoch,
                epoch + (2 * HOUR + 5 * MINUTE).toKotlinDuration(),
                listOf(
                    GroundedActivity(Instant.parse("2020-01-01T00:05:00Z"), DeviceBoot()),
                    GroundedActivity(Instant.parse("2020-01-01T00:15:00Z"), DeviceActivate(10 * MINUTE), name = "Observation 1"),
                    GroundedActivity(Instant.parse("2020-01-01T00:26:00Z"), DeviceShutdown()),
                    GroundedActivity(Instant.parse("2020-01-01T01:00:00Z"), DeviceActivate(20 * MINUTE), name = "Observation 2"),
                    GroundedActivity(Instant.parse("2020-01-01T01:30:00Z"), DeviceShutdown()),
                    GroundedActivity(Instant.parse("2020-01-01T01:40:00Z"), DeviceActivate(60 * MINUTE), name = "Observation 3"),
                    GroundedActivity(Instant.parse("2020-01-01T01:50:00Z"), AddMiscPower(3.0)),
                    GroundedActivity(Instant.parse("2020-01-01T01:55:00Z"), AddMiscPower(3.0)),
                ),
            )
        )

        with (reports) {
            channel("activities") {
                at(Instant.parse("2020-01-01T00:05:00Z"))
                activityStart("DeviceBoot")
                at(Instant.parse("2020-01-01T00:10:00Z"))
                activityEnd("DeviceBoot")
                at(Instant.parse("2020-01-01T00:15:00Z"))
                activityStart("Observation 1", "DeviceActivate")
                at(Instant.parse("2020-01-01T00:25:00Z"))
                activityEnd("Observation 1", "DeviceActivate")
                at(Instant.parse("2020-01-01T00:26:00Z"))
                activityStart("DeviceShutdown")
                at(Instant.parse("2020-01-01T00:31:00Z"))
                activityEnd("DeviceShutdown")
                at(Instant.parse("2020-01-01T01:00:00Z"))
                activityStart("Observation 2", "DeviceActivate")
                activityStart("DeviceBoot")
                at(Instant.parse("2020-01-01T01:05:00Z"))
                activityEnd("DeviceBoot")
                at(Instant.parse("2020-01-01T01:25:00Z"))
                activityEnd("Observation 2", "DeviceActivate")
                at(Instant.parse("2020-01-01T01:30:00Z"))
                activityStart("DeviceShutdown")
                at(Instant.parse("2020-01-01T01:35:00Z"))
                activityEnd("DeviceShutdown")
                at(Instant.parse("2020-01-01T01:40:00Z"))
                activityStart("Observation 3", "DeviceActivate")
                activityStart("DeviceBoot")
                at(Instant.parse("2020-01-01T01:45:00Z"))
                activityEnd("DeviceBoot")
                at(Instant.parse("2020-01-01T01:50:00Z"))
                activityStart("AddMiscPower")
                activityEnd("AddMiscPower")
                at(Instant.parse("2020-01-01T01:55:00Z"))
                activityStart("AddMiscPower")
                activityEnd("AddMiscPower")
                activityStart("DeviceShutdown")
                at(Instant.parse("2020-01-01T02:00:00Z"))
                activityEnd("DeviceShutdown")
                end()
            }
            channel("warning") {
                at(Instant.parse("2020-01-01T01:55:00Z"))
                log("Overheat Protection triggered!")
                end()
            }
            channel("deviceState") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                value("OFF")
                at(Instant.parse("2020-01-01T00:05:00Z"))
                value("WARMUP")
                at(Instant.parse("2020-01-01T00:10:00Z"))
                value("STANDBY")
                at(Instant.parse("2020-01-01T00:15:00Z"))
                value("ON")
                at(Instant.parse("2020-01-01T00:25:00Z"))
                value("STANDBY")
                at(Instant.parse("2020-01-01T00:26:00Z"))
                value("SHUTDOWN")
                at(Instant.parse("2020-01-01T00:31:00Z"))
                value("OFF")
                at(Instant.parse("2020-01-01T01:00:00Z"))
                value("WARMUP")
                at(Instant.parse("2020-01-01T01:05:00Z"))
                value("STANDBY")
                value("ON")
                at(Instant.parse("2020-01-01T01:25:00Z"))
                value("STANDBY")
                at(Instant.parse("2020-01-01T01:30:00Z"))
                value("SHUTDOWN")
                at(Instant.parse("2020-01-01T01:35:00Z"))
                value("OFF")
                at(Instant.parse("2020-01-01T01:40:00Z"))
                value("WARMUP")
                at(Instant.parse("2020-01-01T01:45:00Z"))
                value("STANDBY")
                value("ON")
                at(Instant.parse("2020-01-01T01:55:00Z"))
                value("SHUTDOWN")
                at(Instant.parse("2020-01-01T02:00:00Z"))
                value("OFF")
                end()
            }
            channel("miscPower") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                value(0.0)
                at(Instant.parse("2020-01-01T01:50:00Z"))
                value(3.0)
                at(Instant.parse("2020-01-01T01:55:00Z"))
                value(6.0)
                end()
            }
            channel("totalPower") {
                at(Instant.parse("2020-01-01T00:00:00Z"))
                value(0.0)
                at(Instant.parse("2020-01-01T00:05:00Z"))
                value(5.0)
                at(Instant.parse("2020-01-01T00:10:00Z"))
                value(1.0)
                at(Instant.parse("2020-01-01T00:15:00Z"))
                value(10.0)
                at(Instant.parse("2020-01-01T00:25:00Z"))
                value(1.0)
                at(Instant.parse("2020-01-01T00:31:00Z"))
                value(0.0)
                at(Instant.parse("2020-01-01T01:00:00Z"))
                value(5.0)
                at(Instant.parse("2020-01-01T01:05:00Z"))
                value(1.0)
                value(10.0)
                at(Instant.parse("2020-01-01T01:25:00Z"))
                value(1.0)
                at(Instant.parse("2020-01-01T01:35:00Z"))
                value(0.0)
                at(Instant.parse("2020-01-01T01:40:00Z"))
                value(5.0)
                at(Instant.parse("2020-01-01T01:45:00Z"))
                value(1.0)
                value(10.0)
                at(Instant.parse("2020-01-01T01:50:00Z"))
                value(13.0)
                at(Instant.parse("2020-01-01T01:55:00Z"))
                value(16.0)
                value(7.0)
                at(Instant.parse("2020-01-01T02:00:00Z"))
                value(6.0)
                end()
            }
        }
    }

    @Test
    fun activities_can_be_saved_and_restored() {
        val reports1 = ChannelizedReports()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val jsonFormat = Json {
            serializersModule = SerializersModule {
                include(TestModel.activitySerializersModule)
                contextual(Instant::class, String.serializer().alias(
                    InvertibleFunction.of(Instant::parse, Instant::toString)))
            }
        }
        val simulation1 = PlanSimulation.withoutIncon(
            reportHandler = reports1.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::TestModel,
        )
        // Use addActivities and runUntil to force a state with finished, running, and unstarted activities
        simulation1.addActivities(
            listOf(
                GroundedActivity(Instant.parse("2020-01-01T00:03:00Z"), DeviceBoot()),
                GroundedActivity(Instant.parse("2020-01-01T00:10:00Z"), DeviceActivate(60 * MINUTE)),
                GroundedActivity(Instant.parse("2020-01-01T01:20:00Z"), DeviceShutdown()),
            )
        )
        simulation1.runUntil(Instant.parse("2020-01-01T00:20:00Z"))
        with (reports1) {
            channel("activities") {
                at(Instant.parse("2020-01-01T00:03:00Z"))
                activityStart("DeviceBoot")
                at(Instant.parse("2020-01-01T00:08:00Z"))
                activityEnd("DeviceBoot")
                at(Instant.parse("2020-01-01T00:10:00Z"))
                activityStart("DeviceActivate")
                end()
            }
        }

        val fincon1 = jsonFormat.encodeToJsonElement(JsonConditions(jsonFormat).also(simulation1::save))

        val reports2 = ChannelizedReports()
        val simulation2 = PlanSimulation.withIncon(
            reportHandler = reports2.handler(),
            inconProvider = jsonFormat.decodeJsonConditionsFromJsonElement(fincon1),
            constructModel = ::TestModel,
        )
        // Add an activity which will spawn a child, which will be active during the next fincon cycle
        simulation2.addActivities(listOf(
            GroundedActivity(Instant.parse("2020-01-01T01:58:00Z"), DeviceActivate(20 * MINUTE))
        ))
        simulation2.runUntil(Instant.parse("2020-01-01T02:00:00Z"))

        // TODO: The fincon for the activities task shows a lot of history, when it ought to show only an await after the restart.
        //   Look into the task history / restart logic to see why that's happening...

        with (reports2) {
            channel("activities") {
                at(Instant.parse("2020-01-01T01:10:00Z"))
                activityEnd("DeviceActivate")
                at(Instant.parse("2020-01-01T01:20:00Z"))
                activityStart("DeviceShutdown")
                at(Instant.parse("2020-01-01T01:25:00Z"))
                activityEnd("DeviceShutdown")
                at(Instant.parse("2020-01-01T01:58:00Z"))
                activityStart("DeviceActivate")
                activityStart("DeviceBoot")
                end()
            }
        }

        val fincon2 = jsonFormat.encodeToJsonElement(JsonConditions(jsonFormat).also(simulation2::save))

        val reports3 = ChannelizedReports()
        val simulation3 = PlanSimulation.withIncon(
            reportHandler = reports3.handler(),
            inconProvider = jsonFormat.decodeJsonConditionsFromJsonElement(fincon2),
            constructModel = ::TestModel,
        )
        simulation3.runUntil(Instant.parse("2020-01-01T03:00:00Z"))
        with(reports3) {
            channel("activities") {
                at(Instant.parse("2020-01-01T02:03:00Z"))
                activityEnd("DeviceBoot")
                at(Instant.parse("2020-01-01T02:23:00Z"))
                activityEnd("DeviceActivate")
                end()
            }
        }
    }
}
