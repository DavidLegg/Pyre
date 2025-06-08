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
import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.spark.activityEnd
import gov.nasa.jpl.pyre.spark.activityStart
import gov.nasa.jpl.pyre.spark.end
import gov.nasa.jpl.pyre.spark.log
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.ModelWithResources.DummyActivity
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.PowerState.*
import gov.nasa.jpl.pyre.spark.plans.PlanSimulationTest.TestModel.*
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
    class EmptyModel: Model<EmptyModel> {
        constructor(context: SparkInitContext)

        override fun activitySerializer(): Serializer<GroundedActivity<EmptyModel, *>> =
            Serializer.of(InvertibleFunction.of({ fail() }, { fail() }))
    }

    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val simulation = PlanSimulation(
                PlanSimulationSetup(
                    reportHandler = { },
                    inconProvider = null,
                    constructModel = ::EmptyModel,
                )
            )
            simulation.runUntil(HOUR)
        }
    }

    class ModelWithResources : Model<ModelWithResources> {
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

        class DummyActivity() : Activity<ModelWithResources, Unit> {
            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: ModelWithResources) {}
        }

        override fun activitySerializer(): Serializer<GroundedActivity<ModelWithResources, *>> {
            return Serializer.of(InvertibleFunction.of(
                {
                    JsonMap(mapOf(
                        "time" to Duration.serializer().serialize(it.time),
                        "name" to JsonString(it.name),
                        "type" to JsonString(it.typeName),
                    ))
                },
                {
                    GroundedActivity(
                        Duration.serializer().deserialize(requireNotNull((it as JsonMap).values["time"])),
                        DummyActivity(),
                        (it.values["type"] as JsonString).value,
                        (it.values["name"] as JsonString).value,
                    )
                }
            ))
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
            )
        )
        simulation.runPlan(Plan(
            "Test Plan",
            ZERO,
            HOUR,
            listOf(
                GroundedActivity(5 * MINUTE, DummyActivity(), "Type A", "Activity 1"),
                GroundedActivity(15 * MINUTE, DummyActivity(), "Type B", "Activity 2"),
                GroundedActivity(45 * MINUTE, DummyActivity(), "Type C", "Activity 3"),
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

    class TestModel : Model<TestModel> {
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

        class DeviceBoot() : Activity<TestModel, Unit> {
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

            companion object {
                fun serializer(): Serializer<DeviceBoot> = Serializer.of(InvertibleFunction.of(
                    { JsonMap.empty() },
                    { DeviceBoot() }
                ))
            }
        }

        class DeviceActivate(val duration: Duration) : Activity<TestModel, Unit> {
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

            companion object {
                fun serializer(): Serializer<DeviceActivate> = Serializer.of(InvertibleFunction.of(
                    {
                        JsonMap(mapOf(
                            "duration" to Duration.serializer().serialize(it.duration)
                        ))
                    },
                    {
                        DeviceActivate(Duration.serializer().deserialize(requireNotNull((it as JsonMap).values["duration"])))
                    }
                ))
            }
        }

        class DeviceShutdown() : Activity<TestModel, Unit> {
            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.deviceState.set(SHUTDOWN)
                delay(5 * MINUTE)
                model.deviceState.set(OFF)
            }

            companion object {
                fun serializer(): Serializer<DeviceShutdown> = Serializer.of(InvertibleFunction.of(
                    { JsonMap.empty() }, { DeviceShutdown() }
                ))
            }
        }

        class AddMiscPower(val amount: Double) : Activity<TestModel, Unit> {
            context(SparkTaskScope<Unit>)
            override suspend fun effectModel(model: TestModel) {
                model.miscPower.increase(amount)
            }

            companion object {
                fun serializer(): Serializer<AddMiscPower> = Serializer.of(InvertibleFunction.of(
                    {
                        JsonMap(mapOf(
                            "amount" to JsonDouble(it.amount)
                        ))
                    },
                    {
                        AddMiscPower(((it as JsonMap).values["amount"] as JsonDouble).value)
                    }
                ))
            }
        }

        override fun activitySerializer(): Serializer<GroundedActivity<TestModel, *>> {
            return ActivitySerializer<TestModel>()
                .add("DeviceBoot", DeviceBoot.serializer())
                .add("DeviceActivate", DeviceActivate.serializer())
                .add("DeviceShutdown", DeviceShutdown.serializer())
                .add("AddMiscPower", AddMiscPower.serializer())
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
            )
        )
        simulation.runPlan(Plan(
            "Test Plan",
            ZERO,
            2 * HOUR + 5 * MINUTE,
            listOf(
                GroundedActivity(5 * MINUTE, DeviceBoot()),
                GroundedActivity(15 * MINUTE, DeviceActivate(10 * MINUTE), name="Observation 1"),
                GroundedActivity(26 * MINUTE, DeviceShutdown()),
                GroundedActivity(60 * MINUTE, DeviceActivate(20 * MINUTE), name="Observation 2"),
                GroundedActivity(90 * MINUTE, DeviceShutdown()),
                GroundedActivity(100 * MINUTE, DeviceActivate(60 * MINUTE), name="Observation 3"),
                GroundedActivity(110 * MINUTE, AddMiscPower(3.0)),
                GroundedActivity(115 * MINUTE, AddMiscPower(3.0)),
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
