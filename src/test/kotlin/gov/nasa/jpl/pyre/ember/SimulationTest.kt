package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.*
import gov.nasa.jpl.pyre.ember.Cell.EffectTrait
import gov.nasa.jpl.pyre.ember.SimpleSimulation.SimulationSetup
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
    private data class SimulationResult(
        val reports: List<JsonValue>,
        val fincon: JsonValue?,
    )

    private fun runSimulation(
        endTime: Duration,
        incon: JsonValue? = null,
        takeFincon: Boolean = false,
        initialize: SimulationState.SimulationInitContext.() -> Unit,
    ): SimulationResult {
        assertDoesNotThrow {
            // Build a simulation that'll write reports to memory
            val reports = mutableListOf<JsonValue>()
            val simulation = SimpleSimulation(SimulationSetup(
                reportHandler = { reports.add(it) },
                inconProvider = incon?.let { JsonConditions.serializer().deserialize(it) },
                initialize = initialize,
            ))
            // Run the simulation to the end
            simulation.runUntil(endTime)
            // Cut a fincon, if requested
            val fincon = if (takeFincon) {
                val finconCollector = JsonConditions()
                simulation.save(finconCollector)
                JsonConditions.serializer().serialize(finconCollector)
            } else null
            // Return all results, and let the simulation itself be garbage collected
            return SimulationResult(reports, fincon)
        }
    }

    private fun intCounterCell(name: String, value: Int) = Cell(
        name,
        value,
        INT_SERIALIZER,
        { x, _ -> x },
        { x, n -> x + n },
        object : EffectTrait<Int> {
            override fun empty() = 0
            override fun concurrent(left: Int, right: Int) = left + right
            override fun sequential(first: Int, second: Int) = first + second
        }
    )

    private fun <T> setEffectTrait() = object : EffectTrait<T?> {
        override fun empty(): T? = null
        override fun concurrent(left: T?, right: T?): T? =
            if (left != null && right != null) {
                throw IllegalArgumentException("Concurrent non-commuting effects")
            } else {
                left ?: right
            }
        override fun sequential(first: T?, second: T?): T? = second ?: first
    }

    private data class LinearDynamics(val value: Double, val rate: Double)
    private val linearDynamicsSerializer : Serializer<LinearDynamics> = Serializer.of(
        InvertibleFunction.Companion.of(
            { d: LinearDynamics -> JsonMap(mapOf("value" to JsonDouble(d.value), "rate" to JsonDouble(d.rate))) },
            {
                with((it as JsonMap).values) {
                    LinearDynamics(
                        (get("value") as JsonDouble).value,
                        (get("rate") as JsonDouble).value
                    )
                }
            })
    )
    private fun linearDynamicsStep(d: LinearDynamics, t: Duration) = LinearDynamics(d.value + d.rate * (t ratioOver SECOND), d.rate)
    private fun linearCell(name: String, value: Double, rate: Double) = Cell(
        name,
        LinearDynamics(value, rate),
        linearDynamicsSerializer,
        ::linearDynamicsStep,
        { d, e -> e ?: d },
        setEffectTrait<LinearDynamics>()
    )

    private fun clockCell(name: String, t: Duration) = Cell(
        name,
        t,
        Duration.serializer(),
        { s, delta -> s + delta },
        { s, e -> e ?: s },
        setEffectTrait<Duration>()
    )

    @Test
    fun empty_simulation_is_valid() {
        runSimulation(HOUR) {}
    }

    @Test
    fun task_can_report_result() {
        val results = runSimulation(HOUR) {
            spawn("report result") {
                Report(JsonString("result")) {
                    Complete(Unit)
                }
            }
        }
        assertEquals(mutableListOf(JsonString("result")), results.reports)
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
            spawn("read cell") {
                Read(x) {
                    Report(JsonString("x = $it")) {
                        Complete(Unit)
                    }
                }
            }
        }
        assertEquals(mutableListOf(JsonString("x = 42")), results.reports)
    }

    @Test
    fun task_can_emit_effect() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 42))
            spawn("emit effect") {
                Emit(x, 13) {
                    Read(x) {
                        Report(JsonString("x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
        }
        assertEquals(mutableListOf(JsonString("x = 55")), results.reports)
    }

    @Test
    fun task_can_delay() {
        runSimulation(HOUR) {
            spawn("delay") {
                Delay(30 * MINUTE) {
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
            val x = allocate(Cell("x", 0, INT_SERIALIZER, { x, t -> x + (t / MINUTE).toInt() }, { x, n -> x + n }, object : EffectTrait<Int> {
                override fun empty() = 0
                override fun concurrent(left: Int, right: Int) = left + right
                override fun sequential(first: Int, second: Int) = first + second
            }))
            spawn("step cell") {
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
            spawn("Task A") {
                Emit(x, 5) {
                    Read(x) {
                        Report(JsonString("A says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Task B") {
                Emit(x, 3) {
                    Read(x) {
                        Report(JsonString("B says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Task C") {
                Read(x) {
                    Report(JsonString("C says: x = $it")) {
                        Read(x) {
                            Report(JsonString("C still says: x = $it")) {
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }
        with (results) {
            assert(reports.size == 4)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonString("A says: x = 15"))
            assertContains(reports, JsonString("B says: x = 13"))
            assertContains(reports, JsonString("C says: x = 10"))
            assertContains(reports, JsonString("C still says: x = 10"))
        }
    }

    @Test
    fun parallel_tasks_join_effects_at_each_delay() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            spawn("Task A") {
                Emit(x, 5) {
                    Read(x) {
                        Report(JsonString("A says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Task B") {
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
            }
        }
        with (results) {
            assert(reports.size == 3)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonString("A says: x = 15"))
            assertContains(reports, JsonString("B first says: x = 10"))
            assertContains(reports, JsonString("B next says: x = 15"))
        }
    }

    @Test
    fun sequential_unobserved_effects_are_joined_using_effect_trait() {
        val results = runSimulation(HOUR) {
            // Note: This is *not* a correct effect trait, but it's simple and lets us observe what's happening better.
            val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : EffectTrait<Int> {
                override fun empty() = 0
                override fun concurrent(left: Int, right: Int) = 0
                override fun sequential(first: Int, second: Int) = first + second + 100
            }))
            spawn("emit effect") {
                Emit(x, 5) {
                    Emit(x, 6) {
                        Read(x) {
                            Report(JsonString("x = $it")) {
                                Complete(Unit)
                            }
                        }
                    }
                }
            }
        }
        // Note: The 221 here comes from the violation of the "empty" part of the effect trait
        // - sequential(empty(), e) != e, because we add 100 every time we join effects.
        // This could change in future versions, but this is fine for the current test.
        assertEquals(mutableListOf(JsonString("x = 221")), results.reports)
    }

    @Test
    fun concurrent_effects_are_joined_using_effect_trait() {
        val results = runSimulation(HOUR) {
            // Note: This is *not* a correct effect trait, but it's simple and lets us observe what's happening better.
            val x = allocate(Cell("x", 10, INT_SERIALIZER, { x, _ -> x }, { x, n -> x + n }, object : EffectTrait<Int> {
                override fun empty() = 0
                override fun concurrent(left: Int, right: Int) = left + right + 100
                override fun sequential(first: Int, second: Int) = first + second
            }))
            spawn("Task A") {
                Emit(x, 5) {
                    Read(x) {
                        Report(JsonString("A says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Task B") {
                Emit(x, 3) {
                    Read(x) {
                        Report(JsonString("B says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Task C") {
                Delay(ZERO) {
                    Read(x) {
                        Report(JsonString("C says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
        }
        with (results) {
            assert(reports.size == 3)
            // Order of reports is largely non-deterministic because these tasks are running in parallel
            assertContains(reports, JsonString("A says: x = 15"))
            assertContains(reports, JsonString("B says: x = 13"))
            // Note: The 218 here comes from the violation of the "empty" part of the effect trait
            // - concurrent(empty(), e) != e, because we add 100 every time we join effects.
            // This could change in future versions, but this is fine for the current test.
            assertContains(reports, JsonString("C says: x = 218"))
        }
    }

    @Test
    fun task_can_await_condition() {
        runSimulation(HOUR) {
            spawn("Await condition") {
                Await({ Condition.SatisfiedAt(ZERO) }) {
                    Complete(Unit)
                }
            }
        }
    }

    @Test
    fun await_trivial_condition_runs_task_in_next_batch() {
        val results = runSimulation(HOUR) {
            val x = allocate(intCounterCell("x", 10))
            spawn("Awaiter") {
                Await({ Condition.SatisfiedAt(ZERO) }) {
                    Read(x) {
                        Report(JsonString("Awaiter says: x = $it")) {
                            Complete(Unit)
                        }
                    }
                }
            }
            spawn("Counter") {
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
            }
        }
        assertEquals(mutableListOf(JsonString("Awaiter says: x = 11")), results.reports)
    }

    @Test
    fun await_never_condition_does_not_run_task() {
        val results = runSimulation(HOUR) {
            spawn("Awaiter") {
                Await({ Condition.UnsatisfiedUntil(null) }) {
                    Report(JsonString("Awaiter ran!")) {
                        Complete(Unit)
                    }
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
            spawn("Awaiter") {
                val condition = Condition.Read(x) { xValue ->
                    Condition.Read(y) { yValue ->
                        if (xValue >= yValue) Condition.SatisfiedAt(ZERO) else Condition.UnsatisfiedUntil(null)
                    }
                }
                Await({ condition }) {
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
            }
            spawn("Counter") {
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
            spawn("Awaiter") {
                val cond = Condition.Read(x) {
                    with (it) {
                        // Example implementation of a "greater than 20" condition for a linear dynamics type.
                        if (value >= 20) {
                            Condition.SatisfiedAt(ZERO)
                        } else if (rate <= 0) {
                            Condition.UnsatisfiedUntil(null)
                        } else {
                            Condition.SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }
                Await({ cond }) {
                    Read(x) {
                        Report(x.serializer.serialize(it)) {
                            Complete(Unit)
                        }
                    }
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
            spawn("Awaiter") {
                val cond = Condition.Read(x) {
                    with (it) {
                        // Example implementation of a "greater than 20" condition for a linear dynamics type.
                        if (value >= 20) {
                            Condition.SatisfiedAt(ZERO)
                        } else if (rate <= 0) {
                            Condition.UnsatisfiedUntil(null)
                        } else {
                            Condition.SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }
                Await({ cond }) {
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
            }
            spawn("Interrupter") {
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
            spawn("Awaiter") {
                val cond = Condition.Read(x) {
                    with (it) {
                        // Example implementation of a "greater than 20" condition for a linear dynamics type.
                        if (value >= 20) {
                            Condition.SatisfiedAt(ZERO)
                        } else if (rate <= 0) {
                            Condition.UnsatisfiedUntil(null)
                        } else {
                            Condition.SatisfiedAt(((20 - value) / rate) ceilTimes SECOND)
                        }
                    }
                }
                Await({ cond }) {
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
            }
            spawn("Interrupter") {
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
            spawn("Awaiter") {
                Await({
                    Condition.Read(x) {
                        if (it.value >= 15) {
                            Condition.SatisfiedAt(ZERO)
                        } else {
                            Condition.UnsatisfiedUntil(2 * SECOND)
                        }
                    }
                }) {
                    Read(x) {
                        Report(x.serializer.serialize(it)) {
                            Complete(Unit)
                        }
                    }
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
            spawn("Awaiter") {
                Await({
                    Condition.Read(x) {
                        if (it.value >= 15) {
                            Condition.SatisfiedAt(ZERO)
                        } else {
                            Condition.UnsatisfiedUntil(MINUTE)
                        }
                    }
                }) {
                    Read(x) {
                        Report(x.serializer.serialize(it)) {
                            Complete(Unit)
                        }
                    }
                }
            }

            spawn("Interrupter") {
                Delay(5 * SECOND) {
                    Emit(x, LinearDynamics(20.0, 0.0)) {
                        Complete(Unit)
                    }
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
        with (results.fincon as JsonMap) {
            within("simulation") {
                assertEquals("00:01:00.000000", string("time", "$"))
            }
        }
    }

    @Test
    fun cells_can_be_saved() {
        fun SimulationState.SimulationInitContext.initialize() {
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
        fun SimulationState.SimulationInitContext.initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))
        }
        val results = runSimulation(MINUTE, takeFincon = true) { initialize() }
        runSimulation(2 * MINUTE, incon=results.fincon) { initialize() }
    }

    @Test
    fun tasks_can_be_saved() {
        fun SimulationState.SimulationInitContext.initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))

            spawn("Complete Immediately") {
                Complete(Unit)
            }
            spawn("Single Batch Task") {
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
            }
            spawn("Multi Batch Task") {
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
        fun SimulationState.SimulationInitContext.initialize() {
            val x = allocate(linearCell("x", 10.0, 1.0))
            val y = allocate(linearCell("y", 10.0, -0.1))

            spawn("Complete Immediately") {
                Complete(Unit)
            }
            spawn("Single Batch Task") {
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
            }
            spawn("Multi Batch Task") {
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

        val nextResults = runSimulation(2.1 roundTimes  MINUTE, incon = results.fincon) { initialize() }

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
            spawn("Repeater") {
                Delay(10 * MINUTE) {
                    Emit(x, 1) {
                        Read(clock) { time ->
                            Read(x) {
                                Report(JsonString("x = $it at $time")) {
                                    Restart<Unit>()
                                }
                            }
                        }
                    }
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
            spawn("Repeater") {
                Delay(10 * MINUTE) {
                    Emit(x, 1) {
                        Read(clock) { time ->
                            Read(x) {
                                Report(JsonString("x = $it at $time")) {
                                    Restart<Unit>()
                                }
                            }
                        }
                    }
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
                        element { assertEquals("delay", string("type")) }
                        assert(atEnd())
                    }
                }
                assertEquals("01:00:00.000000", string("time", "$"))
            }
        }
    }

    @Test
    fun restart_tasks_replay_from_last_restart_when_restoring() {
        // This is a deeply cursed way to leak state out of the simulation.
        // This should never be done in production, but for the sake of testing,
        // it's a useful way to observe the replay, when that replay *should* be invisible.
        var plays = 0
        fun SimulationState.SimulationInitContext.initialize() {
            val x = allocate(intCounterCell("x", 0))
            val clock = allocate(clockCell("clock", ZERO))
            spawn("Repeater") {
                plays++
                Delay(10 * MINUTE) {
                    Emit(x, 1) {
                        Read(clock) { time ->
                            Read(x) {
                                Report(JsonString("x = $it at $time")) {
                                    Restart<Unit>()
                                }
                            }
                        }
                    }
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
            spawn("Parent") {
                Report(JsonString("Parent's report")) {
                    Spawn("Child", {
                        Report(JsonString("Child's report")) {
                            Complete(Unit)
                        }
                    }) {
                        Complete(Unit)
                    }
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

            spawn("Counter") {
                Emit(x, 1) {
                    Delay(ZERO) {
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
                    }
                }
            }

            spawn("P") {
                Read(x) {
                    Report(JsonString("Tick 0: P says: x = $it")) {
                        Spawn("C1", {
                            Read(x) {
                                Report(JsonString("Tick 1: C1 says: x = $it")) {
                                    Delay(ZERO) {
                                        Read(x) {
                                            Report(JsonString("Tick 2: C1 says: x = $it")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        }) {
                            Spawn("C2", {
                                Read(x) {
                                    Report(JsonString("Tick 1: C2 says: x = $it")) {
                                        Delay(ZERO) {
                                            Read(x) {
                                                Report(JsonString("Tick 2: C2 says: x = $it")) {
                                                    Complete(Unit)
                                                }
                                            }
                                        }
                                    }
                                }
                            }) {
                                Delay(ZERO) {
                                    Read(x) {
                                        Report(JsonString("Tick 1: P says: x = $it")) {
                                            Delay(ZERO) {
                                                Read(x) {
                                                    Report(JsonString("Tick 2: P says: x = $it")) {
                                                        Complete(Unit)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        with (results) {
            assertEquals(7, reports.size)
            assertEquals(JsonString("Tick 0: P says: x = 0"), reports[0])
            // Ordering of reports in the same tick is non-deterministic
            assertEquals(
                setOf(
                    JsonString("Tick 1: P says: x = 1"),
                    JsonString("Tick 1: C1 says: x = 1"),
                    JsonString("Tick 1: C2 says: x = 1"),
                ),
                reports.subList(1, 4).toSet())
            assertEquals(
                setOf(
                    JsonString("Tick 2: P says: x = 2"),
                    JsonString("Tick 2: C1 says: x = 2"),
                    JsonString("Tick 2: C2 says: x = 2"),
                ),
                reports.subList(4, 7).toSet())
        }
    }

    @Test
    fun child_tasks_can_be_saved() {
        val results = runSimulation(HOUR, takeFincon = true) {
            spawn("P") {
                Report(JsonString("P -- 1")) {
                    Spawn("C", {
                        Report(JsonString("C -- 1")) {
                            Delay(45 * MINUTE) {
                                // 00:45:00
                                Report(JsonString("C -- 2")) {
                                    Complete(Unit)
                                }
                            }
                        }
                    }) {
                        Delay(30 * MINUTE) {
                            // 00:30:00
                            Report(JsonString("P -- 2")) {
                                Spawn("D", {
                                    Report(JsonString("D -- 1")) {
                                        Delay(45 * MINUTE) {
                                            // 01:15:00
                                            Report(JsonString("D -- 2")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }) {
                                    Delay(HOUR) {
                                        // 01:30:00
                                        Report(JsonString("P -- 3")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
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
                        array {
                            // Only the active children are saved, because only the active children need to be restored.
                            // Completed children can simply not be restored, to the same effect as restoring and completing them.
                            // This keeps the fincon files from growing indefinitely, as the completed children
                            // get dropped after one save/restore cycle.
                            element {
                                array {
                                    element { assertEquals("P", string()) }
                                    element { assertEquals("D", string()) }
                                    assert(atEnd())
                                }
                            }
                            assert(atEnd())
                        }
                    }

                    within("$") {
                        array {
                            element { assertEquals("report", string("type")) }
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("parent", string("branch"))
                            }
                            element { assertEquals("delay", string("type")) }
                            element { assertEquals("report", string("type")) }
                            element {
                                assertEquals("spawn", string("type"))
                                assertEquals("parent", string("branch"))
                            }
                            element { assertEquals("delay", string("type")) }
                            assert(atEnd())
                        }
                    }
                    assertEquals("01:30:00.000000", string("time", "$"))

                    within("D") {
                        within("$") {
                            array {
                                element { assertEquals("report", string("type")) }
                                element {
                                    assertEquals("spawn", string("type"))
                                    assertEquals("parent", string("branch"))
                                }
                                element { assertEquals("delay", string("type")) }
                                element { assertEquals("report", string("type")) }
                                element {
                                    assertEquals("spawn", string("type"))
                                    assertEquals("child", string("branch"))
                                }
                                element { assertEquals("report", string("type")) }
                                element { assertEquals("delay", string("type")) }
                            }
                        }
                        assertEquals("01:15:00.000000", string("time", "$"))
                    }
                }
            }
        }
    }

    @Test
    fun child_tasks_can_be_restored() {
        fun SimulationState.SimulationInitContext.initialize() {
            spawn("P") {
                Report(JsonString("P -- 1")) {
                    Spawn("C", {
                        Report(JsonString("C -- 1")) {
                            Delay(45 * MINUTE) {
                                // 00:45:00
                                Report(JsonString("C -- 2")) {
                                    Complete(Unit)
                                }
                            }
                        }
                    }) {
                        Delay(30 * MINUTE) {
                            // 00:30:00
                            Report(JsonString("P -- 2")) {
                                Spawn("D", {
                                    Report(JsonString("D -- 1")) {
                                        Delay(45 * MINUTE) {
                                            // 01:15:00
                                            Report(JsonString("D -- 2")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }) {
                                    Delay(HOUR) {
                                        // 01:30:00
                                        Report(JsonString("P -- 3")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
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
        fun SimulationState.SimulationInitContext.initialize() {
            val n = allocate(intCounterCell("n", 1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn("Repeater") {
                Delay(10 * MINUTE) {
                    Read(n) { nValue ->
                        Spawn("Child $nValue", {
                            Read(clock) { time ->
                                Report(JsonString("Child $nValue start at $time")) {
                                    Delay(45 * MINUTE) {
                                        Read(clock) { time ->
                                            Report(JsonString("Child $nValue end at $time")) {
                                                Complete(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        }) {
                            Emit(n, 1) {
                                Restart<Unit>()
                            }
                        }
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
        fun SimulationState.SimulationInitContext.initialize() {
            val n = allocate(intCounterCell("n", 1))
            val clock = allocate(clockCell("clock", ZERO))

            spawn("Parent") {
                Delay(5 * MINUTE) {
                    Spawn("Repeater", {
                        Delay(10 * MINUTE) {
                            Read(clock) { time ->
                                Read(n) {
                                    Report(JsonString("Iteration $it at $time")) {
                                        Emit(n, 1) {
                                            Restart<Unit>()
                                        }
                                    }
                                }
                            }
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
        fun SimulationState.SimulationInitContext.initialize() {
            spawn("P") {
                Report(JsonString("P -- 1")) {
                    Spawn("C", {
                        Report(JsonString("C -- 1")) {
                            Spawn("GC", {
                                Report(JsonString("GC -- 1")) {
                                    Delay(90 * MINUTE) {
                                        Report(JsonString("GC -- 2")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }) {
                                Report(JsonString("C -- 2")) {
                                    Complete(Unit)
                                }
                            }
                        }
                    }) {
                        Report(JsonString("P -- 2")) {
                            Complete(Unit)
                        }
                    }
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
        fun SimulationState.SimulationInitContext.initialize() {
            for (i in 1..100) {
                // Case A - Task delays directly to fincon time
                spawn("A$i") {
                    Report(JsonString("A$i -- 1")) {
                        Delay(MINUTE) {
                            Report(JsonString("A$i -- 2")) {
                                Delay(10 * SECOND) {
                                    Report(JsonString("A$i -- 3")) {
                                        Complete(Unit)
                                    }
                                }
                            }
                        }
                    }
                }

                // Case B - Task delays to fincon time after first batch
                //   Fincon task delays to fincon time directly, so if there are effects due to the order that tasks
                //   are added to the queue, this hopes to provoke those effects.
                spawn("B$i") {
                    Report(JsonString("B$i -- 1")) {
                        Delay(30 * SECOND) {
                            Delay(30 * SECOND) {
                                Report(JsonString("B$i -- 2")) {
                                    Delay(10 * SECOND) {
                                        Report(JsonString("B$i -- 3")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Case C - Children spawned at fincon time
                spawn("C$i") {
                    Report(JsonString("C$i -- 1")) {
                        Delay(MINUTE) {
                            Spawn("C$i.child", {
                                Report(JsonString("C$i -- 2")) {
                                    Delay(10 * SECOND) {
                                        Report(JsonString("C$i -- 3")) {
                                            Complete(Unit)
                                        }
                                    }
                                }
                            }) {
                                Delay(10 * SECOND) {
                                    Complete(Unit)
                                }
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
                assertContains(nextResults.reports, JsonString("$t$i -- 3"))
            }
        }
    }
}

// Copied from BasicSerializers, for the sake of testing ember without relying on spark
private val INT_SERIALIZER = Serializer.of(
    InvertibleFunction.Companion.of(
        { i: Int -> JsonInt(i.toLong()) },
        { (it as JsonInt).value.toInt() })
)

private fun assertNearlyEquals(expected: Double, actual: Double) {
    assert(abs(expected - actual) < 1e-5)
}
