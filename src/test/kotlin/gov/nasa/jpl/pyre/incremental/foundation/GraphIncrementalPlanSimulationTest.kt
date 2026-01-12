package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.stdout
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.resources.discrete.ListResourceOperations.isNotEmpty
import gov.nasa.jpl.pyre.general.resources.discrete.ListResourceOperations.pop
import gov.nasa.jpl.pyre.general.resources.discrete.ListResourceOperations.push
import gov.nasa.jpl.pyre.general.resources.discrete.MutableListResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.polynomialResource
import gov.nasa.jpl.pyre.incremental.GraphIncrementalPlanSimulation
import gov.nasa.jpl.pyre.incremental.PlanEdits
import gov.nasa.jpl.pyre.incremental.foundation.TestModel.*
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.EPSILON
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.utilities.named
import org.junit.jupiter.api.Assertions.*
import kotlin.math.PI
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.time.Instant

class GraphIncrementalPlanSimulationTest {
    private val planStart = Instant.parse("2025-01-01T00:00:00Z")
    private val planEnd = Instant.parse("2025-02-01T00:00:00Z")
    private inline fun <reified M> test(
        noinline constructModel: context (InitScope) () -> M
    ) = IncrementalSimulationTester(constructModel, Plan(planStart, planEnd), typeOf<M>())

    private fun test(
        vararg activities: GroundedActivity<TestModel>
    ) = IncrementalSimulationTester(::TestModel, Plan(planStart, planEnd, activities.toList()), typeOf<TestModel>())

    @Test
    fun `empty model`() {
        test { /* no model to build */ }
    }

    @Test
    fun `model with unregistered resources`() {
        test {
            val x = discreteResource("x", 10)
            val p = polynomialResource("p", 0.0, 1e-3)
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }
            Triple(x, p, b)
        }
    }

    @Test
    fun `model with registered resources`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            Triple(x, p, b)
        }
    }

    @Test
    fun `model with simple daemon`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                x.increment(10)
            })
            Triple(x, p, b)
        }
    }

    @Test
    fun `model with daemon that spawns children`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                spawn("daemon child", task {
                    repeat(10) {
                        x.increment()
                        delay(5 * SECOND)
                    }
                })
                // Delay epsilon so the child task can bump x up, avoiding an infinite loop in the parent
                delay(EPSILON)
            })
            Triple(x, p, b)
        }
    }

    @Test
    fun `standard test model with an empty initial plan`() {
        test()
    }

    @Test
    fun `initial plan with single effect activities`() {
        test(
            SetStandaloneCounter(1) at 5 * MINUTE,
            SetStandaloneCounter(2) at 10 * MINUTE,
            SetStandaloneCounter(0) at 20 * MINUTE,
        )
    }

    @Test
    fun `initial plan with multi-effect activities`() {
        test(
            SetDerivationSource(1) at 5 * MINUTE,
            SetDerivationSource(2) at 10 * MINUTE,
            SetDerivationSource(0) at 20 * MINUTE,
        )
    }

    @Test
    fun `initial plan with activities that trigger non-trivial daemons`() {
        test(
            AddJob(10) at 5 * MINUTE,
            AddJob(20) at 10 * MINUTE,
            AddJob(30) at 20 * MINUTE,
        )
    }

    @Test
    fun `adding single effect activities`() {
        test().add(
            SetStandaloneCounter(1) at 5 * MINUTE,
            SetStandaloneCounter(2) at 10 * MINUTE,
            SetStandaloneCounter(0) at 20 * MINUTE,
        )
    }

    @Test
    fun `adding multi effect activities`() {
        test().add(
            SetDerivationSource(1) at 5 * MINUTE,
            SetDerivationSource(2) at 10 * MINUTE,
            SetDerivationSource(0) at 20 * MINUTE,
        )
    }

    @Test
    fun `adding activities that trigger non-trivial daemons`() {
        test().add(
            AddJob(10) at 5 * MINUTE,
            AddJob(20) at 10 * MINUTE,
            AddJob(30) at 20 * MINUTE,
        )
    }

    @Test
    fun `removing single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5 * MINUTE
        val a2 = SetStandaloneCounter(2) at 10 * MINUTE
        val a3 = SetStandaloneCounter(0) at 20 * MINUTE
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `removing multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5 * MINUTE
        val a2 = SetDerivationSource(2) at 10 * MINUTE
        val a3 = SetDerivationSource(0) at 20 * MINUTE
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `removing activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5 * MINUTE
        val a2 = AddJob(20) at 10 * MINUTE
        val a3 = AddJob(30) at 20 * MINUTE
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `editing single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5 * MINUTE
        val a2 = SetStandaloneCounter(2) at 10 * MINUTE
        val a3 = SetStandaloneCounter(0) at 20 * MINUTE
        test(a1, a2, a3).edit(a2 to SetStandaloneCounter(100))
    }

    @Test
    fun `editing multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5 * MINUTE
        val a2 = SetDerivationSource(2) at 10 * MINUTE
        val a3 = SetDerivationSource(0) at 20 * MINUTE
        test(a1, a2, a3).edit(a2 to SetDerivationSource(100))
    }

    @Test
    fun `editing activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5 * MINUTE
        val a2 = AddJob(20) at 10 * MINUTE
        val a3 = AddJob(30) at 20 * MINUTE
        test(a1, a2, a3).edit(a2 to AddJob(15))
    }

    @Test
    fun `moving single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5 * MINUTE
        val a2 = SetStandaloneCounter(2) at 10 * MINUTE
        val a3 = SetStandaloneCounter(0) at 20 * MINUTE
        test(a1, a2, a3).move(a2 to 12 * MINUTE)
    }

    @Test
    fun `moving multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5 * MINUTE
        val a2 = SetDerivationSource(2) at 10 * MINUTE
        val a3 = SetDerivationSource(0) at 20 * MINUTE
        test(a1, a2, a3).move(a2 to 12 * MINUTE)
    }

    @Test
    fun `moving activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5 * MINUTE
        val a2 = AddJob(20) at 10 * MINUTE
        val a3 = AddJob(30) at 20 * MINUTE
        test(a1, a2, a3).move(a2 to 12 * MINUTE)
    }

    // TODO: Find a way to bulk- / fuzz-test incremental edits.

    // Private test-ism to quickly and legibly write out a plan
    private infix fun <M> Activity<M>.at(time: Duration): GroundedActivity<M> =
        GroundedActivity(planStart + time.toKotlinDuration(), this)

    // TODO: Something like this might actually be useful more generally, applied to an incremental simulator.
    // Private test-ism to quickly and legibly make simple edits to a plan
    private fun <M> IncrementalSimulationTester<M>.add(vararg activities: GroundedActivity<M>) =
        run(PlanEdits(activities.toList(), emptyList()))
    private fun <M> IncrementalSimulationTester<M>.remove(vararg activities: GroundedActivity<M>) =
        run(PlanEdits(emptyList(), activities.toList()))
    private fun <M> IncrementalSimulationTester<M>.edit(vararg activities: Pair<GroundedActivity<M>, Activity<M>>) =
        run(
            PlanEdits(
                activities.map { GroundedActivity(it.first.time, it.second) },
                activities.map { it.first },
            )
        )
    private fun <M> IncrementalSimulationTester<M>.move(vararg activities: Pair<GroundedActivity<M>, Duration>) =
        run(
            PlanEdits(
                activities.map { GroundedActivity(planStart + it.second.toKotlinDuration(), it.first.activity) },
                activities.map { it.first },
            )
        )
}

private class IncrementalSimulationTester<M>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    modelClass: KType,
) {
    private val baselineSimulation = NonIncrementalPlanSimulation(constructModel, plan, modelClass)
    private val testSimulation = GraphIncrementalPlanSimulation(constructModel, plan, modelClass)

    init {
        assertSynced()
    }

    fun run(edits: PlanEdits<M>) {
        baselineSimulation.run(edits)
        testSimulation.run(edits)
        assertSynced()
    }

    private fun assertSynced() {
        assertEquals(baselineSimulation.plan, testSimulation.plan)
        assertEquals(baselineSimulation.results, testSimulation.results)
        // TODO: Write a finer-grained assertSynced routine to make debugging easier
    }
}

class TestModel(scope: InitScope) {
    // A single resource that activities can interact with directly, with no downstream effects
    val standaloneCounter: MutableIntResource

    // A pair of resources, so a single "poke" from an activity can have multiple simple downstream effects
    val derivationSource: MutableIntResource
    val derivation: DoubleResource

    // A resource which triggers a more complex daemon task
    // (more complex because every registered resource includes a simple daemon task under the hood already)
    val daemonTaskQueue: MutableListResource<Int>
    val longestCollatzFound: MutableDiscreteResource<Pair<Int, Int>>

    init {
        context(scope) {
            standaloneCounter = discreteResource("standaloneCounter", 0).registered()
            derivationSource = discreteResource("derivationSource", 1).registered()
            derivation = map(derivationSource) { it * PI }.named { "derivation" }.registered()
            daemonTaskQueue = discreteResource("daemonTaskQueue", emptyList())
            longestCollatzFound = discreteResource("longestCollatzFound", 1 to 0)

            // As a simple way to get some nontrivial daemon behavior,
            // submit "jobs" in the form of seed values for the collatz procedure.
            spawn("daemon", whenever(daemonTaskQueue.isNotEmpty()) {
                val seed = daemonTaskQueue.pop()
                val name = "daemon($seed)"
                spawn(name, task {
                    var steps = 0
                    var n = seed
                    while (n > 1) {
                        stdout.report("$name - n = $n")
                        n = if (n % 2 == 0) n / 2 else 3 * n + 1
                        steps++
                        delay(1 * SECOND)
                    }
                    stdout.report("$name - completed in $steps steps")
                    longestCollatzFound.emit(
                        { (lcfSeed, lcfLength): Pair<Int, Int> ->
                            if (steps > lcfLength) seed to steps
                            else lcfSeed to lcfLength
                        }
                        named { "Update LCF for ($seed, $steps)" }
                    )
                })
            })
        }
    }

    class SetStandaloneCounter(val number: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.standaloneCounter.set(number)
        }
    }

    class SetDerivationSource(val number: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.derivationSource.set(number)
        }
    }

    class AddJob(val seed: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.daemonTaskQueue.push(seed)
        }
    }
}
