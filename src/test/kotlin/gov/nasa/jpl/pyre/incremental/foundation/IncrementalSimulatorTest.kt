package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.call
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
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
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.incremental.IncrementalSimulator
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorImpl
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.add
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.edit
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.minus
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.move
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.plus
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.remove
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.unaryPlus
import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator
import gov.nasa.jpl.pyre.incremental.PlanEdits
import gov.nasa.jpl.pyre.incremental.foundation.TestModel.*
import gov.nasa.jpl.pyre.kernel.DependentMap.Companion.valueEquals
import gov.nasa.jpl.pyre.kernel.Durations.EPSILON
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.utilities.named
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class IncrementalSimulatorTest {
    private val day0 = Instant.parse("2025-01-01T00:00:00Z")
    private val day1 = day0 + 1.days
    private val day2 = day1 + 1.days
    private val day3 = day2 + 1.days
    private fun <M : Any> testModel(
        startTime: Instant = day0,
        endTime: Instant = day1,
        incon: Checkpoint<M>? = null,
        constructModel: context (InitScope) () -> M
    ) = IncrementalSimulationTester(constructModel, Plan(startTime, endTime), incon)

    private fun test(
        vararg activities: GroundedActivity<TestModel>,
        startTime: Instant = day0,
        endTime: Instant = day1,
        incon: Checkpoint<TestModel>? = null,
    ) = test(activities.toList(), startTime, endTime, incon)

    private fun test(
        activities: List<GroundedActivity<TestModel>>,
        startTime: Instant = day0,
        endTime: Instant = day1,
        incon: Checkpoint<TestModel>? = null,
    ) = IncrementalSimulationTester(::TestModel, Plan(startTime, endTime, activities), incon)

    @Test
    fun `empty model`() {
        testModel { /* no model to build */ }
    }

    @Test
    fun `model with unregistered resources`() {
        testModel {
            val x = discreteResource("x", 10)
            val p = polynomialResource("p", 0.0, 1e-3)
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }
            Triple(x, p, b)
        }
    }

    @Test
    fun `model with registered resources`() {
        testModel {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            Triple(x, p, b)
        }
    }

    @Test
    fun `model with simple daemon`() {
        testModel {
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
        testModel {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                TaskScope.spawn("daemon child", task {
                    repeat(10) {
                        x.increment()
                        delay(5.seconds)
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
        testModel {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon 1", whenever(b) {
                repeat(10) {
                    x.increment()
                    delay(5.seconds)
                }
            })
            spawn("daemon 2", whenever(b) {
                repeat(5) {
                    x.increment()
                    delay(10.seconds)
                }
            })
            Triple(x, p, b)
        }
    }

    private fun <M : Any> testModelSaveAndRestore(constructModel: context (InitScope) () -> M) {
        val simulator1 = testModel(startTime = day0, endTime = day2, constructModel = constructModel)
        // Try saving a checkpoint in the middle of the time range
        val checkpoint1 = simulator1.save(day1)
        // Use that to restore a simulation extending further than the original sim
        testModel(startTime = day1, endTime = day3, incon = checkpoint1, constructModel = constructModel)
        // Try saving a checkpoint at the end of the time range
        val checkpoint2 = simulator1.save(day2)
        // Use that to restore a simulation as well
        testModel(startTime = day2, endTime = day3, incon = checkpoint2, constructModel = constructModel)
    }

    @Test
    fun `save and restore empty model`() {
        testModelSaveAndRestore { /* no model to build */ }
    }

    @Test
    fun `save and restore model with unregistered resources`() {
        testModelSaveAndRestore {
            val x = discreteResource("x", 10)
            val p = polynomialResource("p", 0.0, 1e-3)
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }
            Triple(x, p, b)
        }
    }

    @Test
    fun `save and restore model with registered resources`() {
        testModelSaveAndRestore {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            Triple(x, p, b)
        }
    }

    @Test
    fun `save and restore model with simple daemon`() {
        testModelSaveAndRestore {
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
    fun `save and restore model with daemon that spawns children`() {
        testModelSaveAndRestore {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                TaskScope.spawn("daemon child", task {
                    repeat(10) {
                        x.increment()
                        delay(5.seconds)
                    }
                })
                // Delay epsilon so the child task can bump x up, avoiding an infinite loop in the parent
                delay(EPSILON)
            })
            Triple(x, p, b)
        }
    }

    @Test
    fun `save and restore model with concurrent tasks`() {
        testModelSaveAndRestore {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon 1", whenever(b) {
                repeat(10) {
                    x.increment()
                    delay(5.seconds)
                }
            })
            spawn("daemon 2", whenever(b) {
                repeat(5) {
                    x.increment()
                    delay(10.seconds)
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
            SetStandaloneCounter(1) at 5.minutes,
            SetStandaloneCounter(2) at 10.minutes,
            SetStandaloneCounter(0) at 20.minutes,
        )
    }

    @Test
    fun `initial plan with multi-effect activities`() {
        test(
            SetDerivationSource(1) at 5.minutes,
            SetDerivationSource(2) at 10.minutes,
            SetDerivationSource(0) at 20.minutes,
        )
    }

    @Test
    fun `initial plan with activities that trigger non-trivial daemons`() {
        test(
            AddJob(10) at 5.minutes,
            AddJob(20) at 10.minutes,
            AddJob(30) at 20.minutes,
        )
    }

    @Test
    fun `initial plan with concurrent activities`() {
        test(
            IncrementStandaloneCounter(1) at 5.minutes,
            IncrementStandaloneCounter(2) at 5.minutes,
            SetStandaloneCounter(0) at 10.minutes,
            IncrementStandaloneCounter(10) at 20.minutes,
            IncrementStandaloneCounter(20) at 20.minutes,
        )
    }

    @Test
    fun `initial plan provoking nontrivial dynamics`() {
        test(
            SetIntegrand(1.0) at 5.minutes,
            SetIntegrand(0.0) at 6.minutes,
            SetIntegrand(1.0) at 10.minutes,
            SetIntegrand(0.0) at 15.minutes,
            SetIntegrand(-1.0) at 20.minutes,
            SetIntegrand(0.0) at 25.minutes,
        )
    }

    @Test
    fun `initial plan with activities that spawn children`() {
        test(
            // SpawnChildren reads the counter, so interleave activities to set the counter to interesting values.
            IncrementStandaloneCounter(1) at 4.minutes,
            SpawnChildren("A") at 5.minutes,
            IncrementStandaloneCounter(2) at 9.minutes,
            SpawnChildren("B") at 10.minutes,
            IncrementStandaloneCounter(10) at 14.minutes,
            SpawnChildren("C") at 15.minutes,
            SpawnChildren("D") at 15.minutes + 3.seconds,
        )
    }

    @Test
    fun `initial plan with concurrent read and write at plan start`() {
        test(
            IncrementStandaloneCounter(1) at 0.minutes,
            ReportStandaloneCounter("A") at 0.minutes,
        )
    }

    @Test
    fun `adding single effect activities`() {
        test().add(
            SetStandaloneCounter(1) at 5.minutes,
            SetStandaloneCounter(2) at 10.minutes,
            SetStandaloneCounter(0) at 20.minutes,
        )
    }

    @Test
    fun `adding multi effect activities`() {
        test().add(
            SetDerivationSource(1) at 5.minutes,
            SetDerivationSource(2) at 10.minutes,
            SetDerivationSource(0) at 20.minutes,
        )
    }

    @Test
    fun `adding activities that trigger non-trivial daemons`() {
        test().add(
            AddJob(10) at 5.minutes,
            AddJob(20) at 10.minutes,
            AddJob(30) at 20.minutes,
        )
    }

    @Test
    fun `adding concurrent activities`() {
        test().add(
            IncrementStandaloneCounter(1) at 5.minutes,
            IncrementStandaloneCounter(2) at 5.minutes,
            IncrementStandaloneCounter(-3) at 10.minutes,
            IncrementStandaloneCounter(10) at 20.minutes,
            IncrementStandaloneCounter(20) at 20.minutes,
        )
    }

    @Test
    fun `adding activities concurrent with initial activities`() {
        test(
            IncrementStandaloneCounter(1) at 5.minutes,
            SetStandaloneCounter(0) at 10.minutes,
            IncrementStandaloneCounter(10) at 20.minutes,
        ).add(
            IncrementStandaloneCounter(2) at 5.minutes,
            IncrementStandaloneCounter(20) at 20.minutes,
        )
    }

    @Test
    fun `adding activities to provoke nontrivial dynamics`() {
        test(
            SetIntegrand(1.0) at 5.minutes,
            SetIntegrand(0.0) at 6.minutes,
            SetIntegrand(-1.0) at 20.minutes,
            SetIntegrand(0.0) at 25.minutes,
        ).add(
            SetIntegrand(1.0) at 10.minutes,
            SetIntegrand(0.0) at 15.minutes,
        )
    }

    @Test
    fun `adding activities that spawn children`() {
        test(
            // SpawnChildren reads the counter, so interleave activities to set the counter to interesting values.
            IncrementStandaloneCounter(1) at 4.minutes,
            SpawnChildren("A") at 5.minutes,
            IncrementStandaloneCounter(10) at 14.minutes,
            SpawnChildren("C") at 15.minutes,
            SpawnChildren("D") at 15.minutes + 3.seconds,
        ).add(
            IncrementStandaloneCounter(2) at 9.minutes,
            SpawnChildren("B") at 10.minutes,
        )
    }

    @Test
    fun `removing single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5.minutes
        val a2 = SetStandaloneCounter(2) at 10.minutes
        val a3 = SetStandaloneCounter(0) at 20.minutes
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `removing multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5.minutes
        val a2 = SetDerivationSource(2) at 10.minutes
        val a3 = SetDerivationSource(0) at 20.minutes
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `removing activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5.minutes
        val a2 = AddJob(20) at 10.minutes
        val a3 = AddJob(30) at 20.minutes
        test(a1, a2, a3).remove(a2)
    }

    @Test
    fun `removing concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5.minutes
        val a2 = IncrementStandaloneCounter(2) at 5.minutes
        val a3 = SetStandaloneCounter(0) at 10.minutes
        val a4 = IncrementStandaloneCounter(10) at 20.minutes
        val a5 = IncrementStandaloneCounter(20) at 20.minutes
        test(a1, a2, a3, a4, a5).remove(a2, a4, a5)
    }

    @Test
    fun `removing activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5.minutes
        val a2 = SetIntegrand(0.0) at 6.minutes
        val a3 = SetIntegrand(1.0) at 10.minutes
        val a4 = SetIntegrand(0.0) at 15.minutes
        val a5 = SetIntegrand(-1.0) at 20.minutes
        val a6 = SetIntegrand(0.0) at 25.minutes
        test(a1, a2, a3, a4, a5, a6).remove(a3, a4)
    }

    @Test
    fun `removing activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4.minutes
        val a2 = SpawnChildren("A") at 5.minutes
        val a3 = IncrementStandaloneCounter(2) at 9.minutes
        val a4 = SpawnChildren("B") at 10.minutes
        val a5 = IncrementStandaloneCounter(10) at 14.minutes
        val a6 = SpawnChildren("C") at 15.minutes
        val a7 = SpawnChildren("D") at 15.minutes + 3.seconds
        test(a1, a2, a3, a4, a5, a6, a7).remove(a3, a4)
    }

    @Test
    fun `editing single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5.minutes
        val a2 = SetStandaloneCounter(2) at 10.minutes
        val a3 = SetStandaloneCounter(0) at 20.minutes
        test(a1, a2, a3).edit(a2 to SetStandaloneCounter(100))
    }

    @Test
    fun `editing multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5.minutes
        val a2 = SetDerivationSource(2) at 10.minutes
        val a3 = SetDerivationSource(0) at 20.minutes
        test(a1, a2, a3).edit(a2 to SetDerivationSource(100))
    }

    @Test
    fun `editing activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5.minutes
        val a2 = AddJob(20) at 10.minutes
        val a3 = AddJob(30) at 20.minutes
        test(a1, a2, a3).edit(a2 to AddJob(15))
    }

    @Test
    fun `editing concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5.minutes
        val a2 = IncrementStandaloneCounter(2) at 5.minutes
        val a3 = SetStandaloneCounter(0) at 10.minutes
        val a4 = IncrementStandaloneCounter(10) at 20.minutes
        val a5 = IncrementStandaloneCounter(20) at 20.minutes
        test(a1, a2, a3, a4, a5).edit(
            a2 to IncrementStandaloneCounter(10),
            a4 to IncrementStandaloneCounter(-5),
        )
    }

    @Test
    fun `editing activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5.minutes
        val a2 = SetIntegrand(0.0) at 6.minutes
        val a3 = SetIntegrand(1.0) at 10.minutes
        val a4 = SetIntegrand(0.0) at 15.minutes
        val a5 = SetIntegrand(-1.0) at 20.minutes
        val a6 = SetIntegrand(0.0) at 25.minutes
        test(a1, a2, a3, a4, a5, a6).edit(
            a3 to SetIntegrand(-0.1),
            a4 to SetIntegrand(0.1),
        )
    }

    @Test
    fun `editing activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4.minutes
        val a2 = SpawnChildren("A") at 5.minutes
        val a3 = IncrementStandaloneCounter(2) at 9.minutes
        val a4 = SpawnChildren("B") at 10.minutes
        val a5 = IncrementStandaloneCounter(10) at 14.minutes
        val a6 = SpawnChildren("C") at 15.minutes
        val a7 = SpawnChildren("D") at 15.minutes + 3.seconds
        test(a1, a2, a3, a4, a5, a6, a7).edit(
            a4 to SpawnChildren("X"),
            a5 to IncrementStandaloneCounter(4)
        )
    }

    @Test
    fun `moving single effect activities`() {
        val a1 = SetStandaloneCounter(1) at 5.minutes
        val a2 = SetStandaloneCounter(2) at 10.minutes
        val a3 = SetStandaloneCounter(0) at 20.minutes
        test(a1, a2, a3).move(a2 to day0 + 12.minutes)
    }

    @Test
    fun `moving multi effect activities`() {
        val a1 = SetDerivationSource(1) at 5.minutes
        val a2 = SetDerivationSource(2) at 10.minutes
        val a3 = SetDerivationSource(0) at 20.minutes
        test(a1, a2, a3).move(a2 to day0 + 12.minutes)
    }

    @Test
    fun `moving activities that trigger non-trivial daemons`() {
        val a1 = AddJob(10) at 5.minutes
        val a2 = AddJob(20) at 10.minutes
        val a3 = AddJob(30) at 20.minutes
        test(a1, a2, a3).move(a2 to day0 + 12.minutes)
    }

    @Test
    fun `moving concurrent activities`() {
        val a1 = IncrementStandaloneCounter(1) at 5.minutes
        val a2 = IncrementStandaloneCounter(2) at 5.minutes
        val a3 = SetStandaloneCounter(0) at 10.minutes
        val a4 = IncrementStandaloneCounter(10) at 18.minutes
        val a5 = IncrementStandaloneCounter(20) at 20.minutes
        test(a1, a2, a3, a4, a5).move(
            a2 to day0 + 7.minutes,
            a4 to day0 + 20.minutes,
        )
    }

    @Test
    fun `moving activities which provoke nontrivial dynamics`() {
        val a1 = SetIntegrand(1.0) at 5.minutes
        val a2 = SetIntegrand(0.0) at 6.minutes
        val a3 = SetIntegrand(1.0) at 10.minutes
        val a4 = SetIntegrand(0.0) at 15.minutes
        val a5 = SetIntegrand(-1.0) at 20.minutes
        val a6 = SetIntegrand(0.0) at 25.minutes
        test(a1, a2, a3, a4, a5, a6).move(
            a3 to day0 + 15.minutes,
            a4 to day0 + 18.minutes,
        )
    }

    @Test
    fun `moving activities that spawn children`() {
        val a1 = IncrementStandaloneCounter(1) at 4.minutes
        val a2 = SpawnChildren("A") at 5.minutes
        val a3 = IncrementStandaloneCounter(2) at 9.minutes
        val a4 = SpawnChildren("B") at 10.minutes
        val a5 = IncrementStandaloneCounter(10) at 14.minutes
        val a6 = SpawnChildren("C") at 15.minutes
        val a7 = SpawnChildren("D") at 15.minutes + 3.seconds
        test(a1, a2, a3, a4, a5, a6, a7)
            .move(
            a2 to day0 + 4.minutes + 10.seconds,
            a4 to day0 + 15.minutes - 3.seconds,
        )
    }

    @Test
    fun `saving and restoring single effect activities`() {
        val simulator1 = test(
            SetStandaloneCounter(1) at 5.minutes,
            SetStandaloneCounter(2) at 12.hours,
            SetStandaloneCounter(0) at 24.hours,
            SetStandaloneCounter(10) at 25.hours,
            SetStandaloneCounter(0) at 36.hours,
            startTime = day0,
            endTime = day2,
        )
        val checkpoint1 = simulator1.save(day1)
        test(startTime = day1, endTime = day3, incon = checkpoint1)
        val checkpoint2 = simulator1.save(day2)
        test(startTime = day2, endTime = day3, incon = checkpoint2)
    }

    @Test
    fun `saving and restoring activities that spawn children`() {
        val simulator1 = test(
            // SpawnChildren reads the counter, so interleave activities to set the counter to interesting values.
            IncrementStandaloneCounter(1) at 4.hours,
            SpawnChildren("A") at 5.hours,
            IncrementStandaloneCounter(2) at 23.hours,
            SpawnChildren("B") at 1.days - 6.seconds,
            IncrementStandaloneCounter(10) at 47.hours,
            SpawnChildren("C") at 2.days - 12.seconds,
            SpawnChildren("D") at 2.days - 9.seconds,
            startTime = day0,
            endTime = day2,
        )
        val checkpoint1 = simulator1.save(day1)
        test(startTime = day1, endTime = day3, incon = checkpoint1)
        val checkpoint2 = simulator1.save(day2)
        test(startTime = day2, endTime = day3, incon = checkpoint2)
    }

    /** Reproduce the observed NPE-from-continuation by calling a fuzz test with the observed failing seed. */
    @Test
    fun `repro by seed`() {
        `random plan edits conform to fundamental incremental sim guarantee`(1)
    }

    /**
     * Reproduce the observed NPE-from-continuation by taking each action the fuzz test took.
     * This test was produced with the help of repro_incremental_sim_fuzz_test.py
     */
    @Test
    fun `repro directly`() {
        var incon: Checkpoint<TestModel>
        var inconTime: Instant
        println("Building initial plan")
        var tester = test(
            GroundedActivity(Instant.parse("2025-01-01T00:01:07.691928Z"), Name("483838683711"), SpawnChild(child=AddJob(seed=18))),
            GroundedActivity(Instant.parse("2025-01-01T11:56:27.940281Z"), Name("349568391770"), ReportStandaloneCounter(id="2528")),
            GroundedActivity(Instant.parse("2025-01-01T17:47:50.377810Z"), Name("325113240617"), AddJob(seed=10)),
            GroundedActivity(Instant.parse("2025-01-01T18:22:21.404800Z"), Name("256221124709"), SpawnChild(child=AddJob(seed=7))),
            GroundedActivity(Instant.parse("2025-01-01T12:55:17.666966Z"), Name("386191371839"), SpawnChild(child=SpawnChildren(id="SC-2010"))),
            GroundedActivity(Instant.parse("2025-01-01T06:14:17.630151Z"), Name("119816780415"), SpawnChild(child=SpawnChild(child=ReportStandaloneCounter(id="1509")))),
            GroundedActivity(Instant.parse("2025-01-01T14:35:19.140819Z"), Name("295304103185"), SpawnChildren(id="SC-5669")),
            GroundedActivity(Instant.parse("2025-01-01T16:49:36.294154Z"), Name("352428644765"), AddJob(seed=26)),
            GroundedActivity(Instant.parse("2025-01-01T03:24:52.038696Z"), Name("151805940528"), SetIntegrand(number=0.3003489828134367)),
            GroundedActivity(Instant.parse("2025-01-01T17:25:50.550098Z"), Name("290184593803"), IncrementStandaloneCounter(number=-7)),
            GroundedActivity(Instant.parse("2025-01-01T17:32:01.723516Z"), Name("160634307677"), SetStandaloneCounter(number=82)),
            GroundedActivity(Instant.parse("2025-01-01T14:33:43.856263Z"), Name("519742823152"), SpawnChildren(id="SC-5567")),
            GroundedActivity(Instant.parse("2025-01-01T15:37:33.148581Z"), Name("132745857368"), SetStandaloneCounter(number=44)),
            GroundedActivity(Instant.parse("2025-01-01T16:09:19.708097Z"), Name("418018393883"), SpawnChild(child=IncrementStandaloneCounter(number=0))),
            GroundedActivity(Instant.parse("2025-01-01T15:20:43.997321Z"), Name("784316188272"), SpawnChild(child=SetStandaloneCounter(number=0))),
            GroundedActivity(Instant.parse("2025-01-01T11:26:23.975439Z"), Name("784455777462"), SetStandaloneCounter(number=74)),
            GroundedActivity(Instant.parse("2025-01-01T16:36:33.638122Z"), Name("844547961313"), ReportStandaloneCounter(id="6642")),
            GroundedActivity(Instant.parse("2025-01-01T00:17:47.198723Z"), Name("442643708271"), IncrementStandaloneCounter(number=-8)),
            GroundedActivity(Instant.parse("2025-01-01T16:51:25.005002Z"), Name("600679260745"), SpawnChild(child=ReportStandaloneCounter(id="9281")))
        )
        println("Running round 1 of edits")
        tester.run(
            add(GroundedActivity(Instant.parse("2025-01-01T07:06:12.722870Z"), Name("110112859732"), SetStandaloneCounter(number=68)))
                    + move(GroundedActivity(Instant.parse("2025-01-01T16:36:33.638122Z"), Name("844547961313"), ReportStandaloneCounter(id="6642")) to Instant.parse("2025-01-01T16:44:07.956141Z"))
        )
        println("Running round 2 of edits")
        tester.run(
            add(GroundedActivity(Instant.parse("2025-01-01T03:54:55.334418Z"), Name("687465476078"), SetDerivationSource(number=-1)))
                    + add(GroundedActivity(Instant.parse("2025-01-01T12:15:35.390755Z"), Name("985293002262"), SpawnChildren(id="SC-5577")))
        )
        println("Running round 3 of edits")
        println("Doing a save/restore cycle")
        inconTime = Instant.parse("2025-01-01T14:03:24.102133Z")
        incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(
            GroundedActivity(Instant.parse("2025-01-02T00:56:21.146768Z"), Name("122387740366"), SpawnChild(child=ReportStandaloneCounter(id="8021"))),
            GroundedActivity(Instant.parse("2025-01-01T16:36:33.112779Z"), Name("656410423044"), SetIntegrand(number=-0.005422981314735953)),
            GroundedActivity(Instant.parse("2025-01-02T12:07:56.711566Z"), Name("116308768193"), IncrementStandaloneCounter(number=5)),
            GroundedActivity(Instant.parse("2025-01-01T23:34:21.435726Z"), Name("854383325353"), ReportStandaloneCounter(id="9803")),
            GroundedActivity(Instant.parse("2025-01-01T15:41:57.517017Z"), Name("367145481076"), SetIntegrand(number=-0.1315897050805397)),
            GroundedActivity(Instant.parse("2025-01-01T19:24:01.502171Z"), Name("681130853720"), IncrementStandaloneCounter(number=4)),
            GroundedActivity(Instant.parse("2025-01-02T08:30:09.616369Z"), Name("546552665989"), SetIntegrand(number=0.7846684320909298)),
            GroundedActivity(Instant.parse("2025-01-02T12:20:26.505776Z"), Name("882595588967"), IncrementStandaloneCounter(number=-9)),
            GroundedActivity(Instant.parse("2025-01-02T13:42:38.295769Z"), Name("680316375282"), SetStandaloneCounter(number=18)),
            GroundedActivity(Instant.parse("2025-01-01T22:03:43.765440Z"), Name("480582196310"), IncrementStandaloneCounter(number=-3)),
            GroundedActivity(Instant.parse("2025-01-01T14:04:07.413226Z"), Name("959883837961"), AddJob(seed=26)),
            GroundedActivity(Instant.parse("2025-01-02T01:51:08.439063Z"), Name("189728688931"), SpawnChild(child=SpawnChildren(id="SC-7085"))),
            GroundedActivity(Instant.parse("2025-01-02T01:26:09.659204Z"), Name("684012532568"), SpawnChild(child=ReportStandaloneCounter(id="9790"))),
            GroundedActivity(Instant.parse("2025-01-01T14:31:02.001784Z"), Name("181136543870"), SetStandaloneCounter(number=32)),
            GroundedActivity(Instant.parse("2025-01-02T00:56:15.768292Z"), Name("210552885869"), SetStandaloneCounter(number=8)),
            GroundedActivity(Instant.parse("2025-01-02T05:08:58.235039Z"), Name("488336183103"), IncrementStandaloneCounter(number=7)),
            GroundedActivity(Instant.parse("2025-01-02T11:37:59.691216Z"), Name("865203221181"), AddJob(seed=3)),
            GroundedActivity(Instant.parse("2025-01-01T18:25:15.600199Z"), Name("622722202468"), ReportStandaloneCounter(id="7144")),
            GroundedActivity(Instant.parse("2025-01-02T04:16:41.443806Z"), Name("998273946340"), SpawnChild(child=IncrementStandaloneCounter(number=-2))),
            GroundedActivity(Instant.parse("2025-01-01T18:37:04.659401Z"), Name("265098842607"), ReportStandaloneCounter(id="3830")),
            GroundedActivity(Instant.parse("2025-01-02T10:40:37.894889Z"), Name("943996879570"), SpawnChildren(id="SC-5543")),
            GroundedActivity(Instant.parse("2025-01-01T17:13:58.006706Z"), Name("968937714079"), SetIntegrand(number=-0.07147104319954622)),
            GroundedActivity(Instant.parse("2025-01-01T22:41:22.825002Z"), Name("677711445927"), AddJob(seed=16)),
            GroundedActivity(Instant.parse("2025-01-02T02:18:15.306623Z"), Name("341198935927"), SpawnChildren(id="SC-1840")),
            GroundedActivity(Instant.parse("2025-01-02T02:54:44.902225Z"), Name("439935907428"), SetDerivationSource(number=0)),
            GroundedActivity(Instant.parse("2025-01-02T09:39:47.030566Z"), Name("436998965330"), SetIntegrand(number=-0.3726086441772669)),
            GroundedActivity(Instant.parse("2025-01-01T22:44:13.748951Z"), Name("534703930220"), ReportStandaloneCounter(id="6546")),
            GroundedActivity(Instant.parse("2025-01-01T23:42:20.495141Z"), Name("932607094647"), AddJob(seed=25)),
            GroundedActivity(Instant.parse("2025-01-02T10:05:38.150683Z"), Name("666790583887"), SetDerivationSource(number=8)),
            GroundedActivity(Instant.parse("2025-01-01T20:45:46.716011Z"), Name("147057969579"), IncrementStandaloneCounter(number=2)),
            GroundedActivity(Instant.parse("2025-01-01T19:18:56.531388Z"), Name("107406119393"), SpawnChildren(id="SC-8935")),
            GroundedActivity(Instant.parse("2025-01-02T11:45:12.968529Z"), Name("861253450155"), SetStandaloneCounter(number=22)),
            GroundedActivity(Instant.parse("2025-01-02T13:53:43.729041Z"), Name("129091353286"), SpawnChildren(id="SC-8494")),
            GroundedActivity(Instant.parse("2025-01-02T05:29:49.292856Z"), Name("592592861091"), SetIntegrand(number=-0.9643550474445832)),
            GroundedActivity(Instant.parse("2025-01-02T12:22:36.601295Z"), Name("612519236186"), SetStandaloneCounter(number=30)),
            GroundedActivity(Instant.parse("2025-01-01T17:59:03.339889Z"), Name("580420789010"), SpawnChildren(id="SC-5238")),
            GroundedActivity(Instant.parse("2025-01-02T02:41:48.845907Z"), Name("911024632947"), SpawnChild(child=SetStandaloneCounter(number=22))),
            GroundedActivity(Instant.parse("2025-01-01T23:17:02.626969Z"), Name("134602793307"), SetIntegrand(number=-0.7438690970847854)),
            GroundedActivity(Instant.parse("2025-01-01T21:47:49.845015Z"), Name("497706077150"), ReportStandaloneCounter(id="7726")),
            GroundedActivity(Instant.parse("2025-01-02T07:46:26.081105Z"), Name("387345215400"), SpawnChild(child=SpawnChildren(id="SC-5039"))),
            GroundedActivity(Instant.parse("2025-01-02T08:01:08.709364Z"), Name("314433926652"), ReportStandaloneCounter(id="7112")),
            GroundedActivity(Instant.parse("2025-01-02T12:26:45.662973Z"), Name("972023483898"), IncrementStandaloneCounter(number=0)),
            GroundedActivity(Instant.parse("2025-01-01T16:15:50.692131Z"), Name("495684308847"), SpawnChildren(id="SC-8837")),
            GroundedActivity(Instant.parse("2025-01-02T06:47:55.804413Z"), Name("171357431931"), SpawnChildren(id="SC-3164")),
            GroundedActivity(Instant.parse("2025-01-01T17:05:35.490912Z"), Name("198225535728"), ReportStandaloneCounter(id="6494")),
            GroundedActivity(Instant.parse("2025-01-02T11:46:48.491029Z"), Name("202036406628"), SetDerivationSource(number=-5)),
            GroundedActivity(Instant.parse("2025-01-01T15:12:14.407716Z"), Name("949078935600"), IncrementStandaloneCounter(number=4)),
            GroundedActivity(Instant.parse("2025-01-02T08:41:40.078547Z"), Name("928774283102"), SetDerivationSource(number=-3)),
            GroundedActivity(Instant.parse("2025-01-02T13:29:14.732373Z"), Name("651137161823"), ReportStandaloneCounter(id="3317")),
            GroundedActivity(Instant.parse("2025-01-02T08:42:40.644658Z"), Name("227748433408"), SpawnChildren(id="SC-7451")),
            GroundedActivity(Instant.parse("2025-01-01T15:29:42.555916Z"), Name("415238998136"), ReportStandaloneCounter(id="5551")),
            GroundedActivity(Instant.parse("2025-01-02T02:04:07.067893Z"), Name("809147861308"), SetDerivationSource(number=-2)),
            GroundedActivity(Instant.parse("2025-01-02T03:25:17.556899Z"), Name("624207202651"), SpawnChild(child=SetStandaloneCounter(number=-6))),
            GroundedActivity(Instant.parse("2025-01-01T19:07:12.430603Z"), Name("494082806349"), AddJob(seed=27)),
            GroundedActivity(Instant.parse("2025-01-01T14:38:58.378861Z"), Name("487068682942"), SetIntegrand(number=0.41563792321014703)),
            GroundedActivity(Instant.parse("2025-01-01T15:31:55.357057Z"), Name("643876860936"), SpawnChildren(id="SC-7504")),
            GroundedActivity(Instant.parse("2025-01-01T14:25:53.308841Z"), Name("597730179485"), SetIntegrand(number=-0.28776053395534684)),
            GroundedActivity(Instant.parse("2025-01-01T19:32:20.085239Z"), Name("561115011909"), IncrementStandaloneCounter(number=-10)),
            GroundedActivity(Instant.parse("2025-01-01T22:25:39.313440Z"), Name("446589105918"), ReportStandaloneCounter(id="1481")),
            GroundedActivity(Instant.parse("2025-01-01T21:34:01.597275Z"), Name("923505151174"), SpawnChild(child=AddJob(seed=4))),
            GroundedActivity(Instant.parse("2025-01-02T06:01:28.419695Z"), Name("670572579461"), SpawnChild(child=ReportStandaloneCounter(id="8901"))),
            GroundedActivity(Instant.parse("2025-01-02T12:26:09.578804Z"), Name("966521764981"), SetDerivationSource(number=-10)),
            GroundedActivity(Instant.parse("2025-01-01T17:30:05.396217Z"), Name("636702302039"), SetIntegrand(number=-0.46732398262812014))
        ))
        println("Save/restore cycle complete")
        tester.run(
            remove(GroundedActivity(Instant.parse("2025-01-01T15:31:55.357057Z"), Name("643876860936"), SpawnChildren(id="SC-7504")))
                    + move(GroundedActivity(Instant.parse("2025-01-02T12:26:09.578804Z"), Name("966521764981"), SetDerivationSource(number=-10)) to Instant.parse("2025-01-02T12:20:29.737532Z"))
                    + add(GroundedActivity(Instant.parse("2025-01-02T05:41:04.587522Z"), Name("870621409001"), SetIntegrand(number=-0.63023398009347)))
                    + move(GroundedActivity(Instant.parse("2025-01-02T04:16:41.443806Z"), Name("998273946340"), SpawnChild(child=IncrementStandaloneCounter(number=-2))) to Instant.parse("2025-01-02T04:23:12.201832Z"))
                    + remove(GroundedActivity(Instant.parse("2025-01-01T23:34:21.435726Z"), Name("854383325353"), ReportStandaloneCounter(id="9803")))
                    + remove(GroundedActivity(Instant.parse("2025-01-01T23:42:20.495141Z"), Name("932607094647"), AddJob(seed=25)))
                    + add(GroundedActivity(Instant.parse("2025-01-02T06:41:03.146383Z"), Name("370126189594"), SetIntegrand(number=-0.1910991369829993)))
                    + add(GroundedActivity(Instant.parse("2025-01-01T18:16:13.606290Z"), Name("143989872362"), SetDerivationSource(number=9)))
                    + move(GroundedActivity(Instant.parse("2025-01-02T11:45:12.968529Z"), Name("861253450155"), SetStandaloneCounter(number=22)) to Instant.parse("2025-01-02T11:54:11.533276Z"))
            + add(GroundedActivity(Instant.parse("2025-01-01T21:12:11.136256Z"), Name("737497575449"), SetDerivationSource(number=7)))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T08:42:40.644658Z"), Name("227748433408"), SpawnChildren(id="SC-7451")))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T06:47:55.804413Z"), Name("171357431931"), SpawnChildren(id="SC-3164")))
                    + add(GroundedActivity(Instant.parse("2025-01-01T22:29:00.344174Z"), Name("196186781863"), ReportStandaloneCounter(id="7360")))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T01:26:09.659204Z"), Name("684012532568"), SpawnChild(child=ReportStandaloneCounter(id="9790"))))
                    + remove(GroundedActivity(Instant.parse("2025-01-01T22:25:39.313440Z"), Name("446589105918"), ReportStandaloneCounter(id="1481")))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T08:30:09.616369Z"), Name("546552665989"), SetIntegrand(number=0.7846684320909298)))
                    + move(GroundedActivity(Instant.parse("2025-01-02T10:40:37.894889Z"), Name("943996879570"), SpawnChildren(id="SC-5543")) to Instant.parse("2025-01-02T10:43:03.953751Z"))
                    + add(GroundedActivity(Instant.parse("2025-01-02T10:58:36.293496Z"), Name("403822234439"), SetStandaloneCounter(number=22)))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T02:54:44.902225Z"), Name("439935907428"), SetDerivationSource(number=0)))
                    + add(GroundedActivity(Instant.parse("2025-01-02T11:17:55.616765Z"), Name("836056941328"), SetIntegrand(number=-0.5396062570775171)))
                    + move(GroundedActivity(Instant.parse("2025-01-02T13:29:14.732373Z"), Name("651137161823"), ReportStandaloneCounter(id="3317")) to Instant.parse("2025-01-02T13:35:29.944506Z"))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T08:41:40.078547Z"), Name("928774283102"), SetDerivationSource(number=-3)))
                    + remove(GroundedActivity(Instant.parse("2025-01-02T02:18:15.306623Z"), Name("341198935927"), SpawnChildren(id="SC-1840")))
                    + add(GroundedActivity(Instant.parse("2025-01-01T18:07:57.668070Z"), Name("834467251300"), SetDerivationSource(number=9)))
                    + add(GroundedActivity(Instant.parse("2025-01-02T12:46:56.775076Z"), Name("662451633014"), SpawnChildren(id="SC-8902")))
        )
        println("Repro complete!")
    }

    /**
     * The "cut down" version of [`repro directly`], where I removed as much as possible while still reproducing the error.
     */
    @Test
    fun `repro directly -- cut down`() {
        var tester = test()
        val inconTime = Instant.parse("2025-01-01T14:03:24.102133Z")
        val incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(
            GroundedActivity(Instant.parse("2025-01-02T10:05:38.150683Z"), Name("666790583887"), SetDerivationSource(number=8)),
        ))
        tester.run(
            add(GroundedActivity(Instant.parse("2025-01-01T21:12:11.136256Z"), Name("737497575449"), SetDerivationSource(number=7)))
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
        var indentLevel = 0
        fun println(msg: String) {
            repeat(indentLevel) { print("  ") }
            print(msg + "\n")
        }
        fun startBlock(msg: String? = null) {
            msg?.also(::println)
            indentLevel++
        }
        fun endBlock(msg: String? = null) {
            msg?.also(::println)
            indentLevel--
        }

        // TODO - bias the randomization slightly towards plan bounds and concurrency
        val rng = Random(seed)
        val numberOfInitialActivities = 10.0.pow(rng.nextDouble(1.0, 3.0)).toInt()
        val roundsOfEdits = 100
        println("Running $numberOfInitialActivities activities through $roundsOfEdits rounds of edits...")

        val usedActivityIds = mutableSetOf<Long>()
        fun Random.nextActivityId(): Name {
            var activityId: Long
            do {
                activityId = nextLong(100_000_000_000, 1_000_000_000_000)
            } while (!usedActivityIds.add(activityId))
            return Name(activityId.toString())
        }

        var startTime = day0
        var endTime = day1

        // Choose an initial plan
        // We'll maintain this list of activities, separate from simulator.plan, since we don't yet trust the simulator.
        startBlock("Building initial plan")
        val activities = mutableListOf<GroundedActivity<TestModel>>()
        repeat(numberOfInitialActivities) {
            activities += GroundedActivity(
                rng.nextInstant(startTime..endTime),
                rng.nextActivityId(),
                rng.nextActivity()).also { println("Add $it") }
        }
        endBlock()
        // Verify the incremental simulator can handle that initial plan
        var tester = test(activities)
        println("Initial simulation complete")

        // For as many rounds of edits as we've decided to do...
        for (roundNumber in 1..roundsOfEdits) {
            startBlock("Running round $roundNumber of edits...")
            // In some rounds, do a save/restore cycle
            if (rng.chance(0.05)) {
                startBlock("Doing a save/restore cycle")
                // Pick a new random start time, and slide the end time with it
                startTime = rng.nextInstant(startTime..endTime)
                endTime = startTime + 1.days
                println("Checkpoint time = $startTime")
                // Activities that were saved through the checkpoint can't then be changed incrementally,
                // so choose a new set of activities to work with instead.
                // TODO: Think through whether this must be the case... If an activity comes from an incon, can it be incrementally edited?
                val newActivities = mutableListOf<GroundedActivity<TestModel>>()
                repeat (10.0.pow(rng.nextDouble(1.0, 3.0)).toInt()) {
                    newActivities += GroundedActivity(
                        rng.nextInstant(startTime..endTime),
                        rng.nextActivityId(),
                        rng.nextActivity()).also { println("Add $it") }
                }
                // Then build a new incremental tester with those time bounds, saving and restoring from a checkpoint
                tester = test(
                    activities = newActivities,
                    startTime = startTime,
                    endTime = endTime,
                    incon = tester.save(startTime),
                )
                activities.clear()
                activities += newActivities
                endBlock("Save/restore cycle complete")
            }
            // Choose a number of activities to edit, up to the entire plan, with a bias towards small edits.
            val numberOfEdits = if (activities.size <= 1) activities.size else
                exp(rng.nextDouble(0.0, ln(activities.size.toDouble()))).toInt()
            var edits = PlanEdits<TestModel>()
            startBlock("Choosing $numberOfEdits random edits")
            // Pick random edits to make. If we edit an activity, remove it from activities so it doesn't get edited twice.
            repeat (numberOfEdits) {
                when (rng.nextInt(1..4)) {
                    1 -> {
                        // Add an activity
                        edits += GroundedActivity(
                            rng.nextInstant(startTime..endTime),
                            rng.nextActivityId(),
                            rng.nextActivity()
                        ).also { println("Add $it") }
                    }
                    2 -> {
                        // Remove an activity
                        edits -= activities.randomRemove(rng).also { println("Remove $it") }
                    }
                    3 -> {
                        // Move an activity (by up to 10 minutes)
                        val activity = activities.randomRemove(rng)
                        val time = rng.nextInstant(activity.time - 10.minutes..activity.time + 10.minutes)
                            .coerceIn(startTime..endTime - 1.microseconds)
                        println("Move $activity to $time")
                        edits += move(activity to time)
                    }
                    4 -> {
                        // Edit an activity's arguments
                        val activity = activities.randomRemove(rng)
                        val newActivity = activity.activity.randomArgs(rng)
                        println("Edit $activity to $newActivity")
                        edits += edit(activity to newActivity)
                    }
                    else -> throw AssertionError("Code path should never run")
                }
            }
            endBlock()
            println("Running edits")
            // Now run those randomly-chosen edits, asserting the single-shot and incremental simulators agree
            tester.run(edits)
            // Also apply the edits to our list of activities, to know what we can edit next round
            activities -= edits.removals.toSet()
            activities += edits.additions
            endBlock()
        }
    }

    private fun Random.nextInstant(range: ClosedRange<Instant>): Instant =
        range.start + nextLong(0..range.start.until(range.endInclusive, DateTimeUnit.MICROSECOND)).microseconds

    private fun Random.chance(p: Double = 0.5): Boolean = nextDouble() < p

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
        fun fuzzingSeeds(): IntStream = IntStream.rangeClosed(1, 1000)
    }

    // Private test-ism to quickly and legibly write out a plan
    private var nextActivityId = 1
    private infix fun <M> Activity<M>.at(time: Duration): GroundedActivity<M> = GroundedActivity(
        day0 + time,
        Name(nextActivityId++.toString()) / this::class.simpleName!!,
        this)
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
private class IncrementalSimulationTester<M : Any>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    incon: Checkpoint<M>? = null,
) : IncrementalSimulator<M> {
    private val baselineSimulation = NonIncrementalSimulator(constructModel, plan, incon)
    private val testSimulation = IncrementalSimulatorImpl(constructModel, plan, incon)

    init {
        assertSynced()
    }

    override val plan: Plan<M> get() = testSimulation.plan
    override val results: SimulationResults get() = testSimulation.results

    override fun run(edits: PlanEdits<M>) {
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

    override fun save(time: Instant): Checkpoint<M> {
        // Save checkpoints from both, and assert they're equivalent
        val baselineCheckpoint = baselineSimulation.save(time)
        val testCheckpoint = testSimulation.save(time)

        // Since a checkpoint is likely to have issues, do a fine-grained comparison to aid debugging
        assertEquals(baselineCheckpoint.time, testCheckpoint.time)
        assert(baselineCheckpoint.cells valueEquals testCheckpoint.cells)

        // Daemons are stored as a list, but order isn't relevant
        val remainingTestDaemons = testCheckpoint.daemons.toMutableList()
        for (baselineDaemon in baselineCheckpoint.daemons) {
            // Find the relevant daemon entry by name and remove it
            val n = remainingTestDaemons.indexOfFirst { it.name == baselineDaemon.name }
            require(n >= 0) { "No daemon named ${baselineDaemon.name}" }
            val testDaemon = remainingTestDaemons.removeAt(n)
            // Assert that each field is equal separately to aid debugging
            assertEquals(baselineDaemon.name, testDaemon.name)
            assertEquals(baselineDaemon.root, testDaemon.root)
            assertEquals(baselineDaemon.time, testDaemon.time)
            assertEquals(baselineDaemon.history, testDaemon.history)
        }
        assert(remainingTestDaemons.isEmpty())

        // Activities are stored as a list, but order isn't relevant
        val remainingTestActivities = testCheckpoint.activities.toMutableList()
        for (baselineActivity in baselineCheckpoint.activities) {
            val n = remainingTestActivities.indexOfFirst { it.name == baselineActivity.name }
            require(n >= 0) { "No activity named ${baselineActivity.name}" }
            val testActivity = remainingTestActivities.removeAt(n)
            // Assert that each field is equal separately to aid debugging
            assertEquals(baselineActivity.name, testActivity.name)
            assertEquals(baselineActivity.time, testActivity.time)
            assertEquals(baselineActivity.activity, testActivity.activity)
            assertEquals(baselineActivity.history, testActivity.history)
        }
        assert(remainingTestActivities.isEmpty())

        // Having asserted that both checkpoints are equivalent, it doesn't matter which we return.
        return baselineCheckpoint
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
                        delay(1.seconds)
                    }
                    stdout.report("$name - completed in $steps steps")
                    longestCollatzFound.emit(
                        { (lcfSeed, lcfLength): Pair<Int, Int> ->
                            if (steps > lcfLength) seed to steps
                            else lcfSeed to lcfLength
                        }.named { "Update LCF for ($seed, $steps)" }
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
            delay(5.seconds)
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
            delay(model.standaloneCounter.getValue().seconds)
            call(child, model)
        }
    }
}
