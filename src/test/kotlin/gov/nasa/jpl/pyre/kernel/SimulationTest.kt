package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.kernel.SimpleSimulation.SimulationSetup
import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.allocate
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.read
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.spawn
import gov.nasa.jpl.pyre.kernel.JsonConditions.Companion.decodeJsonConditionsFromJsonElement
import gov.nasa.jpl.pyre.kernel.Task.PureStepResult.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.math.abs
import kotlin.reflect.typeOf
import kotlin.test.assertContains

class SimulationTest {
    private data class SimulationResult(
        val reports: List<JsonElement>,
        val fincon: JsonElement?,
    )

    private fun runSimulation(
        endTime: Duration,
        incon: JsonElement? = null,
        takeFincon: Boolean = false,
        initialize: context (BasicInitScope) () -> Unit,
    ): SimulationResult {
        assertDoesNotThrow {
            // Build a simulation that'll write reports to memory
            val reports = mutableListOf<JsonElement>()
            val simulation = SimpleSimulation(SimulationSetup(
                reportHandler = { value, type ->
                    reports.add(Json.encodeToJsonElement(serializer(type), value))
                },
                inconProvider = incon?.let { Json.decodeJsonConditionsFromJsonElement(it) },
                initialize = initialize,
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

    private fun intCounterCell(name: String, value: Int) = Cell(
        Name(name),
        value,
        typeOf<Int>(),
        { x, _ -> x },
        { l, r -> l andThen r }
    )

    @Serializable
    private data class LinearDynamics(val value: Double, val rate: Double)
    private fun linearDynamicsStep(d: LinearDynamics, t: Duration) = LinearDynamics(d.value + d.rate * (t ratioOver SECOND), d.rate)
    private fun linearCell(name: String, value: Double, rate: Double) = Cell(
        Name(name),
        LinearDynamics(value, rate),
        typeOf<LinearDynamics>(),
        ::linearDynamicsStep,
        { l, r -> l andThen r }
    )

    private fun clockCell(name: String, t: Duration) = Cell(
        Name(name),
        t,
        typeOf<Duration>(),
        { s, delta -> s + delta },
        { l, r -> l andThen r }
    )

    /**
     * "Patch" test-ism - I removed the Delay task step type in favor of using Await.
     * Rather than rewrite a bunch of tests, I'm re-building Delay in terms of Await.
     */
    private fun <T> Task.BasicTaskActions.Delay(time: Duration, clock: CellSet.CellHandle<Duration>, block: PureTaskStep<T>): Await<T> {
        val endTime = read(clock) + time
        return Await({ SatisfiedAt(endTime - it.read(clock)) }, block)
    }

    @Test
    fun empty_simulation_is_valid() {
        runSimulation(HOUR) {}
    }

    @Test
    fun task_can_report_result() {
        val results = runSimulation(HOUR) {
            spawn(Name("report result")) {
                it.report("result", typeOf<String>())
                Complete(Unit)
            }
        }
        assertEquals(mutableListOf(JsonPrimitive("result")), results.reports)
    }

    @Test
    fun task_can_allocate_cell() {
        runSimulation(HOUR) {
            allocate(intCounterCell("x", 42))
        }
    }

    @Test
    fun task_can_read_cell() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 42))
            spawn(Name("read cell")) {
                val xVal = it.read(x)
                it.report("x = $xVal", typeOf<String>())
                Complete(Unit)
            }
        }
        assertEquals(mutableListOf(JsonPrimitive("x = 42")), results.reports)
    }

    @Test
    fun task_can_read_cell_during_init() {
        runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 42))
            assertEquals(42, read(x))
        }
    }

    @Test
    fun task_can_emit_effect() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 42))
            spawn(Name("emit effect")) {
                it.emit(x) { it + 13 }
                val xVal = it.read(x)
                it.report("x = $xVal", typeOf<String>())
                Complete(Unit)
            }
        }
        assertEquals(mutableListOf(JsonPrimitive("x = 55")), results.reports)
    }

    // Note: There used to be an explicit "delay" StepResult, separate from Await.
    // Await has since subsumed Delay, but we're keeping this test around because later tests use "delay-style awaits"
    // to test cell stepping. I want to know that this very simple style of await works, then test that cells work,
    // then come back and test that more complicated styles of awaiting also work.
    @Test
    fun task_can_delay() {
        runSimulation(HOUR) {
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("delay")) {
                it.Delay(30 * MINUTE, clock) {
                    Complete(Unit)
                }
            }
        }
    }

    @Test
    fun cells_can_step() {
        val results = runSimulation(HOUR) {
            // This is *not* a good way to implement stepping, since multiple steps, each < 1 minute,
            // will not change the value, but a single >1 minute step would.
            // It's fine for this test, though.
            val x = allocate(Cell(Name("x"), 0, typeOf<Int>(), { x, t -> x + (t / MINUTE).toInt() }, { l, r -> l andThen r }))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("step cell")) {
                val xVal = it.read(x)
                it.report("now x = $xVal", typeOf<String>())
                it.Delay(30 * MINUTE, clock) {
                    val xVal = it.read(x)
                    it.report("later x = $xVal", typeOf<String>())
                    Complete(Unit)
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("now x = 0", string()) }
                element { assertEquals("later x = 30", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun parallel_tasks_do_not_observe_each_other() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            spawn(Name("Task A")) {
                it.emit(x) { it + 5 }
                val xVal = it.read(x)
                it.report("A says: x = $xVal", typeOf<String>())
                Complete(Unit)
            }
            spawn(Name("Task B")) {
                it.emit(x) { it + 3 }
                val xVal = it.read(x)
                it.report("B says: x = $xVal", typeOf<String>())
                Complete(Unit)
            }
            spawn(Name("Task C")) {
                val xVal = it.read(x)
                it.report("C says: x = $xVal", typeOf<String>())
                val xVal2 = it.read(x)
                it.report("C still says: x = $xVal2", typeOf<String>())
                Complete(Unit)
            }
        }
        with (results) {
            assert(reports.size == 4)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonPrimitive("A says: x = 15"))
            assertContains(reports, JsonPrimitive("B says: x = 13"))
            assertContains(reports, JsonPrimitive("C says: x = 10"))
            assertContains(reports, JsonPrimitive("C still says: x = 10"))
        }
    }

    @Test
    fun parallel_tasks_join_effects_at_each_delay() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Task A")) {
                it.emit(x) { it + 5 }
                val xVal = it.read(x)
                it.report("A says: x = $xVal", typeOf<String>())
                Complete(Unit)
            }
            spawn(Name("Task B")) {
                val xVal = it.read(x)
                it.report("B first says: x = $xVal", typeOf<String>())
                it.Delay(ZERO, clock) {
                    val xVal = it.read(x)
                    it.report("B next says: x = $xVal", typeOf<String>())
                    Complete(Unit)
                }
            }
        }
        with (results) {
            assert(reports.size == 3)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonPrimitive("A says: x = 15"))
            assertContains(reports, JsonPrimitive("B first says: x = 10"))
            assertContains(reports, JsonPrimitive("B next says: x = 15"))
        }
    }

    @Test
    fun concurrent_effects_are_joined_using_effect_trait() {
        val results = runSimulation(HOUR) {
            // Note: This is *not* a correct effect trait, but it's simple and lets us observe what's happening better.
            val x = allocate(Cell(Name("x"), 10, typeOf<Int>(), { x, _ -> x }, { l, r -> { 100 + r(l(it)) } }))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Task A")) {
                it.emit(x) { it + 5 }
                val xVal = it.read(x)
                it.report("A says: x = $xVal", typeOf<String>())
                Complete(Unit)
            }
            spawn(Name("Task B")) {
                it.emit(x) { it + 3 }
                val xVal = it.read(x)
                it.report("B says: x = $xVal", typeOf<String>())
                Complete(Unit)
            }
            spawn(Name("Task C")) {
                it.Delay(ZERO, clock) {
                    val xVal = it.read(x)
                    it.report("C says: x = $xVal", typeOf<String>())
                    Complete(Unit)
                }
            }
        }
        with (results) {
            assert(reports.size == 3)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonPrimitive("A says: x = 15"))
            assertContains(reports, JsonPrimitive("B says: x = 13"))
            assertContains(reports, JsonPrimitive("C says: x = 118"))
        }
    }

    @Test
    fun task_can_await_condition() {
        runSimulation(HOUR) {
            spawn(Name("Await condition")) {
                Await({ SatisfiedAt(ZERO) }) {
                    Complete(Unit)
                }
            }
        }
    }

    @Test
    fun await_trivial_condition_runs_task_in_next_batch() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Awaiter")) {
                Await({ SatisfiedAt(ZERO) }) {
                    val xVal = it.read(x)
                    it.report("Awaiter says: x = $xVal", typeOf<String>())
                    Complete(Unit)
                }
            }
            spawn(Name("Counter")) {
                it.emit(x) { it + 1 }
                it.Delay(ZERO, clock) {
                    it.emit(x) { it + 1 }
                    it.Delay(ZERO, clock) {
                        it.emit(x) { it + 1 }
                        Complete(Unit)
                    }
                }
            }
        }
        assertEquals(mutableListOf(JsonPrimitive("Awaiter says: x = 11")), results.reports)
    }

    @Test
    fun await_never_condition_does_not_run_task() {
        val results = runSimulation(HOUR) {
            spawn(Name("Awaiter")) {
                Await({ UnsatisfiedUntil(null) }) {
                    it.report("Awaiter ran!", typeOf<String>())
                    Complete(Unit)
                }
            }
        }
        assert(results.reports.isEmpty())
    }

    @Test
    fun await_nontrivial_condition_runs_task_in_first_batch_after_condition_is_satisfied() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            val y = allocate(intCounterCell("y", 12))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Awaiter")) {
                Await({
                    val xValue = it.read(x)
                    val yValue = it.read(y)
                    if (xValue >= yValue) SatisfiedAt(ZERO) else UnsatisfiedUntil(null)
                }) {
                    val xVal = it.read(x)
                    it.report("Awaiter says: x = $xVal", typeOf<String>())
                    val yVal = it.read(y)
                    it.report("Awaiter says: y = $yVal", typeOf<String>())
                    Complete(Unit)
                }
            }
            spawn(Name("Counter")) {
                it.emit(x) { it + 1 }
                it.Delay(ZERO, clock) {
                    it.emit(y) { it - 1 }
                    it.Delay(ZERO, clock) {
                        it.emit(x) { it + 1 }
                        Complete(Unit)
                    }
                }
            }
        }
        with (JsonArray(results.reports)) {
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

    @Test
    fun await_nonzero_condition_waits_specified_time() {
        val results = runSimulation(HOUR) {
            val x = allocate(linearCell("x", 10.0, 1.0))
            spawn(Name("Awaiter")) {
                Await({
                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                    with (it.read(x)) {
                        when {
                            value >= 20 -> SatisfiedAt(ZERO)
                            rate <= 0 -> UnsatisfiedUntil(null)
                            else -> SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }) {
                    val xVal = it.read(x)
                    it.report(xVal, typeOf<LinearDynamics>())
                    Complete(Unit)
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element {
                    assertNearlyEquals(20.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                assert(atEnd())
            }
        }
    }

    @Test
    fun await_nonzero_condition_can_be_interrupted() {
        val results = runSimulation(HOUR) {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(intCounterCell("y", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Awaiter")) {
                Await({
                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                    with (it.read(x)) {
                        when {
                            value >= 20 -> SatisfiedAt(ZERO)
                            rate <= 0 -> UnsatisfiedUntil(null)
                            else -> SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }) {
                    val xVal = it.read(x)
                    it.report(xVal, typeOf<LinearDynamics>())
                    val yVal = it.read(y)
                    it.report("Awaiter says: y = $yVal", typeOf<String>())
                    Complete(Unit)
                }
            }
            spawn(Name("Interrupter")) {
                it.emit(y) { it + 1 }
                it.Delay(6 * SECOND, clock) {
                    it.emit(y) { it + 1 }
                    it.emit(x) { LinearDynamics(19.0, -0.5) }
                    it.Delay(20 * MINUTE, clock) {
                        it.emit(y) { it + 1 }
                        it.emit(x) { LinearDynamics(35.0, 0.0) }
                        Complete(Unit)
                    }
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element {
                    assertNearlyEquals(35.0, double("value")!!)
                    assertNearlyEquals(0.0, double("rate")!!)
                }
                // Condition is satisfied in reaction to last batch of the interrupter task, which is why y = 3
                element { assertEquals("Awaiter says: y = 3", string())}
                assert(atEnd())
            }
        }
    }

    @Test
    fun await_nonzero_condition_can_wait_after_interruption() {
        val results = runSimulation(HOUR) {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(intCounterCell("y", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Awaiter")) {
                Await({
                    // Example implementation of a "greater than 20" condition for a linear dynamics type.
                    with (it.read(x)) {
                        when {
                            value >= 20 -> SatisfiedAt(ZERO)
                            rate <= 0 -> UnsatisfiedUntil(null)
                            else -> SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }) {
                    val xVal = it.read(x)
                    it.report(xVal, typeOf<LinearDynamics>())
                    val yVal = it.read(y)
                    it.report("Awaiter says: y = $yVal", typeOf<String>())
                    Complete(Unit)
                }
            }
            spawn(Name("Interrupter")) {
                it.emit(y) { it + 1 }
                it.Delay(6 * SECOND, clock) {
                    it.emit(y) { it + 1 }
                    it.emit(x) { LinearDynamics(19.0, -0.5) }
                    it.Delay(20 * MINUTE, clock) {
                        it.emit(y) { it + 1 }
                        it.emit(x) { LinearDynamics(19.0, 0.1) }
                        Complete(Unit)
                    }
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element {
                    // Condition isn't satisfied until it waits long enough for x to grow to 20
                    assertNearlyEquals(20.0, double("value")!!)
                    assertNearlyEquals(0.1, double("rate")!!)
                }
                element { assertEquals("Awaiter says: y = 3", string())}
                assert(atEnd())
            }
        }
    }

    @Test
    fun unsatisfied_condition_will_be_reevaluated() {
        val results = runSimulation(HOUR) {
            val x = allocate(linearCell("x", 10.0, 1.0))
            spawn(Name("Awaiter")) {
                Await({
                    if (it.read(x).value >= 15) SatisfiedAt(ZERO)
                    else UnsatisfiedUntil(2 * SECOND)
                }) {
                    val xVal = it.read(x)
                    it.report(xVal, typeOf<LinearDynamics>())
                    Complete(Unit)
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element {
                    // Condition repeatedly delays re-evaluation for 2s, so value is 16 by the time it's satisfied
                    assertNearlyEquals(16.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                assert(atEnd())
            }
        }
    }

    @Test
    fun unsatisfied_condition_reevaluation_can_be_interrupted() {
        val results = runSimulation(HOUR) {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Awaiter")) {
                Await({
                    if (it.read(x).value >= 15) SatisfiedAt(ZERO)
                    else UnsatisfiedUntil(MINUTE)
                }) {
                    val xVal = it.read(x)
                    it.report(xVal, typeOf<LinearDynamics>())
                    Complete(Unit)
                }
            }

            spawn(Name("Interrupter")) {
                it.Delay(5 * SECOND, clock) {
                    it.emit(x) { LinearDynamics(20.0, 0.0) }
                    Complete(Unit)
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element {
                    // Condition evaluates at first to Unsatisfied, with a 1-minute expiry.
                    // That's interrupted by the interrupter task, which sets the value to 20.
                    // Had it not been interrupted, it would have grown to 70.
                    assertNearlyEquals(20.0, double("value")!!)
                    assertNearlyEquals(0.0, double("rate")!!)
                }
                assert(atEnd())
            }
        }
    }

    @Test
    fun empty_simulation_can_be_saved() {
        val results = runSimulation(MINUTE, takeFincon = true) {}
        with (results.fincon!!) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "$"))
            }
        }
    }

    @Test
    fun cells_can_be_saved() {
        context (scope: BasicInitScope)
        fun initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))
        }
        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }
        with (results.fincon!!) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "$"))
            }
            within("cells") {
                within("x", "$") {
                    assertNearlyEquals(70.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                within("y", "$") {
                    assertNearlyEquals(4.0, double("value")!!)
                    assertNearlyEquals(-0.1, double("rate")!!)
                }
            }
        }
    }

    @Test
    fun cells_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))
        }
        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }
        runSimulation(2 * MINUTE, incon=results.fincon) { initialize() }
    }

    @Test
    fun tasks_can_be_saved() {
        context (scope: BasicInitScope)
        fun initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn(Name("Complete Immediately")) {
                Complete(Unit)
            }
            spawn(Name("Single Batch Task")) {
                val xDynamics = it.read(x)
                val yDynamics = it.read(y)
                it.report(JsonObject(mapOf(
                    "tag" to JsonPrimitive("Single Batch Task"),
                    "x" to Json.encodeToJsonElement(xDynamics),
                    "y" to Json.encodeToJsonElement(yDynamics),
                )), typeOf<JsonObject>())
                Complete(Unit)
            }
            spawn(Name("Multi Batch Task")) {
                val xDynamics = it.read(x)
                val yDynamics = it.read(y)
                it.report(JsonObject(mapOf(
                    "tag" to JsonPrimitive("Multi Batch Task - 1"),
                    "x" to Json.encodeToJsonElement(xDynamics),
                    "y" to Json.encodeToJsonElement(yDynamics),
                )), typeOf<JsonObject>())
                it.Delay(90 * SECOND, clock) {
                    val xDynamics = it.read(x)
                    val yDynamics = it.read(y)
                    it.report(JsonObject(mapOf(
                        "tag" to JsonPrimitive("Multi Batch Task - 2"),
                        "x" to Json.encodeToJsonElement(xDynamics),
                        "y" to Json.encodeToJsonElement(yDynamics),
                    )), typeOf<JsonObject>())
                    Complete(Unit)
                }
            }
        }

        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }

        with (results.fincon!!) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "$"))
            }
            within("cells") {
                within("x", "$") {
                    assertNearlyEquals(70.0, double("value")!!)
                    assertNearlyEquals(1.0, double("rate")!!)
                }
                within("y", "$") {
                    assertNearlyEquals(4.0, double("value")!!)
                    assertNearlyEquals(-0.1, double("rate")!!)
                }
            }
            within("tasks") {
                within("Multi Batch Task", "$") {
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
                    }
                }
            }
        }
    }

    @Test
    fun tasks_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn(Name("Complete Immediately")) {
                Complete(Unit)
            }
            spawn(Name("Single Batch Task")) {
                val xDynamics = it.read(x)
                val yDynamics = it.read(y)
                // Add a delay 0 to make the report order deterministic, for easier verification
                it.Delay(ZERO, clock) {
                    it.report(JsonObject(mapOf(
                        "tag" to JsonPrimitive("Single Batch Task"),
                        "x" to Json.encodeToJsonElement(xDynamics),
                        "y" to Json.encodeToJsonElement(yDynamics),
                    )), typeOf<JsonObject>())
                    Complete(Unit)
                }
            }
            spawn(Name("Multi Batch Task")) {
                val xDynamics = it.read(x)
                val yDynamics = it.read(y)
                it.report(JsonObject(mapOf(
                    "tag" to JsonPrimitive("Multi Batch Task - 1"),
                    "x" to Json.encodeToJsonElement(xDynamics),
                    "y" to Json.encodeToJsonElement(yDynamics),
                )), typeOf<JsonObject>())
                // Since this delay spans a fincon boundary, do the delay correctly by reading a clock.
                // Abusing Await like I've done elsewhere, directly returning the time, causes buggy behavior with fincons.
                it.Delay(90 * SECOND, clock) {
                    val xDynamics = it.read(x)
                    val yDynamics = it.read(y)
                    it.report(JsonObject(mapOf(
                        "tag" to JsonPrimitive("Multi Batch Task - 2"),
                        "x" to Json.encodeToJsonElement(xDynamics),
                        "y" to Json.encodeToJsonElement(yDynamics),
                    )), typeOf<JsonObject>())
                    Complete(Unit)
                }
            }
        }

        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }

        with (JsonArray(results.reports)) {
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

        val nextResults = runSimulation(2 * MINUTE, incon = results.fincon) { initialize() }

        with (JsonArray(nextResults.reports)) {
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

    @Test
    fun restart_tasks_can_run_indefinitely() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Repeater")) {
                it.Delay(10 * MINUTE, clock) {
                    it.emit(x) { it + 1 }
                    val time = it.read(clock)
                    val xVal = it.read(x)
                    it.report("x = $xVal at $time", typeOf<String>())
                    Restart<Unit>()
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("x = 1 at 00:10:00.000000", string()) }
                element { assertEquals("x = 2 at 00:20:00.000000", string()) }
                element { assertEquals("x = 3 at 00:30:00.000000", string()) }
                element { assertEquals("x = 4 at 00:40:00.000000", string()) }
                element { assertEquals("x = 5 at 00:50:00.000000", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun restart_tasks_save_fincon_data_since_last_restart_only() {
        val results = runSimulation(59 * MINUTE, takeFincon = true) {
            val x = allocate(intCounterCell("x", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Repeater")) {
                it.Delay(10 * MINUTE, clock) {
                    it.emit(x) { it + 1 }
                    val time = it.read(clock)
                    val xVal = it.read(x)
                    it.report("x = $xVal at $time", typeOf<String>())
                    Restart<Unit>()
                }
            }
        }
        with (results.fincon!!) {
            within("simulation") {
                assertEquals("00:59:00.000000", string("time", "$"))
            }
            within("cells") {
                assertEquals(5, int("x", "$"))
                assertEquals("00:59:00.000000", string("clock", "$"))
            }
            within("tasks", "Repeater") {
                within("$") {
                    array {
                        element {
                            assertEquals("read", string("type"))
                            assertEquals("00:50:00.000000", string("value"))
                        }
                        assert(atEnd())
                    }
                }
                assertEquals("00:59:00.000000", string("time", "$"))
            }
        }
    }

    @Test
    fun restart_tasks_replay_from_last_restart_when_restoring() {
        // This is a deeply cursed way to leak state out of the simulation.
        // This should never be done in production, but for the sake of testing,
        // it's a useful way to observe the replay, when that replay *should* be invisible.
        var plays = 0
        context (scope: BasicInitScope)
        fun initialize() {
            val x = allocate(intCounterCell("x", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("Repeater")) {
                plays++
                it.Delay(10 * MINUTE, clock) {
                    it.emit(x) { it + 1 }
                    val time = it.read(clock)
                    val xVal = it.read(x)
                    it.report("x = $xVal at $time", typeOf<String>())
                    Restart<Unit>()
                }
            }
        }

        val results = runSimulation(59 * MINUTE, takeFincon = true) { initialize() }
        assertEquals(6, plays)
        plays = 0

        runSimulation(HOUR + 5 * MINUTE, incon = results.fincon) { initialize() }
        // 1 play during the "restore" phase, to get the task re-initialized
        // 1 play during actual simulation, at time = 1 hour
        assertEquals(2, plays)
    }

    @Test
    fun tasks_can_spawn_children() {
        val results = runSimulation(HOUR) {
            spawn(Name("Parent")) {
                it.report("Parent's report", typeOf<String>())
                Spawn(Name("Child"), {
                    it.report("Child's report", typeOf<String>())
                    Complete(Unit)
                }) {
                    Complete(Unit)
                }
            }
        }
        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Parent's report", string()) }
                element { assertEquals("Child's report", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun child_tasks_run_in_parallel_with_parent_and_each_other() {
        val results = runSimulation(HOUR) {
            var x = allocate(intCounterCell("x", 0))
            val clock = allocate(clockCell("clock", ZERO))

            spawn(Name("Counter")) {
                it.emit(x) { it + 1 }
                it.Delay(ZERO, clock) {
                    it.emit(x) { it + 1 }
                    it.Delay(ZERO, clock) {
                        it.emit(x) { it + 1 }
                        it.Delay(ZERO, clock) {
                            it.emit(x) { it + 1 }
                            Complete(Unit)
                        }
                    }
                }
            }

            spawn(Name("P")) {
                val xVal = it.read(x)
                it.report("Tick 0: P says: x = $xVal", typeOf<String>())
                Spawn(Name("C1"), {
                    val xVal = it.read(x)
                    it.report("Tick 1: C1 says: x = $xVal", typeOf<String>())
                    it.Delay(ZERO, clock) {
                        val xVal = it.read(x)
                        it.report("Tick 2: C1 says: x = $xVal", typeOf<String>())
                        Complete(Unit)
                    }
                }) {
                    Spawn(Name("C2"), {
                        val xVal = it.read(x)
                        it.report("Tick 1: C2 says: x = $xVal", typeOf<String>())
                        it.Delay(ZERO, clock) {
                            val xVal = it.read(x)
                            it.report("Tick 2: C2 says: x = $xVal", typeOf<String>())
                            Complete(Unit)
                        }
                    }) {
                        it.Delay(ZERO, clock) {
                            val xVal = it.read(x)
                            it.report("Tick 1: P says: x = $xVal", typeOf<String>())
                            it.Delay(ZERO, clock) {
                                val xVal = it.read(x)
                                it.report("Tick 2: P says: x = $xVal", typeOf<String>())
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }

        with (results) {
            assertEquals(7, reports.size)
            assertEquals(JsonPrimitive("Tick 0: P says: x = 0"), reports[0])
            // Ordering of reports in the same tick is non-deterministic
            assertEquals(
                setOf(
                    JsonPrimitive("Tick 1: P says: x = 1"),
                    JsonPrimitive("Tick 1: C1 says: x = 1"),
                    JsonPrimitive("Tick 1: C2 says: x = 1"),
                ),
                reports.subList(1, 4).toSet())
            assertEquals(
                setOf(
                    JsonPrimitive("Tick 2: P says: x = 2"),
                    JsonPrimitive("Tick 2: C1 says: x = 2"),
                    JsonPrimitive("Tick 2: C2 says: x = 2"),
                ),
                reports.subList(4, 7).toSet())
        }
    }

    @Test
    fun child_tasks_can_be_saved() {
        val results = runSimulation(HOUR, takeFincon = true) {
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("P")) {
                it.report("P -- 1", typeOf<String>())
                Spawn(Name("C"), {
                    it.report("C -- 1", typeOf<String>())
                    it.Delay(45 * MINUTE, clock) {
                        // 00:45:00
                        it.report("C -- 2", typeOf<String>())
                        Complete(Unit)
                    }
                }) {
                    it.Delay(30 * MINUTE, clock) {
                        // 00:30:00
                        it.report("P -- 2", typeOf<String>())
                        Spawn(Name("D"), {
                            it.report("D -- 1", typeOf<String>())
                            it.Delay(45 * MINUTE, clock) {
                                // 01:15:00
                                it.report("D -- 2", typeOf<String>())
                                Complete(Unit)
                            }
                        }) {
                            it.Delay(HOUR, clock) {
                                // 01:30:00
                                it.report("P -- 3", typeOf<String>())
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }
        with (results.fincon!!) {
            within("simulation") {
                assertEquals("01:00:00.000000", string("time", "$"))
            }
            within("tasks") {
                within("P") {
                    within("children", "$") {
                        assert((this as JsonArray).size == 2)
                        assert(JsonArray(listOf(JsonPrimitive("P"))) in this)
                        // Note: The child id is now independent of the parent ID, at least in theory.
                        assert(JsonArray(listOf(JsonPrimitive("D"))) in this)
                    }

                    within("$") {
                        array {
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("parent", string("branch"))
                            }
                            element {
                                assertEquals("read", string("type"))
                                assertEquals("00:00:00.000000", string("value"))
                            }
                            element { assertEquals("await", string("type")) }
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("parent", string("branch"))
                            }
                            element {
                                assertEquals("read", string("type"))
                                assertEquals("00:30:00.000000", string("value"))
                            }
                            assert(atEnd())
                        }
                    }
                    assertEquals("01:00:00.000000", string("time", "$"))
                }

                within("D") {
                    within("$") {
                        array {
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("parent", string("branch"))
                            }
                            element {
                                assertEquals("read", string("type"))
                                assertEquals("00:00:00.000000", string("value"))
                            }
                            element { assertEquals("await", string("type")) }
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("child", string("branch"))
                            }
                            element {
                                assertEquals("read", string("type"))
                                assertEquals("00:30:00.000000", string("value"))
                            }
                            assert(atEnd())
                        }
                    }
                    assertEquals("01:00:00.000000", string("time", "$"))
                }
            }
        }
    }

    @Test
    fun child_tasks_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("P")) {
                it.report("P -- 1", typeOf<String>())
                Spawn(Name("C"), {
                    it.report("C -- 1", typeOf<String>())
                    it.Delay(45 * MINUTE, clock) {
                        // 00:45:00
                        it.report("C -- 2", typeOf<String>())
                        Complete(Unit)
                    }
                }) {
                    it.Delay(30 * MINUTE, clock) {
                        // 00:30:00
                        it.report("P -- 2", typeOf<String>())
                        Spawn(Name("D"), {
                            it.report("D -- 1", typeOf<String>())
                            it.Delay(45 * MINUTE, clock) {
                                // 01:15:00
                                it.report("D -- 2", typeOf<String>())
                                Complete(Unit)
                            }
                        }) {
                            it.Delay(HOUR, clock) {
                                // 01:30:00
                                it.report("P -- 3", typeOf<String>())
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }
        val results = runSimulation(HOUR, takeFincon = true) { initialize() }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("P -- 1", string()) }
                element { assertEquals("C -- 1", string()) }
                element { assertEquals("P -- 2", string()) }
                element { assertEquals("D -- 1", string()) }
                element { assertEquals("C -- 2", string()) }
                assert(atEnd())
            }
        }

        val nextResults = runSimulation(2 * HOUR, incon = results.fincon) { initialize() }

        with (JsonArray(nextResults.reports)) {
            array {
                element { assertEquals("D -- 2", string()) }
                element { assertEquals("P -- 3", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun children_of_repeating_tasks_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val n = allocate(intCounterCell("n", 1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn(Name("Repeater")) {
                it.Delay(10 * MINUTE, clock) {
                    val nValue = it.read(n)
                    Spawn(Name("Child $nValue"), {
                        val time = it.read(clock)
                        it.report("Child $nValue start at $time", typeOf<String>())
                        it.Delay(45 * MINUTE, clock) {
                            val time = it.read(clock)
                            it.report("Child $nValue end at $time", typeOf<String>())
                            Complete(Unit)
                        }
                    }) {
                        it.emit(n) { it + 1 }
                        Restart<Unit>()
                    }
                }
            }
        }
        val results = runSimulation(58 * MINUTE, takeFincon = true) { initialize() }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Child 1 start at 00:10:00.000000", string()) }
                element { assertEquals("Child 2 start at 00:20:00.000000", string()) }
                element { assertEquals("Child 3 start at 00:30:00.000000", string()) }
                element { assertEquals("Child 4 start at 00:40:00.000000", string()) }
                element { assertEquals("Child 5 start at 00:50:00.000000", string()) }
                element { assertEquals("Child 1 end at 00:55:00.000000", string()) }
                assert(atEnd())
            }
        }

        val nextResults = runSimulation(2 * HOUR, incon = results.fincon) { initialize() }

        with (JsonArray(nextResults.reports)) {
            array {
                element { assertEquals("Child 6 start at 01:00:00.000000", string()) }
                element { assertEquals("Child 2 end at 01:05:00.000000", string()) }
                element { assertEquals("Child 7 start at 01:10:00.000000", string()) }
                element { assertEquals("Child 3 end at 01:15:00.000000", string()) }
                element { assertEquals("Child 8 start at 01:20:00.000000", string()) }
                element { assertEquals("Child 4 end at 01:25:00.000000", string()) }
                element { assertEquals("Child 9 start at 01:30:00.000000", string()) }
                element { assertEquals("Child 5 end at 01:35:00.000000", string()) }
                element { assertEquals("Child 10 start at 01:40:00.000000", string()) }
                element { assertEquals("Child 6 end at 01:45:00.000000", string()) }
                element { assertEquals("Child 11 start at 01:50:00.000000", string()) }
                element { assertEquals("Child 7 end at 01:55:00.000000", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun repeating_children_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val n = allocate(intCounterCell("n", 1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn(Name("Parent")) {
                it.Delay(5 * MINUTE, clock) {
                    Spawn(Name("Repeater"), {
                        it.Delay(10 * MINUTE, clock) {
                            val time = it.read(clock)
                            val nVal = it.read(n)
                            it.report("Iteration $nVal at $time", typeOf<String>())
                            it.emit(n) { it + 1 }
                            Restart<Unit>()
                        }
                    }) {
                        Complete(Unit)
                    }
                }
            }
        }

        val results = runSimulation(HOUR, takeFincon = true) { initialize() }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("Iteration 1 at 00:15:00.000000", string())}
                element { assertEquals("Iteration 2 at 00:25:00.000000", string())}
                element { assertEquals("Iteration 3 at 00:35:00.000000", string())}
                element { assertEquals("Iteration 4 at 00:45:00.000000", string())}
                element { assertEquals("Iteration 5 at 00:55:00.000000", string())}
                assert(atEnd())
            }
        }

        val nextResults = runSimulation(2 * HOUR, incon = results.fincon) { initialize() }

        with (JsonArray(nextResults.reports)) {
            array {
                element { assertEquals("Iteration 6 at 01:05:00.000000", string())}
                element { assertEquals("Iteration 7 at 01:15:00.000000", string())}
                element { assertEquals("Iteration 8 at 01:25:00.000000", string())}
                element { assertEquals("Iteration 9 at 01:35:00.000000", string())}
                element { assertEquals("Iteration 10 at 01:45:00.000000", string())}
                element { assertEquals("Iteration 11 at 01:55:00.000000", string())}
                assert(atEnd())
            }
        }
    }

    @Test
    fun grandchild_tasks_can_be_restored() {
        context (scope: BasicInitScope)
        fun initialize() {
            val clock = allocate(clockCell("clock", ZERO))
            spawn(Name("P")) {
                it.report("P -- 1", typeOf<String>())
                Spawn(Name("C"), {
                    it.report("C -- 1", typeOf<String>())
                    Spawn(Name("GC"), {
                        it.report("GC -- 1", typeOf<String>())
                        it.Delay(90 * MINUTE, clock) {
                            it.report("GC -- 2", typeOf<String>())
                            Complete(Unit)
                        }
                    }) {
                        it.report("C -- 2", typeOf<String>())
                        Complete(Unit)
                    }
                }) {
                    it.report("P -- 2", typeOf<String>())
                    Complete(Unit)
                }
            }
        }

        val results = runSimulation(HOUR, takeFincon = true) { initialize() }

        with (JsonArray(results.reports)) {
            array {
                element { assertEquals("P -- 1", string()) }
                element { assertEquals("P -- 2", string()) }
                element { assertEquals("C -- 1", string()) }
                element { assertEquals("C -- 2", string()) }
                element { assertEquals("GC -- 1", string()) }
                assert(atEnd())
            }
        }

        val nextResults = runSimulation(2 * HOUR, incon = results.fincon) { initialize() }

        with (JsonArray(nextResults.reports)) {
            array {
                element { assertEquals("GC -- 2", string()) }
                assert(atEnd())
            }
        }
    }

    @Test
    fun tasks_running_at_fincon_time_can_be_restored() {
        // I'm concerned there could be a gap if tasks run in parallel with the fincon collector.
        // Stress test this by spawning a bunch of tasks, from init and from the sim itself, that'll run exactly at fincon time.
        context (scope: BasicInitScope)
        fun initialize() {
            val clock = allocate(clockCell("clock", ZERO))
            for (i in 1..100) {
                // Case A - Task delays directly to fincon time
                spawn(Name("A$i")) {
                    it.report("A$i -- 1", typeOf<String>())
                    it.Delay(MINUTE, clock) {
                        it.report("A$i -- 2", typeOf<String>())
                        it.Delay(10 * SECOND, clock) {
                            it.report("A$i -- 3", typeOf<String>())
                            Complete(Unit)
                        }
                    }
                }

                // Case B - Task delays to fincon time after first batch
                //   Fincon task delays to fincon time directly, so if there are effects due to the order that tasks
                //   are added to the queue, this hopes to provoke those effects.
                spawn(Name("B$i")) {
                    it.report("B$i -- 1", typeOf<String>())
                    it.Delay(30 * SECOND, clock) {
                        it.Delay(30 * SECOND, clock) {
                            it.report("B$i -- 2", typeOf<String>())
                            it.Delay(10 * SECOND, clock) {
                                it.report("B$i -- 3", typeOf<String>())
                                Complete(Unit)
                            }
                        }
                    }
                }

                // Case C - Children spawned at fincon time
                spawn(Name("C$i")) {
                    it.report("C$i -- 1", typeOf<String>())
                    it.Delay(MINUTE, clock) {
                        Spawn(Name("C${i}_child"), {
                            it.report("C$i -- 2", typeOf<String>())
                            it.Delay(10 * SECOND, clock) {
                                it.report("C$i -- 3", typeOf<String>())
                                Complete(Unit)
                            }
                        }) {
                            it.Delay(10 * SECOND, clock) {
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }
        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }
        val nextResults = runSimulation(2 * MINUTE, incon = results.fincon) { initialize() }

        // For every task, assert that it was properly saved and restored by seeing the 3rd report from it.
        for (i in 1..100) {
            for (t in listOf("A", "B", "C")) {
                assertContains(nextResults.reports, JsonPrimitive("$t$i -- 3"))
            }
        }
    }
}

private fun assertNearlyEquals(expected: Double, actual: Double) {
    assert(abs(expected - actual) < 1e-5)
}
