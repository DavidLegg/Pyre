package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.call
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.decrement
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
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
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.constant
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.polynomialResource
import gov.nasa.jpl.pyre.incremental.GraphIncrementalPlanSimulation
import gov.nasa.jpl.pyre.incremental.PlanEdits
import gov.nasa.jpl.pyre.incremental.foundation.TestModel.*
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.EPSILON
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.utilities.named
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class GraphIncrementalPlanSimulationTest {
    private val planStart = Instant.parse("2025-01-01T00:00:00Z")
    private val planEnd = Instant.parse("2025-01-02T00:00:00Z")
    private inline fun <reified M> test(
        noinline constructModel: context (InitScope) () -> M
    ) = IncrementalSimulationTester(constructModel, Plan(planStart, planEnd), typeOf<M>())

    private fun test(
        vararg activities: GroundedActivity<TestModel>
    ) = test(activities.toList())

    private fun test(
        activities: List<GroundedActivity<TestModel>>
    ) = IncrementalSimulationTester(::TestModel, Plan(planStart, planEnd, activities), typeOf<TestModel>())

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
                TaskScope.spawn("daemon child", task {
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
    fun `model with concurrent tasks`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon 1", whenever(b) {
                repeat(10) {
                    x.increment()
                    delay(5 * SECOND)
                }
            })
            spawn("daemon 2", whenever(b) {
                repeat(5) {
                    x.increment()
                    delay(10 * SECOND)
                }
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
    fun `initial plan with concurrent activities`() {
        test(
            IncrementStandaloneCounter(1) at 5 * MINUTE,
            IncrementStandaloneCounter(2) at 5 * MINUTE,
            SetStandaloneCounter(0) at 10 * MINUTE,
            IncrementStandaloneCounter(10) at 20 * MINUTE,
            IncrementStandaloneCounter(20) at 20 * MINUTE,
        )
    }

    @Test
    fun `initial plan provoking nontrivial dynamics`() {
        test(
            SetIntegrand(1.0) at 5 * MINUTE,
            SetIntegrand(0.0) at 6 * MINUTE,
            SetIntegrand(1.0) at 10 * MINUTE,
            SetIntegrand(0.0) at 15 * MINUTE,
            SetIntegrand(-1.0) at 20 * MINUTE,
            SetIntegrand(0.0) at 25 * MINUTE,
        )
    }

    @Test
    fun `initial plan with activities that spawn children`() {
        test(
            // SpawnChildren reads the counter, so interleave activities to set the counter to interesting values.
            IncrementStandaloneCounter(1) at 4 * MINUTE,
            SpawnChildren("A") at 5 * MINUTE,
            IncrementStandaloneCounter(2) at 9 * MINUTE,
            SpawnChildren("B") at 10 * MINUTE,
            IncrementStandaloneCounter(10) at 14 * MINUTE,
            SpawnChildren("C") at 15 * MINUTE,
            SpawnChildren("D") at 15 * MINUTE + 3 * SECOND,
        )
    }

    @Test
    fun `initial plan with concurrent read and write at plan start`() {
        // TODO: The incremental simulator gets the correct answer here,
        //   while the single-shot simulator gets it wrong!
        //   The single-shot issues a report with value 1!
        test(
            IncrementStandaloneCounter(1) at 0 * MINUTE,
            ReportStandaloneCounter("A") at 0 * MINUTE,
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
    fun `adding concurrent activities`() {
        test().add(
            IncrementStandaloneCounter(1) at 5 * MINUTE,
            IncrementStandaloneCounter(2) at 5 * MINUTE,
            IncrementStandaloneCounter(-3) at 10 * MINUTE,
            IncrementStandaloneCounter(10) at 20 * MINUTE,
            IncrementStandaloneCounter(20) at 20 * MINUTE,
        )
    }

    @Test
    fun `adding activities concurrent with initial activities`() {
        test(
            IncrementStandaloneCounter(1) at 5 * MINUTE,
            SetStandaloneCounter(0) at 10 * MINUTE,
            IncrementStandaloneCounter(10) at 20 * MINUTE,
        ).add(
            IncrementStandaloneCounter(2) at 5 * MINUTE,
            IncrementStandaloneCounter(20) at 20 * MINUTE,
        )
    }

    @Test
    fun `adding activities to provoke nontrivial dynamics`() {
        test(
            SetIntegrand(1.0) at 5 * MINUTE,
            SetIntegrand(0.0) at 6 * MINUTE,
            SetIntegrand(-1.0) at 20 * MINUTE,
            SetIntegrand(0.0) at 25 * MINUTE,
        ).add(
            SetIntegrand(1.0) at 10 * MINUTE,
            SetIntegrand(0.0) at 15 * MINUTE,
        )
    }

    @Test
    fun `adding activities that spawn children`() {
        test(
            // SpawnChildren reads the counter, so interleave activities to set the counter to interesting values.
            IncrementStandaloneCounter(1) at 4 * MINUTE,
            SpawnChildren("A") at 5 * MINUTE,
            IncrementStandaloneCounter(10) at 14 * MINUTE,
            SpawnChildren("C") at 15 * MINUTE,
            SpawnChildren("D") at 15 * MINUTE + 3 * SECOND,
        ).add(
            IncrementStandaloneCounter(2) at 9 * MINUTE,
            SpawnChildren("B") at 10 * MINUTE,
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
    fun `removing concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5 * MINUTE
        val a2 = IncrementStandaloneCounter(2) at 5 * MINUTE
        val a3 = SetStandaloneCounter(0) at 10 * MINUTE
        val a4 = IncrementStandaloneCounter(10) at 20 * MINUTE
        val a5 = IncrementStandaloneCounter(20) at 20 * MINUTE
        test(a1, a2, a3, a4, a5).remove(a2, a4, a5)
    }

    @Test
    fun `removing activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5 * MINUTE
        val a2 = SetIntegrand(0.0) at 6 * MINUTE
        val a3 = SetIntegrand(1.0) at 10 * MINUTE
        val a4 = SetIntegrand(0.0) at 15 * MINUTE
        val a5 = SetIntegrand(-1.0) at 20 * MINUTE
        val a6 = SetIntegrand(0.0) at 25 * MINUTE
        test(a1, a2, a3, a4, a5, a6).remove(a3, a4)
    }

    @Test
    fun `removing activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4 * MINUTE
        val a2 = SpawnChildren("A") at 5 * MINUTE
        val a3 = IncrementStandaloneCounter(2) at 9 * MINUTE
        val a4 = SpawnChildren("B") at 10 * MINUTE
        val a5 = IncrementStandaloneCounter(10) at 14 * MINUTE
        val a6 = SpawnChildren("C") at 15 * MINUTE
        val a7 = SpawnChildren("D") at 15 * MINUTE + 3 * SECOND
        test(a1, a2, a3, a4, a5, a6, a7).remove(a3, a4)
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
    fun `editing concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5 * MINUTE;
        val a2 = IncrementStandaloneCounter(2) at 5 * MINUTE;
        val a3 = SetStandaloneCounter(0) at 10 * MINUTE;
        val a4 = IncrementStandaloneCounter(10) at 20 * MINUTE;
        val a5 = IncrementStandaloneCounter(20) at 20 * MINUTE;
        test(a1, a2, a3, a4, a5).edit(
            a2 to IncrementStandaloneCounter(10),
            a4 to IncrementStandaloneCounter(-5),
        )
    }

    @Test
    fun `editing activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5 * MINUTE
        val a2 = SetIntegrand(0.0) at 6 * MINUTE
        val a3 = SetIntegrand(1.0) at 10 * MINUTE
        val a4 = SetIntegrand(0.0) at 15 * MINUTE
        val a5 = SetIntegrand(-1.0) at 20 * MINUTE
        val a6 = SetIntegrand(0.0) at 25 * MINUTE
        test(a1, a2, a3, a4, a5, a6).edit(
            a3 to SetIntegrand(-0.1),
            a4 to SetIntegrand(0.1),
        )
    }

    @Test
    fun `editing activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4 * MINUTE
        val a2 = SpawnChildren("A") at 5 * MINUTE
        val a3 = IncrementStandaloneCounter(2) at 9 * MINUTE
        val a4 = SpawnChildren("B") at 10 * MINUTE
        val a5 = IncrementStandaloneCounter(10) at 14 * MINUTE
        val a6 = SpawnChildren("C") at 15 * MINUTE
        val a7 = SpawnChildren("D") at 15 * MINUTE + 3 * SECOND
        test(a1, a2, a3, a4, a5, a6, a7).edit(
            a4 to SpawnChildren("X"),
            a5 to IncrementStandaloneCounter(4)
        )
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

    @Test
    fun `moving concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5 * MINUTE
        val a2 = IncrementStandaloneCounter(2) at 5 * MINUTE
        val a3 = SetStandaloneCounter(0) at 10 * MINUTE
        val a4 = IncrementStandaloneCounter(10) at 18 * MINUTE
        val a5 = IncrementStandaloneCounter(20) at 20 * MINUTE
        test(a1, a2, a3, a4, a5).move(
            a2 to 7 * MINUTE,
            a4 to 20 * MINUTE,
        )
    }

    @Test
    fun `moving activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5 * MINUTE
        val a2 = SetIntegrand(0.0) at 6 * MINUTE
        val a3 = SetIntegrand(1.0) at 10 * MINUTE
        val a4 = SetIntegrand(0.0) at 15 * MINUTE
        val a5 = SetIntegrand(-1.0) at 20 * MINUTE
        val a6 = SetIntegrand(0.0) at 25 * MINUTE
        test(a1, a2, a3, a4, a5, a6).move(
            a3 to 15 * MINUTE,
            a4 to 18 * MINUTE,
        )
    }

    @Test
    fun `moving activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4 * MINUTE
        val a2 = SpawnChildren("A") at 5 * MINUTE
        val a3 = IncrementStandaloneCounter(2) at 9 * MINUTE
        val a4 = SpawnChildren("B") at 10 * MINUTE
        val a5 = IncrementStandaloneCounter(10) at 14 * MINUTE
        val a6 = SpawnChildren("C") at 15 * MINUTE
        val a7 = SpawnChildren("D") at 15 * MINUTE + 3 * SECOND
        test(a1, a2, a3, a4, a5, a6, a7).move(
            a2 to 4 * MINUTE + 10 * SECOND,
            a4 to 15 * MINUTE - 3 * SECOND,
        )
    }

    /**
     * Since incremental sim is complicated, and we have an "oracle" in the form of single-shot simulation,
     * we can randomly generate plans and plan edits and see if incremental sim works on them.
     * This is a good way to flush out edge cases we didn't consider above.
     *
     * Bugs identified by this test should be reproduced by hand in a dedicated test above,
     * ideally with as much superfluous detail stripped out as possible.
     * This prevents regression on unusual edge cases.
     */
    @ParameterizedTest
    @MethodSource("fuzzingSeeds")
    fun `random plan edits conform to fundamental incremental sim guarantee`(seed: Int) {
        val rng = Random(seed)
        val numberOfInitialActivities = Math.pow(10.0, rng.nextDouble(1.0, 3.0)).toInt()
        val roundsOfEdits = 1000
        println("Running $numberOfInitialActivities activities through $roundsOfEdits rounds of edits...")

        // Choose an initial plan
        val activities = mutableListOf<GroundedActivity<TestModel>>()
        repeat(numberOfInitialActivities) {
            activities += GroundedActivity(
                rng.nextInstant(planStart..planEnd),
                rng.nextActivity())
        }
        // Verify the incremental simulator can handle that initial plan
        val tester = test(activities)
        println("Initial simulation complete")

        // For as many rounds of edits as we've decided to do...
        for (roundNumber in 1..roundsOfEdits) {
            println("Running round $roundNumber of edits...")
            // Choose a number of activities to edit, up to the entire plan, with a bias towards small edits.
            val numberOfEdits = if (activities.size <= 1) activities.size else
                Math.exp(rng.nextDouble(0.0, Math.log(activities.size.toDouble()))).toInt()
            val additions = mutableListOf<GroundedActivity<TestModel>>()
            val removals = mutableListOf<GroundedActivity<TestModel>>()
            // Pick random edits to make. If we edit an activity, remove it from activities so it doesn't get edited twice.
            repeat (numberOfEdits) {
                when (rng.nextInt(1..4)) {
                    1 -> {
                        // Add an activity
                        val activity = GroundedActivity(
                            rng.nextInstant(planStart..planEnd),
                            rng.nextActivity())
                        additions += activity
                    }
                    2 -> {
                        // Remove an activity
                        val activity = activities.randomRemove(rng)
                        removals += activity
                    }
                    3 -> {
                        // Move an activity (by up to 10 minutes)
                        val activity = activities.randomRemove(rng)
                        removals += activity
                        val newActivity = activity.copy(time =
                            rng.nextInstant(activity.time - 10.minutes..activity.time + 10.minutes)
                                .coerceIn(planStart..planEnd - 1.microseconds))
                        additions += newActivity
                    }
                    4 -> {
                        // Edit an activity's arguments
                        val activity = activities.randomRemove(rng)
                        removals += activity
                        val newActivity = GroundedActivity(activity.time, activity.activity.randomArgs(rng))
                        additions += newActivity
                    }
                    else -> throw AssertionError("Code path should never run")
                }
            }
            // Now run those randomly-chosen edits, asserting the single-shot and incremental simulators agree
            tester.run(PlanEdits(additions, removals))
        }
    }

    private fun Random.nextInstant(range: ClosedRange<Instant>): Instant =
        range.start + nextLong(0..range.start.until(range.endInclusive, DateTimeUnit.MICROSECOND)).microseconds

    private fun Random.nextActivity(): Activity<TestModel> =
        // Choose randomly among the activity types, then choose random arguments for them
        when (nextInt(1..8)) {
            1 -> SetStandaloneCounter(0)
            2 -> IncrementStandaloneCounter(0)
            3 -> ReportStandaloneCounter("")
            4 -> SetDerivationSource(0)
            5 -> AddJob(0)
            6 -> SetIntegrand(0.0)
            7 -> SpawnChildren("")
            8 -> SpawnChild(SetStandaloneCounter(0))
            else -> throw AssertionError("Code path should never run")
        }.randomArgs(this)

    private fun Activity<TestModel>.randomArgs(rng: Random): Activity<TestModel> = when (this) {
        is SetStandaloneCounter -> copy(number = rng.nextInt(-10..100))
        is IncrementStandaloneCounter -> copy(number = rng.nextInt(-10..10))
        is ReportStandaloneCounter -> copy(id = rng.nextInt(1000..9999).toString())
        is SetDerivationSource -> copy(number = rng.nextInt(-10..10))
        is AddJob -> copy(seed = rng.nextInt(2..30))
        is SetIntegrand -> copy(number = rng.nextDouble(-1.0, 1.0))
        is SpawnChildren -> copy(id = "SC-" + rng.nextInt(1000, 9999))
        is SpawnChild -> copy(child = rng.nextActivity())
        else -> throw AssertionError("Code path should never run")
    }

    private fun <T> MutableList<T>.randomRemove(rng: Random): T =
        removeAt(rng.nextInt(0..<size))

    companion object {
        @JvmStatic
        fun fuzzingSeeds(): IntStream = IntStream.rangeClosed(1, 100)
    }

    // Private test-ism to quickly and legibly write out a plan
    private infix fun <M> Activity<M>.at(time: Duration): GroundedActivity<M> =
        GroundedActivity(planStart + time.toKotlinDuration(), this)

    // TODO: Something like this might actually be useful more generally, applied to an incremental simulator / scheduler.
    //   More generally, incremental sim should be powering a SchedulingSystem-like class with operations like this
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

/**
 * The fundamental incremental simulation guarantee is that incrementally simulating edits
 * yields the same results as single-shot simulating the edited plan.
 *
 * Put another way, the following square must commute:
 * ```
 *        Plan P   ----------- apply edits E ----------->   Plan P'
 *          |                                                 |
 *   simulate (incremental)                         simulate (single-shot)
 *          |                                                 |
 *          V                                                 V
 *       Results R -- incrementally simulate edits E ---> Results R'
 * ```
 *
 * This test class directly checks this guarantee, comparing results from the incremental and single-shot simulators.
 */
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
        // The reported plan is rarely different, so a coarse assertion is fine here.
        assertEquals(baselineSimulation.plan, testSimulation.plan)

        // The results are often different if there's a bug, so fine-grained assertions help debugging
        val baseResults = baselineSimulation.results
        val testResults = testSimulation.results
        // Plan bounds:
        assertEquals(baseResults.startTime, testResults.startTime)
        assertEquals(baseResults.endTime, testResults.endTime)
        // TODO: We have some false test failures during fuzzing due to order of simultaneous messages.
        //   Write a batch-and-match algorithm for messages on stdout, stderr, and activities
        //   so that messages at the same time can be out-of-order without failing the test.
        // Resources:
        for ((resourceName, baselineResource) in baseResults.resources) {
            assert(resourceName in testResults.resources)
            val testResource = testResults.resources.getValue(resourceName)
            assertEquals(baselineResource.metadata, testResource.metadata)
            for ((baselineReport, testReport) in baselineResource.data zip testResource.data) {
                assertEquals(baselineReport, testReport)
            }
            assertEquals(baselineResource.data.size, testResource.data.size)
        }
        assertEquals(baseResults.resources.size, testResults.resources.size)
        // Activities:
        // Treat activity events as unordered.
        // TODO: Refine this assertion to instead only treat concurrent reports as unordered.
        val remainingTestActivities = testResults.activities.toMutableList()
        for (baseActivity in baseResults.activities) {
            assert(remainingTestActivities.remove(baseActivity))
        }
        assert(remainingTestActivities.isEmpty())
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

    // A resource that uses nontrivial dynamics, to demand that cell stepping works properly.
    val integrand: MutableDoubleResource
    val integral: PolynomialResource

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
                // TODO: See if there's a way to eliminate InitScope from the context within this block,
                //   or at least make calling InitScope.spawn after initialization a runtime error.
                TaskScope.spawn(name, task {
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
                        } named { "Update LCF for ($seed, $steps)" }
                    )
                })
            })

            integrand = discreteResource("integrand", 0.0).registered()
            integral = integrand.asPolynomial().clampedIntegral(
                "integral",
                constant(0.0),
                constant(100.0),
                0.0
            ).integral.registered()
        }
    }

    // Use data classes for activities so equivalent activities are equal.
    // This allows us to compare child activities spawned in different simulations.

    data class SetStandaloneCounter(val number: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.standaloneCounter.set(number)
        }
    }

    data class IncrementStandaloneCounter(val number: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.standaloneCounter.increment(number)
        }
    }

    data class ReportStandaloneCounter(val id: String) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            stdout.report("RSC($id): standaloneCounter = ${model.standaloneCounter.getValue()}")
        }
    }

    data class SetDerivationSource(val number: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.derivationSource.set(number)
        }
    }

    data class AddJob(val seed: Int) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.daemonTaskQueue.push(seed)
        }
    }

    data class SetIntegrand(val number: Double) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            model.integrand.set(number)
        }
    }

    data class SpawnChildren(val id: String) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            // Wait for a short delay, so that other activities can interleave with the SpawnChildren call tree.
            delay(5 * SECOND)
            // Read and write a resource to inject some interesting dependencies into the graph
            val counter = model.standaloneCounter.getValue()
            if (counter > 1) {
                stdout.report("SpawnChildren($id) - counter = $counter, taking path 1")
                model.standaloneCounter.decrement()
                spawn(SpawnChildren("$id.$counter"), model)
            } else {
                stdout.report("SpawnChildren($id) - counter = $counter, taking path 2")
            }
        }
    }

    data class SpawnChild(val child: Activity<TestModel>) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            delay(model.standaloneCounter.getValue() * SECOND)
            call(child, model)
        }
    }
}
