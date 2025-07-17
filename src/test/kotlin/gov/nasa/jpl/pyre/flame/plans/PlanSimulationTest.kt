package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.int
import gov.nasa.jpl.pyre.spark.ChannelizedReports
import gov.nasa.jpl.pyre.spark.channel
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.ReportHandler
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Assertions.*
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.time.Instant

class PlanSimulationTest {
    class EmptyModel(context: SparkInitContext)

    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val simulation = PlanSimulation(
                reportHandler = object : ReportHandler {
                    override fun <T> handle(value: T, type: KType) {}
                },
                simulationStart = Instant.parse("2020-01-01T00:00:00Z"),
                simulationEpoch = Instant.parse("2020-01-01T00:00:00Z"),
                constructModel = ::EmptyModel,
                activitySerializersModule = activitySerializersModule<EmptyModel> {},
            )
            simulation.runUntil(HOUR)
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
        val simulation = PlanSimulation(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::ModelWithResources,
            activitySerializersModule = activitySerializersModule {
                activity(DummyActivity::class)
            },
        )
        simulation.runUntil(HOUR)

        with (reports) {
            channel("x") {
                withEpoch(epoch)
                at(ZERO)
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                withEpoch(epoch)
                at(ZERO)
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun activities_can_be_created() {
        val reports = ChannelizedReports()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::ModelWithResources,
            activitySerializersModule = activitySerializersModule {
                activity(DummyActivity::class)
            },
        )
        simulation.runPlan(
            Plan(
                "Test Plan",
                epoch,
                epoch + HOUR.toKotlinDuration(),
                listOf(
                    GroundedActivity(5 * MINUTE, DummyActivity(), "Type A", "Activity 1"),
                    GroundedActivity(15 * MINUTE, DummyActivity(), "Type B", "Activity 2"),
                    GroundedActivity(45 * MINUTE, DummyActivity(), "Type C", "Activity 3"),
                )
            )
        )
        simulation.runUntil(HOUR)

        with (reports) {
            channel("x") {
                withEpoch(epoch)
                at(ZERO)
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                withEpoch(epoch)
                at(ZERO)
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
            channel("activities") {
                withEpoch(epoch)
                at(5 * MINUTE)
                activityStart("Activity 1", "Type A")
                activityEnd("Activity 1", "Type A")
                at(15 * MINUTE)
                activityStart("Activity 2", "Type B")
                activityEnd("Activity 2", "Type B")
                at(45 * MINUTE)
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
        val simulation = PlanSimulation(
            reportHandler = reports.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::TestModel,
            activitySerializersModule = TestModel.activitySerializersModule,
        )
        simulation.runPlan(
            Plan(
                "Test Plan",
                epoch,
                epoch + (2 * HOUR + 5 * MINUTE).toKotlinDuration(),
                listOf(
                    GroundedActivity(5 * MINUTE, DeviceBoot()),
                    GroundedActivity(15 * MINUTE, DeviceActivate(10 * MINUTE), name = "Observation 1"),
                    GroundedActivity(26 * MINUTE, DeviceShutdown()),
                    GroundedActivity(60 * MINUTE, DeviceActivate(20 * MINUTE), name = "Observation 2"),
                    GroundedActivity(90 * MINUTE, DeviceShutdown()),
                    GroundedActivity(100 * MINUTE, DeviceActivate(60 * MINUTE), name = "Observation 3"),
                    GroundedActivity(110 * MINUTE, AddMiscPower(3.0)),
                    GroundedActivity(115 * MINUTE, AddMiscPower(3.0)),
                ),
            )
        )

        with (reports) {
            channel("activities") {
                withEpoch(epoch)
                at(5 * MINUTE)
                activityStart("DeviceBoot")
                at(10 * MINUTE)
                activityEnd("DeviceBoot")
                at(15 * MINUTE)
                activityStart("Observation 1", "DeviceActivate")
                at(25 * MINUTE)
                activityEnd("Observation 1", "DeviceActivate")
                at(26 * MINUTE)
                activityStart("DeviceShutdown")
                at(31 * MINUTE)
                activityEnd("DeviceShutdown")
                at(60 * MINUTE)
                activityStart("Observation 2", "DeviceActivate")
                activityStart("DeviceBoot")
                at(65 * MINUTE)
                activityEnd("DeviceBoot")
                at(85 * MINUTE)
                activityEnd("Observation 2", "DeviceActivate")
                at(90 * MINUTE)
                activityStart("DeviceShutdown")
                at(95 * MINUTE)
                activityEnd("DeviceShutdown")
                at(100 * MINUTE)
                activityStart("Observation 3", "DeviceActivate")
                activityStart("DeviceBoot")
                at(105 * MINUTE)
                activityEnd("DeviceBoot")
                at(110 * MINUTE)
                activityStart("AddMiscPower")
                activityEnd("AddMiscPower")
                at(115 * MINUTE)
                activityStart("AddMiscPower")
                activityEnd("AddMiscPower")
                activityStart("DeviceShutdown")
                at(120 * MINUTE)
                activityEnd("DeviceShutdown")
                end()
            }
            channel("warning") {
                withEpoch(epoch)
                at(115 * MINUTE)
                log("Overheat Protection triggered!")
                end()
            }
            channel("deviceState") {
                withEpoch(epoch)
                at(ZERO)
                value("OFF")
                at(5 * MINUTE)
                value("WARMUP")
                at(10 * MINUTE)
                value("STANDBY")
                at(15 * MINUTE)
                value("ON")
                at(25 * MINUTE)
                value("STANDBY")
                at(26 * MINUTE)
                value("SHUTDOWN")
                at(31 * MINUTE)
                value("OFF")
                at(60 * MINUTE)
                value("WARMUP")
                at(65 * MINUTE)
                value("STANDBY")
                value("ON")
                at(85 * MINUTE)
                value("STANDBY")
                at(90 * MINUTE)
                value("SHUTDOWN")
                at(95 * MINUTE)
                value("OFF")
                at(100 * MINUTE)
                value("WARMUP")
                at(105 * MINUTE)
                value("STANDBY")
                value("ON")
                at(115 * MINUTE)
                value("SHUTDOWN")
                at(120 * MINUTE)
                value("OFF")
                end()
            }
            channel("miscPower") {
                withEpoch(epoch)
                at(ZERO)
                value(0.0)
                at(110 * MINUTE)
                value(3.0)
                at(115 * MINUTE)
                value(6.0)
                end()
            }
            channel("totalPower") {
                withEpoch(epoch)
                at(ZERO)
                value(0.0)
                at(5 * MINUTE)
                value(5.0)
                at(10 * MINUTE)
                value(1.0)
                at(15 * MINUTE)
                value(10.0)
                at(25 * MINUTE)
                value(1.0)
                at(31 * MINUTE)
                value(0.0)
                at(60 * MINUTE)
                value(5.0)
                at(65 * MINUTE)
                value(1.0)
                value(10.0)
                at(85 * MINUTE)
                value(1.0)
                at(95 * MINUTE)
                value(0.0)
                at(100 * MINUTE)
                value(5.0)
                at(105 * MINUTE)
                value(1.0)
                value(10.0)
                at(110 * MINUTE)
                value(13.0)
                at(115 * MINUTE)
                value(16.0)
                value(7.0)
                at(120 * MINUTE)
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
            serializersModule = TestModel.activitySerializersModule
        }
        val simulation1 = PlanSimulation(
            reportHandler = reports1.handler(),
            simulationStart = epoch,
            simulationEpoch = epoch,
            constructModel = ::TestModel,
            activitySerializersModule = TestModel.activitySerializersModule,
        )
        // Use addActivities and runUntil to force a state with finished, running, and unstarted activities
        simulation1.addActivities(
            listOf(
                GroundedActivity(3 * MINUTE, DeviceBoot()),
                GroundedActivity(10 * MINUTE, DeviceActivate(60 * MINUTE)),
                GroundedActivity(80 * MINUTE, DeviceShutdown()),
            )
        )
        simulation1.runUntil(20 * MINUTE)
        with (reports1) {
            channel("activities") {
                withEpoch(epoch)
                at(3 * MINUTE)
                activityStart("DeviceBoot")
                at(8 * MINUTE)
                activityEnd("DeviceBoot")
                at(10 * MINUTE)
                activityStart("DeviceActivate")
                end()
            }
        }

        val fincon1 = Json.encodeToJsonElement(JsonConditions().also(simulation1::save))

        val reports2 = ChannelizedReports()
        val simulation2 = PlanSimulation(
            reportHandler = reports2.handler(),
            inconProvider = jsonFormat.decodeFromJsonElement<JsonConditions>(fincon1),
            constructModel = ::TestModel,
            activitySerializersModule = TestModel.activitySerializersModule,
        )
        // Add an activity which will spawn a child, which will be active during the next fincon cycle
        simulation2.addActivities(listOf(
            GroundedActivity(2 * HOUR - 2 * MINUTE, DeviceActivate(20 * MINUTE))
        ))
        simulation2.runUntil(2 * HOUR)

        with (reports2) {
            channel("activities") {
                withEpoch(epoch)
                at(70 * MINUTE)
                activityEnd("DeviceActivate")
                at(80 * MINUTE)
                activityStart("DeviceShutdown")
                at(85 * MINUTE)
                activityEnd("DeviceShutdown")
                at(2 * HOUR - 2 * MINUTE)
                activityStart("DeviceActivate")
                activityStart("DeviceBoot")
                end()
            }
        }

        val fincon2 = Json.encodeToJsonElement(JsonConditions().also(simulation2::save))

        val reports3 = ChannelizedReports()
        val simulation3 = PlanSimulation(
            reportHandler = reports3.handler(),
            inconProvider = Json.decodeFromJsonElement<JsonConditions>(fincon2),
            constructModel = ::TestModel,
            activitySerializersModule = TestModel.activitySerializersModule,
        )
        simulation3.runUntil(3 * HOUR)
        with(reports3) {
            channel("activities") {
                withEpoch(epoch)
                at(2 * HOUR + 3 * MINUTE)
                activityEnd("DeviceBoot")
                at(2 * HOUR + 23 * MINUTE)
                activityEnd("DeviceActivate")
                end()
            }
        }
    }
}
