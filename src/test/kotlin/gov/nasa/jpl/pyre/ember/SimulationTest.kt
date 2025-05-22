package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.InvertibleFunction.Companion.withInverse
import gov.nasa.jpl.pyre.ember.Simulation.SimulationSetup
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Task.PureStepResult.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.math.abs
import kotlin.test.assertContains

class SimulationTest {
    private var reports: MutableList<JsonValue> = mutableListOf()

    private fun emptySetup() = SimulationSetup(
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
    fun task_can_report_result() {
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
    fun task_can_allocate_cell() {
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
    fun task_can_read_cell() {
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

    @Test
    fun task_can_emit_effect() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 42, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("emit effect") {
                            Emit(x, 13) {
                                Read(x) {
                                    Report(JsonString("x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(mutableListOf(JsonString("x = 55")), reports)
    }

    @Test
    fun task_can_delay() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        spawn(Task.of("delay") {
                            Delay(30 * MINUTE) {
                                Complete(Unit)
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
    }

    @Test
    fun cells_can_step() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        // This is *not* a good way to implement stepping, since multiple steps, each < 1 minute,
                        // will not change the value, but a single >1 minute step would.
                        // It's fine for this test, though.
                        val x = allocate(Cell("x", 0, INT_SERIALIZER, { x, t -> x + (t / MINUTE).toInt() }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("step cell") {
                            Read(x) {
                                Report(JsonString("now x = $it")) {
                                    Delay(30 * MINUTE) {
                                        Read(x) {
                                            Report(JsonString("later x = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(mutableListOf(JsonString("now x = 0"), JsonString("later x = 30")), reports)
    }

    @Test
    fun parallel_tasks_do_not_observe_each_other() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Task A") {
                            Emit(x, 5) {
                                Read(x) {
                                    Report(JsonString("A says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Task B") {
                            Emit(x, 3) {
                                Read(x) {
                                    Report(JsonString("B says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Task C") {
                            Read(x) {
                                Report(JsonString("C says: x = $it")) {
                                    Read(x) {
                                        Report(JsonString("C still says: x = $it")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assert(reports.size == 4)
        // Order of reports is largely non-deterministic because these tasks are running in parallel
        assertContains(reports, JsonString("A says: x = 15"))
        assertContains(reports, JsonString("B says: x = 13"))
        assertContains(reports, JsonString("C says: x = 10"))
        assertContains(reports, JsonString("C still says: x = 10"))
    }

    @Test
    fun parallel_tasks_join_effects_at_each_delay() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Task A") {
                            Emit(x, 5) {
                                Read(x) {
                                    Report(JsonString("A says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Task B") {
                            Read(x) {
                                Report(JsonString("B first says: x = $it")) {
                                    Delay(ZERO) {
                                        Read(x) {
                                            Report(JsonString("B next says: x = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assert(reports.size == 3)
        // Order of reports is largely non-deterministic because these tasks are running in parallel
        assertContains(reports, JsonString("A says: x = 15"))
        assertContains(reports, JsonString("B first says: x = 10"))
        assertContains(reports, JsonString("B next says: x = 15"))
    }

    @Test
    fun sequential_unobserved_effects_are_joined_using_effect_trait() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        // Note: This is *not* a correct effect trait, but it's simple and lets us observe what's happening better.
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = 0
                            override fun sequential(first: Int, second: Int) = first + second + 100
                        }))
                        spawn(Task.of("emit effect") {
                            Emit(x, 5) {
                                Emit(x, 6) {
                                    Read(x) {
                                        Report(JsonString("x = $it")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        // Note: The 221 here comes from the violation of the "empty" part of the effect trait
        // - sequential(empty(), e) != e, because we add 100 every time we join effects.
        // This could change in future versions, but this is fine for the current test.
        assertEquals(mutableListOf(JsonString("x = 221")), reports)
    }

    @Test
    fun concurrent_effects_are_joined_using_effect_trait() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        // Note: This is *not* a correct effect trait, but it's simple and lets us observe what's happening better.
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right + 100
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Task A") {
                            Emit(x, 5) {
                                Read(x) {
                                    Report(JsonString("A says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Task B") {
                            Emit(x, 3) {
                                Read(x) {
                                    Report(JsonString("B says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Task C") {
                            Delay(ZERO) {
                                Read(x) {
                                    Report(JsonString("C says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assert(reports.size == 3)
        // Order of reports is largely non-deterministic because these tasks are running in parallel
        assertContains(reports, JsonString("A says: x = 15"))
        assertContains(reports, JsonString("B says: x = 13"))
        // Note: The 218 here comes from the violation of the "empty" part of the effect trait
        // - concurrent(empty(), e) != e, because we add 100 every time we join effects.
        // This could change in future versions, but this is fine for the current test.
        assertContains(reports, JsonString("C says: x = 218"))
    }

    @Test
    fun task_can_await_condition() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        spawn(Task.of("Await condition") {
                            Await(Condition.Complete(ZERO)) {
                                Complete(Unit)
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
    }

    @Test
    fun await_trivial_condition_runs_task_in_next_batch() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Awaiter") {
                            Await(Condition.Complete(ZERO)) {
                                Read(x) {
                                    Report(JsonString("Awaiter says: x = $it")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Counter") {
                            Emit(x, 1) {
                                Delay(ZERO) {
                                    Emit(x, 1) {
                                        Delay(ZERO) {
                                            Emit(x, 1) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(mutableListOf(JsonString("Awaiter says: x = 11")), reports)
    }

    @Test
    fun await_never_condition_does_not_run_task() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        spawn(Task.of("Awaiter") {
                            Await(Condition.Complete(null)) {
                                Report(JsonString("Awaiter ran!")) {
                                    Complete(Unit)
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assertEquals(0, reports.size)
    }

    @Test
    fun await_nontrivial_condition_runs_task_in_first_batch_after_condition_is_satisfied() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        val y = allocate(Cell("y", 12, INT_SERIALIZER, { y, _ -> y }, { y, n -> y + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Awaiter") {
                            val condition = Condition.Read(x) { xValue ->
                                Condition.Read(y) { yValue ->
                                    Condition.Complete(if (xValue >= yValue) ZERO else null)
                                }
                            }
                            Await(condition) {
                                Read(x) {
                                    Report(JsonString("Awaiter says: x = $it")) {
                                        Read(y) {
                                            Report(JsonString("Awaiter says: y = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Counter") {
                            Emit(x, 1) {
                                Delay(ZERO) {
                                    Emit(y, -1) {
                                        Delay(ZERO) {
                                            Emit(x, 1) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        with (JsonArray(reports)) {
            array {
                element {
                    assertEquals("Awaiter says: x = 11", string())
                }
                element {
                    assertEquals("Awaiter says: y = 11", string())
                }
                assert(atEnd())
            }
        }
    }

    private data class LinearDynamics(val value: Double, val rate: Double)
    private val linearDynamicsSerializer : Serializer<LinearDynamics> = Serializer.of(
        { d: LinearDynamics -> JsonMap(mapOf("value" to JsonDouble(d.value), "rate" to JsonDouble(d.rate))) }
        withInverse
        { with((it as JsonMap).values) { LinearDynamics((get("value") as JsonDouble).value, (get("rate") as JsonDouble).value) } }
    )
    private fun linearDynamicsStep(d: LinearDynamics, t: Duration) = LinearDynamics(d.value + d.rate * (t ratioOver SECOND), d.rate)
    private fun linearCell(name: String, value: Double, rate: Double) = Cell(
        name,
        LinearDynamics(value, rate),
        linearDynamicsSerializer,
        ::linearDynamicsStep,
        { d, e -> e ?: d },
        object : Cell.EffectTrait<LinearDynamics?> {
            override fun empty() = null
            override fun concurrent(left: LinearDynamics?, right: LinearDynamics?) =
                if (left != null && right != null) {
                    throw IllegalArgumentException("Concurrent non-commuting effects")
                } else {
                    left ?: right
                }
            override fun sequential(first: LinearDynamics?, second: LinearDynamics?) = second ?: first
        }
    )

    @Test
    fun await_nonzero_condition_waits_specified_time() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(linearCell("x", 10.0, 1.0))
                        spawn(Task.of("Awaiter") {
                            val cond = Condition.Read(x) {
                                Condition.Complete(with (it) {
                                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                                    if (value >= 20) {
                                        ZERO
                                    } else if (rate <= 0) {
                                        null
                                    } else {
                                        ((20 - value) / rate) ceilTimes SECOND
                                    }
                                })
                            }
                            Await(cond) {
                                Read(x) {
                                    Report(x.serializer.serialize(it)) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        assert(reports.size == 1)
        with (reports[0] as JsonMap) {
            assertEquals(2, values.size)
            assertNearlyEquals(20.0, double("value")!!)
            assertNearlyEquals(1.0, double("rate")!!)
        }
    }

    @Test
    fun await_nonzero_condition_can_be_interrupted() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(linearCell("x", 10.0, 1.0))
                        val y = allocate(Cell("y", 0, INT_SERIALIZER, { y, _ -> y }, { y, n -> y + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Awaiter") {
                            val cond = Condition.Read(x) {
                                Condition.Complete(with (it) {
                                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                                    if (value >= 20) {
                                        ZERO
                                    } else if (rate <= 0) {
                                        null
                                    } else {
                                        ((20 - value) / rate) ceilTimes SECOND
                                    }
                                })
                            }
                            Await(cond) {
                                Read(x) {
                                    Report(x.serializer.serialize(it)) {
                                        Read(y) {
                                            Report(JsonString("Awaiter says: y = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Interrupter") {
                            Emit(y, 1) {
                                Delay(6 * SECOND) {
                                    Emit(y, 1) {
                                        Emit(x, LinearDynamics(19.0, -0.5)) {
                                            Delay(20 * MINUTE) {
                                                Emit(y, 1) {
                                                    Emit(x, LinearDynamics(35.0, 0.0)) {
                                                        Complete(Unit)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        with (reports[0] as JsonMap) {
            assertEquals(2, values.size)
            assertNearlyEquals(35.0, double("value")!!)
            assertNearlyEquals(0.0, double("rate")!!)
        }
        // Condition is satisfied in reaction to last batch of the interrupter task, which is why y = 3
        assert(reports[1] == JsonString("Awaiter says: y = 3"))
    }

    @Test
    fun await_nonzero_condition_can_wait_after_interruption() {
        assertDoesNotThrow {
            Simulation.run(
                emptySetup().copy(
                    initialize = {
                        val x = allocate(linearCell("x", 10.0, 1.0))
                        val y = allocate(Cell("y", 0, INT_SERIALIZER, { y, _ -> y }, { y, n -> y + n }, object : Cell.EffectTrait<Int> {
                            override fun empty() = 0
                            override fun concurrent(left: Int, right: Int) = left + right
                            override fun sequential(first: Int, second: Int) = first + second
                        }))
                        spawn(Task.of("Awaiter") {
                            val cond = Condition.Read(x) {
                                Condition.Complete(with (it) {
                                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                                    if (value >= 20) {
                                        ZERO
                                    } else if (rate <= 0) {
                                        null
                                    } else {
                                        ((20 - value) / rate) ceilTimes SECOND
                                    }
                                })
                            }
                            Await(cond) {
                                Read(x) {
                                    Report(x.serializer.serialize(it)) {
                                        Read(y) {
                                            Report(JsonString("Awaiter says: y = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        spawn(Task.of("Interrupter") {
                            Emit(y, 1) {
                                Delay(6 * SECOND) {
                                    Emit(y, 1) {
                                        Emit(x, LinearDynamics(19.0, -0.5)) {
                                            Delay(20 * MINUTE) {
                                                Emit(y, 1) {
                                                    Emit(x, LinearDynamics(19.0, 0.1)) {
                                                        Complete(Unit)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    },
                    endTime = HOUR,
                )
            )
        }
        with (reports[0] as JsonMap) {
            assertEquals(2, values.size)
            // Condition isn't satisfied until it waits long enough for x to grow to 20
            assertNearlyEquals(20.0, double("value")!!)
            assertNearlyEquals(0.1, double("rate")!!)
        }
        assert(reports[1] == JsonString("Awaiter says: y = 3"))
    }

    @Test
    fun empty_simulation_can_be_saved() {
        val setup = emptySetup().copy(endTime = 1.1 roundTimes MINUTE, finconTime = MINUTE)
        assertDoesNotThrow { Simulation.run(setup) }
        val fincon = JsonConditions.serializer().serialize(setup.finconCollector as JsonConditions)
        with (fincon as JsonMap) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "value"))
            }
        }
    }

    @Test
    fun cells_can_be_saved() {
        fun SimulationState.SimulationInitializer.initialize() {
            var x = allocate(linearCell("x", 10.0, 1.0))
            var y = allocate(linearCell("y", 10.0, -0.1))
        }
        val setup = emptySetup().copy(
            endTime = 1.1 roundTimes MINUTE,
            finconTime = MINUTE,
            initialize = { initialize() },
        )
        assertDoesNotThrow { Simulation.run(setup) }
        val fincon = JsonConditions.serializer().serialize(setup.finconCollector as JsonConditions)
        with (fincon as JsonMap) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "value"))
            }
            within("cells") {
                within("x", "value") {
                    assertNearlyEquals(70.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                within("y", "value") {
                    assertNearlyEquals(4.0, double("value")!!)
                    assertNearlyEquals(-0.1, double("rate")!!)
                }
            }
        }
    }

    @Test
    fun cells_can_be_restored() {
        fun SimulationState.SimulationInitializer.initialize() {
            var x = allocate(linearCell("x", 10.0, 1.0))
            var y = allocate(linearCell("y", 10.0, -0.1))
        }
        val setup = emptySetup().copy(
            endTime = 1.1 roundTimes MINUTE,
            finconTime = MINUTE,
            initialize = { initialize() },
        )
        assertDoesNotThrow { Simulation.run(setup) }
        val fincon = JsonConditions.serializer().serialize(setup.finconCollector as JsonConditions)

        val nextSetup = emptySetup().copy(
            endTime = 2.1 roundTimes MINUTE,
            inconProvider = JsonConditions.serializer().deserialize(fincon).getOrThrow(),
            initialize = { initialize() }
        )
        assertDoesNotThrow { Simulation.run(nextSetup) }
    }

    @Test
    fun tasks_can_be_saved() {
        fun SimulationState.SimulationInitializer.initialize() {
            var x = allocate(linearCell("x", 10.0, 1.0))
            var y = allocate(linearCell("y", 10.0, -0.1))

            spawn(Task.of("Complete Immediately") {
                Complete(Unit)
            })
            spawn(Task.of("Single Batch Task") {
                Read(x) { xDynamics ->
                    Read(y) { yDynamics ->
                        Report(JsonMap(mapOf(
                            "tag" to JsonString("Single Batch Task"),
                            "x" to x.serializer.serialize(xDynamics),
                            "y" to y.serializer.serialize(yDynamics),
                        ))) {
                            Complete(Unit)
                        }
                    }
                }
            })
            spawn(Task.of("Multi Batch Task") {
                Read(x) { xDynamics ->
                    Read(y) { yDynamics ->
                        Report(JsonMap(mapOf(
                            "tag" to JsonString("Multi Batch Task - 1"),
                            "x" to x.serializer.serialize(xDynamics),
                            "y" to y.serializer.serialize(yDynamics),
                        ))) {
                            Delay(90 * SECOND) {
                                Read(x) { xDynamics ->
                                    Read(y) { yDynamics ->
                                        Report(JsonMap(mapOf(
                                            "tag" to JsonString("Multi Batch Task - 2"),
                                            "x" to x.serializer.serialize(xDynamics),
                                            "y" to y.serializer.serialize(yDynamics),
                                        ))) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }
        val setup = emptySetup().copy(
            endTime = 1.1 roundTimes MINUTE,
            finconTime = MINUTE,
            initialize = { initialize() },
        )
        assertDoesNotThrow { Simulation.run(setup) }

        val fincon = JsonConditions.serializer().serialize(setup.finconCollector as JsonConditions)
        with (fincon as JsonMap) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "value"))
            }
            within("cells") {
                within("x", "value") {
                    assertNearlyEquals(70.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                within("y", "value") {
                    assertNearlyEquals(4.0, double("value")!!)
                    assertNearlyEquals(-0.1, double("rate")!!)
                }
            }
            within("tasks") {
                within("Complete Immediately", "state", "value") {
                    array {
                        element {
                            assertEquals("complete", string("type"))
                        }
                        assert(atEnd())
                    }
                }
                within("Single Batch Task", "state", "value") {
                    array {
                        element {
                            assertEquals("read", string("type"))
                            within("value") {
                                assertNearlyEquals(10.0, double("value")!!)
                                assertNearlyEquals(1.0, double("rate")!!)
                            }
                        }
                        element {
                            assertEquals("read", string("type"))
                            within("value") {
                                assertNearlyEquals(10.0, double("value")!!)
                                assertNearlyEquals(-0.1, double("rate")!!)
                            }
                        }
                        element {
                            assertEquals("report", string("type"))
                        }
                        element {
                            assertEquals("complete", string("type"))
                        }
                    }
                }
                within("Multi Batch Task", "state", "value") {
                    array {
                        element {
                            assertEquals("read", string("type"))
                            within("value") {
                                assertNearlyEquals(10.0, double("value")!!)
                                assertNearlyEquals(1.0, double("rate")!!)
                            }
                        }
                        element {
                            assertEquals("read", string("type"))
                            within("value") {
                                assertNearlyEquals(10.0, double("value")!!)
                                assertNearlyEquals(-0.1, double("rate")!!)
                            }
                        }
                        element {
                            assertEquals("report", string("type"))
                        }
                        element {
                            assertEquals("delay", string("type"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tasks_can_be_restored() {
        fun SimulationState.SimulationInitializer.initialize() {
            var x = allocate(linearCell("x", 10.0, 1.0))
            var y = allocate(linearCell("y", 10.0, -0.1))

            spawn(Task.of("Complete Immediately") {
                Complete(Unit)
            })
            spawn(Task.of("Single Batch Task") {
                Read(x) { xDynamics ->
                    Read(y) { yDynamics ->
                        // Add a delay 0 to make the report order deterministic, for easier verification
                        Delay(ZERO) {
                            Report(JsonMap(mapOf(
                                "tag" to JsonString("Single Batch Task"),
                                "x" to x.serializer.serialize(xDynamics),
                                "y" to y.serializer.serialize(yDynamics),
                            ))) {
                                Complete(Unit)
                            }
                        }
                    }
                }
            })
            spawn(Task.of("Multi Batch Task") {
                Read(x) { xDynamics ->
                    Read(y) { yDynamics ->
                        Report(JsonMap(mapOf(
                            "tag" to JsonString("Multi Batch Task - 1"),
                            "x" to x.serializer.serialize(xDynamics),
                            "y" to y.serializer.serialize(yDynamics),
                        ))) {
                            Delay(90 * SECOND) {
                                Read(x) { xDynamics ->
                                    Read(y) { yDynamics ->
                                        Report(JsonMap(mapOf(
                                            "tag" to JsonString("Multi Batch Task - 2"),
                                            "x" to x.serializer.serialize(xDynamics),
                                            "y" to y.serializer.serialize(yDynamics),
                                        ))) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }
        val setup = emptySetup().copy(
            endTime = 1.1 roundTimes MINUTE,
            finconTime = MINUTE,
            initialize = { initialize() },
        )
        assertDoesNotThrow { Simulation.run(setup) }

        with (JsonArray(reports)) {
            array {
                element {
                    assertEquals("Multi Batch Task - 1", string("tag"))
                    within("x") {
                        assertNearlyEquals(10.0, double("value")!!)
                        assertNearlyEquals(1.0, double("rate")!!)
                    }
                    within("y") {
                        assertNearlyEquals(10.0, double("value")!!)
                        assertNearlyEquals(-0.1, double("rate")!!)
                    }
                }
                element {
                    assertEquals("Single Batch Task", string("tag"))
                    within("x") {
                        assertNearlyEquals(10.0, double("value")!!)
                        assertNearlyEquals(1.0, double("rate")!!)
                    }
                    within("y") {
                        assertNearlyEquals(10.0, double("value")!!)
                        assertNearlyEquals(-0.1, double("rate")!!)
                    }
                }
                assert(atEnd())
                // No report for Multi Batch Task - 2, because the simulation ended during its delay
            }
        }

        // Clear the reports so we can see what happens in the second round of simulation
        reports.clear()

        val fincon = JsonConditions.serializer().serialize(setup.finconCollector as JsonConditions)
        val nextSetup = emptySetup().copy(
            endTime = 2.1 roundTimes MINUTE,
            inconProvider = JsonConditions.serializer().deserialize(fincon).getOrThrow(),
            initialize = { initialize() }
        )
        assertDoesNotThrow { Simulation.run(nextSetup) }
        with (JsonArray(reports)) {
            array {
                // There should be no reports for Single Batch or the first part of Multi Batch,
                // because those tasks have already run.
                // Internally, we'll replay them to get to the part of the task we'd like to resume,
                // but officially, they aren't part of the simulation execution & output.
                element {
                    assertEquals("Multi Batch Task - 2", string("tag"))
                    within("x") {
                        assertNearlyEquals(100.0, double("value")!!)
                        assertNearlyEquals(1.0, double("rate")!!)
                    }
                    within("y") {
                        assertNearlyEquals(1.0, double("value")!!)
                        assertNearlyEquals(-0.1, double("rate")!!)
                    }
                }
                assert(atEnd())
            }
        }
    }
}

// Copied from BasicSerializers, for the sake of testing ember without relying on spark
private val INT_SERIALIZER = Serializer.of({ i: Int -> JsonInt(i.toLong()) } withInverse { (it as JsonInt).value.toInt() })

private fun assertNearlyEquals(expected: Double, actual: Double) {
    assert(abs(expected - actual) < 1e-5)
}
