package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.checkActivities
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.checkChannel
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.finished
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.reports
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.reportsDiscrete
import gov.nasa.jpl.pyre.foundation.SimulationResultsAssertions.unfinished
import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.JsonConditions
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulationTest.ModelWithResources.DummyActivity
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulationTest.PowerState.*
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulationTest.TestModel.*
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.discardReports
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.*
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults
import gov.nasa.jpl.pyre.kernel.InconProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PlanSimulationTest {
    class EmptyModel(context: InitScope)

    @Test
    fun empty_model_can_be_created() {
        assertDoesNotThrow {
            val epoch = Instant.parse("2020-01-01T00:00:00Z")
            val simulation = PlanSimulation(
                reportHandler = discardReports,
                start = epoch,
                constructModel = ::EmptyModel,
            )
            simulation.runUntil(Instant.parse("2020-01-01T01:00:00Z"))
        }
    }

    class ModelWithResources {
        val intR: MutableIntResource
        val longR: MutableLongResource
        val boolR: MutableBooleanResource
        val stringR: MutableStringResource
        val doubleR: MutableDoubleResource
        val floatR: MutableFloatResource
        val enumR: MutableDiscreteResource<PowerState>

        constructor(context: InitScope) {
            with(context) {
                intR = discreteResource("intR", 0).registered()
                longR = discreteResource("longR", 1L).registered()
                boolR = discreteResource("boolR", false).registered()
                stringR = discreteResource("stringR", "string value").registered()
                doubleR = discreteResource("doubleR", 2.0).registered()
                floatR = discreteResource("floatR", 3.0f).registered()
                enumR = discreteResource("enumR", OFF).registered()

                assertEquals(0, intR.getValue())
                assertEquals(1L, longR.getValue())
                assertEquals(false, boolR.getValue())
                assertEquals("string value", stringR.getValue())
                assertEquals(2.0, doubleR.getValue())
                assertEquals(3.0f, floatR.getValue())
                assertEquals(OFF, enumR.getValue())

                spawn("Reader", task {
                    assertEquals(0, intR.getValue())
                    assertEquals(1L, longR.getValue())
                    assertEquals(false, boolR.getValue())
                    assertEquals("string value", stringR.getValue())
                    assertEquals(2.0, doubleR.getValue())
                    assertEquals(3.0f, floatR.getValue())
                    assertEquals(OFF, enumR.getValue())

                    contextOf<TaskScope>().stdout.report("Reader done")
                })
            }
        }

        @Serializable
        class DummyActivity() : Activity<ModelWithResources> {
            context(scope: TaskScope)
            override suspend fun effectModel(model: ModelWithResources) {}
        }

        companion object {
            val JSON_FORMAT = Json {
                serializersModule = SerializersModule {
                    contextual(Instant::class, String.serializer().alias(
                        InvertibleFunction.of(Instant::parse, Instant::toString)))

                    activities {
                        activity(DummyActivity::class)
                    }
                }
            }
        }
    }

    @Test
    fun model_with_resources_can_be_created() {
        val reports = MutableSimulationResults()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation(
            reportHandler = reports.reportHandler(),
            start = epoch,
            constructModel = ::ModelWithResources,
        )
        simulation.runUntil(Instant.parse("2020-01-01T01:00:00Z"))

        with (reports.toSimulationResults()) {
            checkChannel<Discrete<Int>>("intR") {
                reportsDiscrete("2020-01-01T00:00:00Z", 0)
            }
            checkChannel<Discrete<Long>>("longR") {
                reportsDiscrete("2020-01-01T00:00:00Z", 1L)
            }
            checkChannel<Discrete<Boolean>>("boolR") {
                reportsDiscrete("2020-01-01T00:00:00Z", false)
            }
            checkChannel<Discrete<String>>("stringR") {
                reportsDiscrete("2020-01-01T00:00:00Z", "string value")
            }
            checkChannel<Discrete<Double>>("doubleR") {
                reportsDiscrete("2020-01-01T00:00:00Z", 2.0)
            }
            checkChannel<Discrete<Float>>("floatR") {
                reportsDiscrete("2020-01-01T00:00:00Z", 3.0f)
            }
            checkChannel<Discrete<PowerState>>("enumR") {
                reportsDiscrete("2020-01-01T00:00:00Z", OFF)
            }
            // Ensure the reader task finished, which proves those assertions ran
            checkChannel<String>("stdout") {
                reports("2020-01-01T00:00:00Z", "Reader done")
            }
        }
    }

    @Test
    fun activities_can_be_created() {
        val reports = MutableSimulationResults()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation(
            reportHandler = reports.reportHandler(),
            start = epoch,
            constructModel = ::ModelWithResources,
        )
        simulation.runPlan(
            Plan(
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

        with (reports.toSimulationResults()) {
            checkActivities {
                finished("Activity 1", "Type A", "2020-01-01T00:05:00Z", "2020-01-01T00:05:00Z")
                finished("Activity 2", "Type B", "2020-01-01T00:15:00Z", "2020-01-01T00:15:00Z")
                finished("Activity 3", "Type C", "2020-01-01T00:45:00Z", "2020-01-01T00:45:00Z")
            }
        }
    }

    enum class PowerState { OFF, WARMUP, STANDBY, ON, SHUTDOWN }

    class TestModel {
        val deviceState: MutableDiscreteResource<PowerState>
        val powerTable: Map<PowerState, Double>
        val miscPower: MutableDoubleResource
        val totalPower: DoubleResource

        constructor(context: InitScope) {
            with(context) {
                deviceState = discreteResource("deviceState", OFF).registered()
                powerTable = mapOf(
                    OFF to 0.0,
                    WARMUP to 5.0,
                    STANDBY to 1.0,
                    ON to 10.0,
                    SHUTDOWN to 1.0,
                )
                miscPower = discreteResource("miscPower", 0.0).registered()
                totalPower = (map(deviceState, powerTable::getValue) + miscPower)
                    .named { "totalPower" }
                    .registered()

                spawn("Overheat Protection", whenever(
                    (totalPower greaterThan 15.0) and (deviceState notEquals OFF)) {
                    contextOf<TaskScope>().stderr.report("Overheat Protection triggered!")
                    spawn(DeviceShutdown(), this@TestModel)
                    await(deviceState equals OFF)
                })
            }
        }

        @Serializable
        class DeviceBoot() : Activity<TestModel> {
            context(scope: TaskScope)
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

        @Serializable
        class DeviceActivate(val duration: Duration) : Activity<TestModel> {
            context(scope: TaskScope)
            override suspend fun effectModel(model: TestModel) {
                if (model.deviceState.getValue() != STANDBY) {
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
            context(scope: TaskScope)
            override suspend fun effectModel(model: TestModel) {
                model.deviceState.set(SHUTDOWN)
                delay(5 * MINUTE)
                model.deviceState.set(OFF)
            }
        }

        @Serializable
        class AddMiscPower(val amount: Double) : Activity<TestModel> {
            context(scope: TaskScope)
            override suspend fun effectModel(model: TestModel) {
                model.miscPower.increase(amount)
            }
        }

        companion object {
            val JSON_FORMAT = Json {
                serializersModule = SerializersModule {
                    contextual(Instant::class, String.serializer().alias(
                        InvertibleFunction.of(Instant::parse, Instant::toString)))

                    activities {
                        activity(DeviceBoot::class)
                        activity(DeviceActivate::class)
                        activity(DeviceShutdown::class)
                        activity(AddMiscPower::class)
                    }
                }
            }
        }
    }

    @Test
    fun activities_can_interact_with_model() {
        val reports = MutableSimulationResults()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation = PlanSimulation(
            reportHandler = reports.reportHandler(),
            start = epoch,
            constructModel = ::TestModel,
        )
        simulation.runPlan(
            Plan(
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

        with (reports.toSimulationResults()) {
            checkActivities {
                finished("DeviceBoot", "2020-01-01T00:05:00Z", "2020-01-01T00:10:00Z")
                finished("Observation 1", "DeviceActivate", "2020-01-01T00:15:00Z", "2020-01-01T00:25:00Z")
                finished("DeviceShutdown", "2020-01-01T00:26:00Z", "2020-01-01T00:31:00Z")
                finished("DeviceBoot", "2020-01-01T01:00:00Z", "2020-01-01T01:05:00Z")
                finished("Observation 2", "DeviceActivate", "2020-01-01T01:00:00Z", "2020-01-01T01:25:00Z")
                finished("DeviceShutdown", "2020-01-01T01:30:00Z", "2020-01-01T01:35:00Z")
                finished("DeviceBoot", "2020-01-01T01:40:00Z", "2020-01-01T01:45:00Z")
                unfinished("Observation 3", "DeviceActivate", "2020-01-01T01:40:00Z")
                finished("AddMiscPower", "2020-01-01T01:50:00Z")
                finished("AddMiscPower", "2020-01-01T01:55:00Z")
                finished("DeviceShutdown", "2020-01-01T01:55:00Z", "2020-01-01T02:00:00Z")
            }
            checkChannel<String>("stderr") {
                reports("2020-01-01T01:55:00Z", "Overheat Protection triggered!")
            }
            checkChannel<Discrete<PowerState>>("deviceState") {
                reportsDiscrete("2020-01-01T00:00:00Z", OFF)
                reportsDiscrete("2020-01-01T00:05:00Z", WARMUP)
                reportsDiscrete("2020-01-01T00:10:00Z", STANDBY)
                reportsDiscrete("2020-01-01T00:15:00Z", ON)
                reportsDiscrete("2020-01-01T00:25:00Z", STANDBY)
                reportsDiscrete("2020-01-01T00:26:00Z", SHUTDOWN)
                reportsDiscrete("2020-01-01T00:31:00Z", OFF)
                reportsDiscrete("2020-01-01T01:00:00Z", WARMUP)
                reportsDiscrete("2020-01-01T01:05:00Z", STANDBY)
                reportsDiscrete("2020-01-01T01:05:00Z", ON)
                reportsDiscrete("2020-01-01T01:25:00Z", STANDBY)
                reportsDiscrete("2020-01-01T01:30:00Z", SHUTDOWN)
                reportsDiscrete("2020-01-01T01:35:00Z", OFF)
                reportsDiscrete("2020-01-01T01:40:00Z", WARMUP)
                reportsDiscrete("2020-01-01T01:45:00Z", STANDBY)
                reportsDiscrete("2020-01-01T01:45:00Z", ON)
                reportsDiscrete("2020-01-01T01:55:00Z", SHUTDOWN)
                reportsDiscrete("2020-01-01T02:00:00Z", OFF)
            }
            checkChannel<Discrete<Double>>("miscPower") {
                reportsDiscrete("2020-01-01T00:00:00Z", 0.0)
                reportsDiscrete("2020-01-01T01:50:00Z", 3.0)
                reportsDiscrete("2020-01-01T01:55:00Z", 6.0)
            }
            checkChannel<Discrete<Double>>("totalPower") {
                reportsDiscrete("2020-01-01T00:00:00Z", 0.0)
                reportsDiscrete("2020-01-01T00:05:00Z", 5.0)
                reportsDiscrete("2020-01-01T00:10:00Z", 1.0)
                reportsDiscrete("2020-01-01T00:15:00Z", 10.0)
                reportsDiscrete("2020-01-01T00:25:00Z", 1.0)
                reportsDiscrete("2020-01-01T00:31:00Z", 0.0)
                reportsDiscrete("2020-01-01T01:00:00Z", 5.0)
                reportsDiscrete("2020-01-01T01:05:00Z", 1.0)
                reportsDiscrete("2020-01-01T01:05:00Z", 10.0)
                reportsDiscrete("2020-01-01T01:25:00Z", 1.0)
                reportsDiscrete("2020-01-01T01:35:00Z", 0.0)
                reportsDiscrete("2020-01-01T01:40:00Z", 5.0)
                reportsDiscrete("2020-01-01T01:45:00Z", 1.0)
                reportsDiscrete("2020-01-01T01:45:00Z", 10.0)
                reportsDiscrete("2020-01-01T01:50:00Z", 13.0)
                reportsDiscrete("2020-01-01T01:55:00Z", 16.0)
                reportsDiscrete("2020-01-01T01:55:00Z", 7.0)
                reportsDiscrete("2020-01-01T02:00:00Z", 6.0)
            }
        }
    }

    @Test
    fun activities_can_be_saved_and_restored() {
        val reports1 = MutableSimulationResults()
        val epoch = Instant.parse("2020-01-01T00:00:00Z")
        val simulation1 = PlanSimulation(
            reportHandler = reports1.reportHandler(),
            start = epoch,
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
        with (reports1.toSimulationResults()) {
            checkActivities {
                finished("DeviceBoot", "2020-01-01T00:03:00Z", "2020-01-01T00:08:00Z")
                unfinished("DeviceActivate", "2020-01-01T00:10:00Z")
            }
        }

        val fincon1 = TestModel.JSON_FORMAT.encodeToJsonElement(JsonConditions().also(simulation1::save))

        val reports2 = MutableSimulationResults()
        val simulation2 = PlanSimulation(
            reportHandler = reports2.reportHandler(),
            inconProvider = TestModel.JSON_FORMAT.decodeFromJsonElement<JsonConditions>(fincon1),
            constructModel = ::TestModel,
        )
        // Add an activity which will spawn a child, which will be active during the next fincon cycle
        simulation2.addActivities(listOf(
            GroundedActivity(Instant.parse("2020-01-01T01:58:00Z"), DeviceActivate(20 * MINUTE))
        ))
        simulation2.runUntil(Instant.parse("2020-01-01T02:00:00Z"))

        with (reports2.toSimulationResults()) {
            checkActivities {
                finished("DeviceActivate", "2020-01-01T00:10:00Z", "2020-01-01T01:10:00Z")
                finished("DeviceShutdown", "2020-01-01T01:20:00Z", "2020-01-01T01:25:00Z")
                unfinished("DeviceActivate", "2020-01-01T01:58:00Z")
                unfinished("DeviceBoot", "2020-01-01T01:58:00Z")
            }
        }

        val fincon2 = TestModel.JSON_FORMAT.encodeToJsonElement(JsonConditions().also(simulation2::save))

        val reports3 = MutableSimulationResults()
        val simulation3 = PlanSimulation(
            reportHandler = reports3.reportHandler(),
            inconProvider = TestModel.JSON_FORMAT.decodeFromJsonElement<JsonConditions>(fincon2),
            constructModel = ::TestModel,
        )
        simulation3.runUntil(Instant.parse("2020-01-01T03:00:00Z"))
        with(reports3.toSimulationResults()) {
            checkActivities {
                finished("DeviceBoot", "2020-01-01T01:58:00Z", "2020-01-01T02:03:00Z")
                finished("DeviceActivate", "2020-01-01T01:58:00Z", "2020-01-01T02:23:00Z")
            }
        }
    }
}
