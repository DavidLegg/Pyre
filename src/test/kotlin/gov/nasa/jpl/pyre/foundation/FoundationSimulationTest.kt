package gov.nasa.jpl.pyre.foundation

import gov.nasa.jpl.pyre.array
import gov.nasa.jpl.pyre.kernel.*
import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.JsonConditions.Companion.decodeJsonConditionsFromJsonElement
import gov.nasa.jpl.pyre.kernel.SimpleSimulation
import gov.nasa.jpl.pyre.kernel.SimpleSimulation.SimulationSetup
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.*
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.onceWhenever
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.repeatingTask
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FoundationSimulationTest {
    private data class SimulationResult(
        val reports: List<JsonElement>,
        val fincon: JsonElement?,
    )

    private fun runSimulation(
        endTime: Duration,
        incon: JsonElement? = null,
        takeFincon: Boolean = false,
        initialize: suspend context (InitScope) () -> Unit,
    ): SimulationResult {
        assertDoesNotThrow {
            // Build a simulation that'll write reports to memory
            val reports = mutableListOf<JsonElement>()
            context (scope: BasicInitScope)
            suspend fun fullInit() {
                with(object : InitScope, BasicInitScope by scope {
                    override val contextName: Name? = null
                    override val simulationClock = resource("simulation_clock", Timer(ZERO, 1))
                    override val simulationEpoch = Instant.parse("2000-01-01T00:00:00Z")
                    override fun toString() = ""
                    override fun onStartup(name: Name, block: suspend context(TaskScope) () -> Unit) =
                        throw NotImplementedError()
                }) {
                    initialize()
                }
            }

            val simulation = SimpleSimulation(SimulationSetup(
                reportHandler = { value, type ->
                    reports.add(Json.encodeToJsonElement(Json.serializersModule.serializer(type), value))
                },
                inconProvider = incon?.let { Json.decodeJsonConditionsFromJsonElement(it) },
                initialize = { fullInit() },
            ))
            // Run the simulation to the end
            simulation.runUntil(endTime)
            // Cut a fincon, if requested
            val fincon = if (takeFincon) {
                Json.encodeToJsonElement(JsonConditions().also(simulation::save))
            } else null
            // Return all results, and let the simulation itself be garbage collected
            return SimulationResult(reports, fincon)
        }
    }

    private enum class PowerState { OFF, WARMUP, ON }

    @Test
    fun primitive_discrete_resources_can_be_created() {
        runSimulation(HOUR) {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)
        }
    }

    @Test
    fun primitive_discrete_resources_can_be_read() {
        val results = runSimulation(HOUR) {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)

            spawn("Reader", task {
                val i: Int = intR.getValue()
                val l: Long = longR.getValue()
                val b: Boolean = boolR.getValue()
                val s: String = stringR.getValue()
                val d: Double = doubleR.getValue()
                val f: Float = floatR.getValue()
                val e: PowerState = enumR.getValue()

                assertEquals(0, i)
                assertEquals(1L, l)
                assertEquals(false, b)
                assertEquals("string value", s)
                assertEquals(2.0, d)
                assertEquals(3.0f, f)
                assertEquals(PowerState.OFF, e)

                report("Done")
            })
        }

        // Ensure we simulated to the end, and therefore ran all the asserts, by seeing the "Done" message
        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Done", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun primitive_discrete_resources_can_be_read_during_init() {
        runSimulation(HOUR) {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)

            val i: Int = intR.getValue()
            val l: Long = longR.getValue()
            val b: Boolean = boolR.getValue()
            val s: String = stringR.getValue()
            val d: Double = doubleR.getValue()
            val f: Float = floatR.getValue()
            val e: PowerState = enumR.getValue()

            assertEquals(0, i)
            assertEquals(1L, l)
            assertEquals(false, b)
            assertEquals("string value", s)
            assertEquals(2.0, d)
            assertEquals(3.0f, f)
            assertEquals(PowerState.OFF, e)
        }
    }

    @Test
    fun primitive_discrete_resources_can_be_changed() {
        val results = runSimulation(HOUR) {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)

            spawn("Emitter", task {
                intR.emit { i: Int -> i + 1 }
                longR.emit { l: Long -> l + 1 }
                boolR.emit { b: Boolean -> !b }
                stringR.emit { s: String -> "new $s" }
                doubleR.emit { d: Double -> d * 2 }
                floatR.emit { f: Float -> f * 2 }
                enumR.emit { e: PowerState -> PowerState.WARMUP }
            })

            spawn("Reader", task {
                delay(5 * MINUTE)

                val i: Int = intR.getValue()
                val l: Long = longR.getValue()
                val b: Boolean = boolR.getValue()
                val s: String = stringR.getValue()
                val d: Double = doubleR.getValue()
                val f: Float = floatR.getValue()
                val e: PowerState = enumR.getValue()

                assertEquals(1, i)
                assertEquals(2L, l)
                assertEquals(true, b)
                assertEquals("new string value", s)
                assertEquals(4.0, d)
                assertEquals(6.0f, f)
                assertEquals(PowerState.WARMUP, e)

                report("Done")
            })
        }

        // Ensure we simulated to the end, and therefore ran all the asserts, by seeing the "Done" message
        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Done", string()) }
                assert(atEnd())
            }
        }
    }

    // Now that we've run a few tests verifying that all the tasks run to completion, I'm less concerned about it.
    // From here on out, we'll just drop assertions in the task code itself, trusting that they'll execute.

    @Test
    fun primitive_discrete_resources_can_be_derived() {
        runSimulation(HOUR) {
            val state = discreteResource("power", PowerState.OFF)
            val warmupPower = discreteResource("warmupPower", 3.0)
            val onPower = discreteResource("onPower", 10.0)
            val miscPower = discreteResource("miscPower", 2.0)
            // TODO: Consider a "derived resource builder", akin to TaskBuilder, to use coroutines to define the derivation...
            val devicePower = with (DiscreteResourceMonad) {
                bind (state) {
                    when (it) {
                        PowerState.OFF -> pure(0.0)
                        PowerState.WARMUP -> warmupPower
                        PowerState.ON -> onPower
                    }
                }
            }
            val totalPower = DiscreteResourceMonad.map(devicePower, miscPower) { d, m ->
                // I'm just adding things up here, but you could do arbitrarily complicated stuff here.
                d + m
            }

            spawn("Test", task {
                assertEquals(0.0, devicePower.getValue())
                assertEquals(2.0, totalPower.getValue())

                delay(5 * MINUTE)
                state.set(PowerState.WARMUP)
                assertEquals(3.0, devicePower.getValue())
                assertEquals(5.0, totalPower.getValue())

                delay(10 * MINUTE)
                warmupPower.emit { p: Double -> 2 * p }
                assertEquals(6.0, devicePower.getValue())
                assertEquals(8.0, totalPower.getValue())

                delay(10 * MINUTE)
                state.set(PowerState.ON)
                assertEquals(10.0, devicePower.getValue())
                assertEquals(12.0, totalPower.getValue())

                delay(10 * MINUTE)
                miscPower.emit { p: Double -> p + 1 }
                assertEquals(10.0, devicePower.getValue())
                assertEquals(13.0, totalPower.getValue())
            })
        }
    }

    @Test
    fun coroutine_tasks_can_restart() {
        val results = runSimulation(endTime=10.5 roundTimes MINUTE) {
            spawn("Report periodically", repeatingTask {
                delay(MINUTE)
                report("Report")
            })
        }

        assertEquals(10, results.reports.size)
        results.reports.forEach { assertEquals(JsonPrimitive("Report"), it) }
    }

    @Test
    fun tasks_can_await_condition_on_resources() {
        val results = runSimulation(HOUR) {
            val x = discreteResource("x", 1)
            val y = discreteResource("y", 5)

            spawn("Report x > y", task {
                await(x greaterThan y)
                report("Condition triggered: ${x.getValue()} > ${y.getValue()}")
            })

            spawn("Change values", task {
                delay(MINUTE)
                x.set(5)
                delay(MINUTE)
                y.set(6)
                delay(MINUTE)
                y.set(4)
                delay(MINUTE)
                x.set(10)
            })
        }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Condition triggered: 5 > 4", string())}
                assert(atEnd())
            }
        }
    }

    @Test
    fun reactions_against_discrete_resources() {
        val results = runSimulation(HOUR) {
            val minimum = discreteResource("minimum", 1)
            val maximum = discreteResource("maximum", 10)
            val setting = discreteResource("setting", 5)

            spawn("Minimum Monitor", onceWhenever(setting lessThan minimum) {
                report("Minimum violated: ${setting.getValue()} < ${minimum.getValue()}")
            })

            spawn("Maximum Monitor", onceWhenever(setting greaterThan maximum) {
                report("Maximum violated: ${setting.getValue()} > ${maximum.getValue()}")
            })

            spawn("Change Setting", task {
                for (s in listOf(6, 7, 9, 10, 11, 12, 10, 11, 6, 1, 0, -1, 0, 1, -4)) {
                    delay(SECOND)
                    setting.set(s)
                }
            })
        }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Maximum violated: 11 > 10", string()) }
                element { assertEquals("Maximum violated: 11 > 10", string()) }
                element { assertEquals("Minimum violated: 0 < 1", string()) }
                element { assertEquals("Minimum violated: -4 < 1", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun tasks_may_run_many_non_yielding_steps() {
        // Note: This is technically not specific to foundation, it only relies on kernel functionality.
        // However, it's hard to write a repeating task like this without coroutines.
        // The main concern here is stack overflow - it's easy to write non-obvious recursion into the task or cell
        // handling, such that an intensive task like this will blow up the stack.
        runSimulation(HOUR) {
            val counter = discreteResource("counter", 0)

            spawn("intensive task", task {
                repeat(100_000) {
                    counter.increment()
                    report("Counter is now ${counter.getValue()}")
                }
            })
        }
    }
}