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
}