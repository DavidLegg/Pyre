package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.int
import gov.nasa.jpl.pyre.spark.ChannelizedReports
import gov.nasa.jpl.pyre.spark.channel
import gov.nasa.jpl.pyre.spark.plans.PlanSimulation.PlanSimulationSetup
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.StringResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.StringResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.register
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.spark.activityEnd
import gov.nasa.jpl.pyre.spark.activityStart
import gov.nasa.jpl.pyre.spark.end
import gov.nasa.jpl.pyre.spark.log
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.PowerState.*
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.TestModel.DeviceActivate
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.TestModel.DeviceBoot
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.TestModel.DeviceShutdown
import gov.nasa.jpl.pyre.spark.resources.discrete.*
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.plus
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.await
import gov.nasa.jpl.pyre.spark.tasks.report
import gov.nasa.jpl.pyre.spark.tasks.spawn
import gov.nasa.jpl.pyre.spark.tasks.whenever
import gov.nasa.jpl.pyre.spark.value
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class PlanSimulationTest {
    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val simulation = PlanSimulation(
                PlanSimulationSetup(
                    reportHandler = { },
                    inconProvider = null,
                    constructModel = { },
                    constructActivity = { fail() },
                )
            )
            simulation.runUntil(HOUR)
        }
    }

    class ModelWithResources {
        val x: MutableDiscreteResource<Int>
        val y: MutableDiscreteResource<String>

        constructor(context: SparkInitContext) {
            with(context) {
                x = discreteResource("x", 0)
                y = discreteResource("y", "XYZ")

                register("x", x)
                register("y", y)
            }
        }
    }

    @Test
    fun model_with_resources_can_be_created() {
        val reports = ChannelizedReports()
        val simulation = PlanSimulation(
            PlanSimulationSetup(
                reportHandler = reports::add,
                inconProvider = null,
                constructModel = ::ModelWithResources,
                constructActivity = { fail() },
            )
        )
        simulation.runUntil(HOUR)

        with (reports) {
            channel("x") {
                at(ZERO)
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                at(ZERO)
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun activities_can_be_created() {
        val reports = ChannelizedReports()
        val simulation = PlanSimulation(
            PlanSimulationSetup(
                reportHandler = reports::add,
                inconProvider = null,
                constructModel = ::ModelWithResources,
                constructActivity = {
                    object : Activity<ModelWithResources, Unit> {
                        override val name: String = it.name
                        override val typeName: String = it.typeName

                        context(SparkTaskScope<Unit>)
                        override suspend fun effectModel(model: ModelWithResources) {
                            report("activities", JsonString("Running ${it.name}"))
                        }
                    }
                },
            )
        )
        simulation.runPlan(Plan(
            "Test Plan",
            ZERO,
            HOUR,
            listOf(
                ActivityDirective(
                    5 * MINUTE,
                    ActivitySpec(
                        "Activity 1",
                        "Type A",
                        JsonMap(emptyMap())
                    )
                ),
                ActivityDirective(
                    15 * MINUTE,
                    ActivitySpec(
                        "Activity 2",
                        "Type B",
                        JsonMap(emptyMap())
                    )
                ),
                ActivityDirective(
                    45 * MINUTE,
                    ActivitySpec(
                        "Activity 3",
                        "Type C",
                        JsonMap(emptyMap())
                    )
                ),
            )
        ))
        simulation.runUntil(HOUR)

        with (reports) {
            channel("x") {
                at(ZERO)
                element { assertEquals(0, int()) }
                assert(atEnd())
            }
            channel("y") {
                at(ZERO)
                element { assertEquals("XYZ", string()) }
                assert(atEnd())
            }
            channel("activities") {
                at(5 * MINUTE)
                activityStart("Activity 1", "Type A")
                log("Running Activity 1")
                activityEnd("Activity 1", "Type A")
                at(15 * MINUTE)
                activityStart("Activity 2", "Type B")
                log("Running Activity 2")
                activityEnd("Activity 2", "Type B")
                at(45 * MINUTE)
                activityStart("Activity 3", "Type C")
                log("Running Activity 3")
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
                deviceState = discreteResource("device_state", OFF)
                powerTable = mapOf(
                    OFF to 0.0,
                    WARMUP to 5.0,
                    STANDBY to 1.0,
                    ON to 10.0,
                    SHUTDOWN to 1.0,
                )
                miscPower = discreteResource("misc_power", 0.0)
                totalPower = DiscreteResourceMonad.map(deviceState) { s -> requireNotNull(powerTable[s]) } + miscPower

                register("deviceState", deviceState)
                register("miscPower", miscPower)
                register("totalPower", totalPower)

                spawn("Overheat Protection", whenever(
                    (totalPower greaterThan 15.0) and (deviceState notEquals OFF)) {
                    report("warning", JsonString("Overheat Protection triggered!"))
                    spawn(DeviceShutdown(), this@TestModel)
                    await(deviceState equals OFF)
                })
            }
        }

        class DeviceBoot(override val name: String = "DeviceBoot") : Activity<TestModel, Unit> {
            override val typeName = "DeviceBoot"

            constructor(spec: ActivitySpec) : this(spec.name)

            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
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

        class DeviceActivate(override val name: String = "DeviceActivate", val duration: Duration) : Activity<TestModel, Unit> {
            override val typeName = "DeviceActivate"

            constructor(spec: ActivitySpec) : this(
                spec.name,
                Duration.serializer().deserialize(requireNotNull(spec.arguments.values["duration"])))

            context(SparkTaskScope<Unit>)
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

        class DeviceShutdown(override val name: String = "DeviceShutdown") : Activity<TestModel, Unit> {
            override val typeName = "DeviceShutdown"

            constructor(spec: ActivitySpec) : this(spec.name)

            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.deviceState.set(SHUTDOWN)
                delay(5 * MINUTE)
                model.deviceState.set(OFF)
            }
        }

        class AddMiscPower(override val name: String = "AddMiscPower", val amount: Double) : Activity<TestModel, Unit> {
            override val typeName = "AddMiscPower"

            constructor(spec: ActivitySpec) : this(
                spec.name,
                (spec.arguments.values["amount"] as JsonDouble).value
            )

            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.miscPower.increase(amount)
            }
        }

        companion object {
            val activityFactory = ActivityFactory<TestModel>()
                .addConstructor("DeviceBoot", ::DeviceBoot)
                .addConstructor("DeviceActivate", ::DeviceActivate)
                .addConstructor("DeviceShutdown", ::DeviceShutdown)
                .addConstructor("AddMiscPower", ::AddMiscPower)
        }
    }

    @Test
    fun activities_can_interact_with_model() {
        val reports = ChannelizedReports()
        val simulation = PlanSimulation(
            PlanSimulationSetup(
                reportHandler = reports::add,
                inconProvider = null,
                constructModel = ::TestModel,
                constructActivity = TestModel.activityFactory::constructActivity,
            )
        )
        simulation.runPlan(Plan(
            "Test Plan",
            ZERO,
            2 * HOUR + 5 * MINUTE,
            listOf(
                ActivityDirective(
                    5 * MINUTE,
                    ActivitySpec("DeviceBoot")),
                ActivityDirective(
                    15 * MINUTE,
                    ActivitySpec(
                        "Observation 1",
                        "DeviceActivate",
                        JsonMap(mapOf(
                            "duration" to JsonString("00:10:00.000000")
                        )))),
                ActivityDirective(
                    26 * MINUTE,
                    ActivitySpec("DeviceShutdown")
                ),
                ActivityDirective(
                    60 * MINUTE,
                    ActivitySpec(
                        "Observation 2",
                        "DeviceActivate",
                        JsonMap(mapOf(
                            "duration" to JsonString("00:20:00.000000")
                        )))),
                ActivityDirective(
                    90 * MINUTE,
                    ActivitySpec("DeviceShutdown")
                ),
                ActivityDirective(
                    100 * MINUTE,
                    ActivitySpec(
                        "Observation 3",
                        "DeviceActivate",
                        JsonMap(mapOf(
                            "duration" to JsonString("01:00:00.000000")
                        )))),
                ActivityDirective(
                    110 * MINUTE,
                    ActivitySpec(
                        "AddMiscPower",
                        arguments=JsonMap(mapOf(
                            "amount" to JsonDouble(3.0)
                        )))),
                ActivityDirective(
                    115 * MINUTE,
                    ActivitySpec(
                        "AddMiscPower",
                        arguments=JsonMap(mapOf(
                            "amount" to JsonDouble(3.0)
                        )))),
            ),
        ))

        with (reports) {
            channel("activities") {
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
                at(115 * MINUTE)
                log("Overheat Protection triggered!")
                end()
            }
            channel("deviceState") {
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
                at(ZERO)
                value(0.0)
                at(110 * MINUTE)
                value(3.0)
                at(115 * MINUTE)
                value(6.0)
                end()
            }
            channel("totalPower") {
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
}
