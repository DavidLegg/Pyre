package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Simulation.SimulationSetup
import gov.nasa.jpl.pyre.spark.BasicSerializers.INT_SERIALIZER
import org.example.gov.nasa.jpl.pyre.core.*
import org.example.gov.nasa.jpl.pyre.core.Duration.Companion.HOUR
import org.example.gov.nasa.jpl.pyre.core.Duration.Companion.ZERO
import org.example.gov.nasa.jpl.pyre.core.JsonValue.*
import org.example.gov.nasa.jpl.pyre.core.Task.PureStepResult.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SimulationTest {
    private var reports: MutableList<JsonValue> = mutableListOf()

    fun emptySetup() = SimulationSetup(
        reportHandler = { reports.add(it) },
        inconProvider = null,
        finconCollector = JsonConditions(),
        finconTime = null,
        initialize = {},
        endTime = ZERO,
    )

    @Test
    fun empty_simulation_is_valid() {
        assertDoesNotThrow {
            Simulation.run(emptySetup().copy(endTime = HOUR))
        }
    }

    @Test
    fun simulation_can_report_result() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        spawn(Task.of("report result") {
                            Report(JsonString("result")) {
                                Complete(Unit)
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(mutableListOf(JsonString("result")), reports)
    }

    @Test
    fun simulation_can_allocate_cell() {
        // To minimize dependencies, just use a dummy version of effects, stepping, and effect trait.
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        allocate(Cell("x", 42, INT_SERIALIZER, { x, _ -> x }, { x, _ -> x }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = 0
                            override fun sequential(first: Int, second: Int) = 0
                        }))
                    },
                    endTime = HOUR,
                )
            )
        }
    }

    @Test
    fun simulation_can_read_cell() {
        // To minimize dependencies, just use a dummy version of effects, stepping, and effect trait.
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 42, INT_SERIALIZER, { x, _ -> x }, { x, _ -> x }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = 0
                            override fun sequential(first: Int, second: Int) = 0
                        }))
                        spawn(Task.of("read cell") {
                            Read(x) {
                                Report(JsonString("x = $it")) {
                                    Complete(Unit)
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(mutableListOf(JsonString("x = 42")), reports)
    }
}