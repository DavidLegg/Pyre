package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.array
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.JsonArray
import gov.nasa.jpl.pyre.ember.JsonValue.JsonString
import gov.nasa.jpl.pyre.ember.Simulation
import gov.nasa.jpl.pyre.ember.Simulation.SimulationSetup
import gov.nasa.jpl.pyre.ember.SimulationState
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.resources.discrete.*
import gov.nasa.jpl.pyre.string
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class SparkSimulationTest {
    val reports = mutableListOf<JsonValue>()

    private fun setup(initialize: SimulationState.SimulationInitializer.() -> Unit): SimulationSetup {
        return SimulationSetup(
            reportHandler = reports::add,
            inconProvider = null,
            finconCollector = JsonConditions(),
            finconTime = null,
            endTime = HOUR,
            initialize = initialize,
        )
    }

    private enum class PowerState { OFF, WARMUP, ON }

    @Test
    fun primitive_discrete_resources_can_be_created() {
        val setup = setup {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)
        }

        assertDoesNotThrow { Simulation.run(setup) }
    }

    @Test
    fun primitive_discrete_resources_can_be_read() {
        val setup = setup {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)

            spawn(task("Reader") {
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

                report(JsonString("Done"))
            })
        }

        assertDoesNotThrow { Simulation.run(setup) }

        // Ensure we simulated to the end, and therefore ran all the asserts, by seeing the "Done" message
        with (JsonArray(reports)) {
            array {
                element { assertEquals("Done", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun primitive_discrete_resources_can_be_changed() {
        val setup = setup {
            val intR: MutableResource<Discrete<Int>> = discreteResource("intR", 0)
            val longR: MutableResource<Discrete<Long>> = discreteResource("longR", 1L)
            val boolR: MutableResource<Discrete<Boolean>> = discreteResource("boolR", false)
            val stringR: MutableResource<Discrete<String>> = discreteResource("stringR", "string value")
            val doubleR: MutableResource<Discrete<Double>> = discreteResource("doubleR", 2.0)
            val floatR: MutableResource<Discrete<Float>> = discreteResource("floatR", 3.0f)
            val enumR: MutableResource<Discrete<PowerState>> = discreteResource("enumR", PowerState.OFF)

            spawn(task("Emitter") {
                intR.emit { i: Int -> i + 1 }
                longR.emit { l: Long -> l + 1 }
                boolR.emit { b: Boolean -> !b }
                stringR.emit { s: String -> "new $s" }
                doubleR.emit { d: Double -> d * 2 }
                floatR.emit { f: Float -> f * 2 }
                enumR.emit { e: PowerState -> PowerState.WARMUP }
            })

            spawn(task("Reader") {
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

                report(JsonString("Done"))
            })
        }

        assertDoesNotThrow { Simulation.run(setup) }

        // Ensure we simulated to the end, and therefore ran all the asserts, by seeing the "Done" message
        with (JsonArray(reports)) {
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
        val setup = setup {
            val state = discreteResource("power", PowerState.OFF)
            val warmupPower = discreteResource("warmupPower", 3.0)
            val onPower = discreteResource("onPower", 10.0)
            val miscPower = discreteResource("miscPower", 2.0)
            // TODO: clean these up using multi-arg monad methods
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
            val totalPower = with (DiscreteResourceMonad) {
                bind (devicePower) { d: Double ->
                    map (miscPower) { m: Double -> d + m }
                }
            }

            spawn(task("Test") {
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

        assertDoesNotThrow { Simulation.run(setup) }
    }
}