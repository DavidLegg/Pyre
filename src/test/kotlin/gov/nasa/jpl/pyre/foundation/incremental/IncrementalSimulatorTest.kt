package gov.nasa.jpl.pyre.foundation.incremental

import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.BooleanExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.BooleanResourceExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.DoubleExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.DoubleResourceExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.DurationExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.IntExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.IntResourceExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.PolynomialResourceExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.ExpressionBlock.TimerResourceExpression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.EffectBlock.*
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.call
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
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
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.add
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.edit
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.minus
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.move
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.plus
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.remove
import gov.nasa.jpl.pyre.foundation.incremental.TestModel.*
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.or
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.toggle
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.timer.MutableTimerResource
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResource
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.pause
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.reset
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.restart
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.resume
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.timer
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.minus
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.plus
import gov.nasa.jpl.pyre.kernel.DependentMap.Companion.valueEquals
import gov.nasa.jpl.pyre.kernel.Durations.EPSILON
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.kernel.tasks.PureTask
import gov.nasa.jpl.pyre.kernel.tasks.TaskHistory.Companion.valueEquals
import gov.nasa.jpl.pyre.utilities.named
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.IntStream
import kotlin.collections.iterator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
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
    ) = test(::TestModel, activities, startTime, endTime, incon)

    private fun <M : Any> test(
        constructModel: (InitScope) -> M,
        activities: List<GroundedActivity<M>>,
        startTime: Instant = day0,
        endTime: Instant = day1,
        incon: Checkpoint<M>? = null,
    ) = IncrementalSimulationTester(constructModel, Plan(startTime, endTime, activities.toList()), incon)

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

    /**
     * Edge case caught by fuzz testing:
     * A daemon for reporting a resource must be re-run, starting from the root node built by a save/restore cycle.
     * This demands that the restored root node be capable of replaying, which puts additional constraints on its construction.
     */
    @Test
    fun `re-running a daemon from its restored continuation`() {
        val inconTime = Instant.parse("2025-01-01T14:00:00.000000Z")
        val incon = test().save(inconTime)
        test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(
            GroundedActivity(Instant.parse("2025-01-02T10:00:00.000000Z"), Name("Incon SDS"), SetDerivationSource(number=8)),
        ))
            .add(GroundedActivity(Instant.parse("2025-01-01T20:00:00.000000Z"), Name("Added SDS"), SetDerivationSource(number=7)))
    }

    @Test
    fun `activity preserved through two save-restore cycles`() {
        var tester = test(SetStandaloneCounter(0) at 16.hours)

        val inconTime1 = day0 + 14.hours
        val incon1 = tester.save(inconTime1)
        tester = test(startTime = inconTime1, endTime = inconTime1 + 1.days, incon = incon1)

        val inconTime2 = day0 + 15.hours
        val incon2 = tester.save(inconTime2)
        test(startTime = inconTime2, endTime = inconTime2 + 1.days, incon = incon2)
    }

    @Test
    fun `edit at start`() {
        val a1 = IncrementStandaloneCounter(6) at 0.hours
        test(a1).edit(a1 to IncrementStandaloneCounter(9))
    }

    @Test
    fun `saving activity grandchildren through multiple save-restore cycles`() {
        var tester = test()
        val inconTime = Instant.parse("2025-01-01T11:28:05.334363Z")
        val incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(
            GroundedActivity(Instant.parse("2025-01-01T18:30:00.000000Z"), SetStandaloneCounter(10)),
            GroundedActivity(Instant.parse("2025-01-01T18:33:45.000000Z"), SpawnChildren("SC")),
        ))
        val inconTime1 = Instant.parse("2025-01-01T18:34:00.000000Z")
        val incon1 = tester.save(inconTime1)
        tester = test(startTime = inconTime1, endTime = inconTime1 + 1.days, incon = incon1)
        val inconTime2 = Instant.parse("2025-01-01T18:34:01.000000Z")
        val incon2 = tester.save(inconTime2)
        test(startTime = inconTime2, endTime = inconTime2 + 1.days, incon = incon2)
    }

    @Test
    fun `saving a daemon child which completes when restored`() {
        // Start a job (a child daemon task)
        var tester = test(GroundedActivity(Instant.parse("2025-01-01T18:00:00.000000Z"), AddJob(seed = 20)))
        // Save a checkpoint while that child daemon is running
        val inconTime = Instant.parse("2025-01-01T18:00:01.000000Z")
        val incon = tester.save(inconTime)
        // When restored, the child daemon's task node has no parent. This is the crux of the edge case.
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
        // Now save again, this time after the child daemon has finished.
        val inconTime1 = Instant.parse("2025-01-01T19:00:00.000000Z")
        val incon1 = tester.save(inconTime1)
        test(startTime = inconTime1, endTime = inconTime1 + 1.days, incon = incon1)
        // Since the child daemon finished, it gets either a "completed" checkpoint or no checkpoint.
        // Since it's a child daemon, it should get no checkpoint.
        // Since its first task node has no parent, incorrect code might think it's a top-level daemon
        // and give it a completed checkpoint by mistake.
    }

    @Test
    fun `re-running conditions with delicate (imprecise) dynamics`() {
        val a1 = GroundedActivity(Instant.parse("2025-01-01T21:00:00.000000Z"), Name("A1"), SetIntegrand(0.008284136598839975))
        var tester = test(a1)
        val inconTime = Instant.parse("2025-01-01T23:00:00.000000Z")
        val incon = tester.save(inconTime)
        val a2 = GroundedActivity(Instant.parse("2025-01-01T23:33:29.313981Z"), Name("A2"), SetIntegrand(0.8136596792231392))
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon, activities = listOf(a2))
        tester.move(a2 to Instant.parse("2025-01-01T23:34:34.000000Z"))
    }

    @Test
    fun `saving daemons which were completed in incon`() {
        // Crash the "Compute integral" daemon by faulting the integrand cell
        var tester = test(
            GroundedActivity( Instant.parse("2025-01-01T00:00:00Z"), Name("417394956067"), SetIntegrand(number = 0.6165559727893921)),
            GroundedActivity( Instant.parse("2025-01-01T00:00:00Z"), Name("818675801097"), SetIntegrand(number = -0.0037934538032007303)),
        )
        // Save and reload the completed daemon
        var inconTime = Instant.parse("2025-01-01T02:00:00.000000Z")
        var incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
        // Then save and reload the completed daemon again, to ensure it stays completed
        inconTime = Instant.parse("2025-01-01T03:00:00.000000Z")
        incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
    }

    @Test
    fun `duplicate error messages`() {
        // These activities, combined, produce simultaneous identical error messages on stderr.
        test(
            GroundedActivity(Instant.parse("2025-01-01T01:00:00.000000Z"), Name("299125461284"), SetStandaloneCounter(number = 2)),
            GroundedActivity(Instant.parse("2025-01-01T01:00:00.000000Z"), Name("450785608609"), IncrementStandaloneCounter(number = 7)),
            GroundedActivity(Instant.parse("2025-01-01T12:00:00.000000Z"), Name("805465453894"), SpawnChild(child = AddJob(seed = 18))),
            GroundedActivity(Instant.parse("2025-01-01T12:00:00.000000Z"), Name("428860376343"), ReportStandaloneCounter(id = "2168")),
        )
    }

    @Test
    fun `faulting derivation source with concurrent child activities`() {
        test(SpawnChildPair(child1 = SetDerivationSource(number = 0), child2 = SetDerivationSource(number = 1)) at 1.hours)
    }

    @Test
    fun `saving a daemon that has stopped after restoring from an incon`() {
        var tester = test(
            GroundedActivity(Instant.parse("2025-01-01T16:00:00Z"), SpawnChildPair(
                child1=SetIntegrand(number=0.1),
                child2=SetIntegrand(number=0.2)))
        )
        var inconTime = Instant.parse("2025-01-01T12:00:00Z")
        var incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
        inconTime = Instant.parse("2025-01-01T20:00:00Z")
        incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
    }

    @Test
    fun `inserting a write node just before a merge node`() {
        // This odd pattern of spawns forces a merge node in a relatively late batch at this time.
        val tester = test(
            GroundedActivity(Instant.parse("2025-01-01T11:00:00.000000Z"),
                SpawnChildPair(
                    child1 = AddJob(seed = 2),
                    child2 = SpawnChildPair(
                        child1 = AddJob(seed = 8),
                        child2 = AddJob(seed = 4)
                    )
                )
            ),
        )
        // This add then forces an update which generates a new write node close to that merge node,
        // in a way that invalidated some bookkeeping in an earlier version of the code.
        tester.add(
            GroundedActivity(Instant.parse("2025-01-01T10:00:00.000000Z"), AddJob(seed = 2)),
        )
    }

    @Test
    fun `add a write immediately before a concurrent write`() {
        // This somewhat complicated pattern of concurrent activities winds up forcing us to add a write node
        // one task step before a batch of concurrent writes.
        // This is an unusual edge case in the handling of write nodes, and careless handling of it led to a malformed DAG.
        val a1 = GroundedActivity(Instant.parse("2025-01-01T00:42:10.000000Z"), AddJob(seed = 1))
        val a2 = GroundedActivity(Instant.parse("2025-01-01T00:45:22.000000Z"), SpawnChildPair(child1 = SpawnChildPair(child1 = AddJob(seed = 1), child2 = AddJob(seed = 1)), child2 = AddJob(seed = 2)))
        val tester = test(a1, a2)
        tester.move(a1 to a1.time + 1.seconds)
    }

    @Test
    fun `saving sibling activities with the same name`() {
        // This is actually an edge case less for the simulator and more for the test harness.
        // It's permitted for sibling activities to have the same name, which doesn't tend to trip up the simulator,
        // but did trip up the test harness at one point.
        val a1 = GroundedActivity(Instant.parse("2025-01-01T12:00:00Z"), Name("A1"), SpawnChildPair(
            child1 = IncrementStandaloneCounter(number = 5),
            child2 = SetStandaloneCounter(number = 6))
        )
        var tester = test(activities = listOf(a1))

        tester.run(
            GroundedActivity(Instant.parse("2025-01-01T01:00:00Z"), Name("A2"),
                SpawnChildPair(
                    child1 = SpawnChildren(id = "A2.a"),
                    child2 = SpawnChildren(id = "A2.b")
                )
            )
            - a1
        )

        val inconTime = Instant.parse("2025-01-01T01:00:01Z")
        val incon = tester.save(inconTime)
        tester = test(startTime = inconTime, endTime = inconTime + 1.days, incon = incon)
    }

    @Test
    fun `effects that commute but don't associate`() {
        // This test case provoked a bug in how I automatically resolved concurrent effects.
        // I used to simply say that the merge of two concurrent effect e and f is a new effect,
        // which runs e(f(value)) and f(e(value)), and demands those agree.
        // This checks that the effects commute, but not that they associate!
        // That's only 2^n of the n! orderings.
        // To be fully correct, we need to demand that all concurrent effects commute and associate.
        // To deal with this, the AutoEffects type was introduced, which explicitly runs through all permutations of effects.
        // The exact way that this combination of effects provokes the single-shot and incremental simulators
        // to associate effects differently is still unknown. It's some minor detail of map ordering I don't have the patience to track down.
        // In fact, this test will probably degrade quickly and stop testing the bug it originally indicated.

        // Consider the three effects on standalone counter in A3.
        // If associated as (+1 | -1) | set 5, all four commutations compute the same answer.
        // If associated as +1 | (-1 | set 5), orderings compute different answers, merge fails.
        // Hence, this is a set of effects which commute but do not associate.
        val a0 = GroundedActivity(Instant.parse("2025-01-01T12:00:00Z"), Name("A0"), SpawnChildren(id = "SC-5721"))
        val a1 = GroundedActivity(Instant.parse("2025-01-01T06:00:00Z"), Name("A1"), SetStandaloneCounter(number = 4))
        val a2 = GroundedActivity(Instant.parse("2025-01-01T08:00:00Z"), Name("A2"), SpawnChildren(id = "SC-9505"))
        val a3 = GroundedActivity(Instant.parse("2025-01-01T19:00:00Z"), Name("A3"), SpawnChildPair(
            child1 = SpawnChildPair(
                child1 = IncrementStandaloneCounter(number = 1),
                child2 = ReportStandaloneCounter("RSC-1"),
            ),
            child2 = SpawnChildPair(
                child1 = IncrementStandaloneCounter(number = -1),
                child2 = SetStandaloneCounter(number = -5),
            )
        ))

        val tester = test(a0, a1)
        tester.add(a2)
        tester.add(a3)
    }

    @Tag("long-test")
    @ParameterizedTest
    @MethodSource("fuzzingSeeds")
    fun `random plan edits conform to fundamental incremental sim guarantee -- model 1`(seed: Int) {
        `random plan edits conform to fundamental incremental sim guarantee`(seed) { rng ->
            object : RandomActivityGenerator<TestModel> {
                override fun constructModel(initScope: InitScope): TestModel = TestModel(initScope)

                override fun nextActivity(): Activity<TestModel> = randomizeArgs(rng.choose(
                    { SetStandaloneCounter(0) },
                    { IncrementStandaloneCounter(0) },
                    { ReportStandaloneCounter("") },
                    { SetDerivationSource(0) },
                    { AddJob(0) },
                    { SetIntegrand(0.0) },
                    { SpawnChildren("") },
                    { SpawnChild(SetStandaloneCounter(0)) },
                    { SpawnChildPair(SetStandaloneCounter(0), SetStandaloneCounter(0)) },
                ))

                override fun randomizeArgs(activity: Activity<TestModel>): Activity<TestModel> = when (activity) {
                    is SetStandaloneCounter -> activity.copy(number = rng.nextInt(-10..100))
                    is IncrementStandaloneCounter -> activity.copy(number = rng.nextInt(-10..10))
                    is ReportStandaloneCounter -> activity.copy(id = rng.nextInt(1000..9999).toString())
                    is SetDerivationSource -> activity.copy(number = rng.nextInt(-10..10))
                    is AddJob -> activity.copy(seed = rng.nextInt(2..30))
                    is SetIntegrand -> activity.copy(number = rng.nextDouble(-1.0, 1.0))
                    is SpawnChildren -> activity.copy(id = "SC-" + rng.nextInt(1000, 9999))
                    is SpawnChild -> activity.copy(child = nextActivity())
                    is SpawnChildPair -> activity.copy(child1 = nextActivity(), child2 = nextActivity())
                    else -> throw AssertionError("Code path should never run")
                }
            }
        }
    }

    /**
     * Identical to [`random plan edits conform to fundamental incremental sim guarantee -- model 1`],
     * but seeded with a smaller set of seeds.
     * This provides some coverage on every build, with the option to run the more-intensive version only on request.
     */
    @ParameterizedTest
    @MethodSource("fuzzingSeeds -- lightweight")
    fun `random plan edits conform to fundamental incremental sim guarantee -- model 1 lightweight`(seed: Int) {
        `random plan edits conform to fundamental incremental sim guarantee -- model 1`(seed)
    }

    @Tag("long-test")
    @ParameterizedTest
    @MethodSource("fuzzingSeeds")
    fun `random plan edits conform to fundamental incremental sim guarantee -- model 2`(seed: Int) {
        `random plan edits conform to fundamental incremental sim guarantee`(seed) { rng ->
            object : RandomActivityGenerator<BlockTestModel> {
                override fun constructModel(initScope: InitScope): BlockTestModel = BlockTestModel(initScope)

                override fun nextActivity(): Activity<BlockTestModel> = BlockActivity(nextStatementList())

                /**
                 * Number of statements in each activity.
                 * More statements provides more opportunities for interesting edge case interactions,
                 * but requires more time and power to run.
                 */
                val STATEMENTS_PER_ACTIVITY = 10

                /**
                 * To keep expressions from growing unreasonably large, expression generation is biased towards constants.
                 * Each expression has this chance of being a constant instead of a compound expression.
                 *
                 * Recommended to keep this set to 0.5 or higher.
                 */
                val CHANCE_OF_CONSTANT_EXPRESSION = 0.5

                override fun randomizeArgs(activity: Activity<BlockTestModel>): Activity<BlockTestModel> {
                    // To keep the idea of an activity "edit" being less severe than fully replacing the activity,
                    // let's change only one of the statements in this activity.
                    activity as BlockActivity
                    val i = rng.nextInt(0..<activity.statements.size)
                    val newStatements = activity.statements.toMutableList()
                    newStatements[i] = nextStatementBlock()
                    return BlockActivity(newStatements)
                }

                private fun nextStatementList(): List<StatementBlock> =
                    (1..STATEMENTS_PER_ACTIVITY).map { nextStatementBlock() }

                private fun nextStatementBlock(): StatementBlock = rng.choose(
                    0.30 to ::nextEffectBlock,
                    0.25 to ::nextSaveValue,
                    0.20 to ::nextReportBlock,
                    0.20 to ::nextAwaitBlock,
                    // Spawn blocks create a disproportionately large fan-out in the AST.
                    // Bias the randomization away from them to keep AST's bounded.
                    0.02 to ::nextSpawnBlock
                )

                private fun nextEffectBlock(): EffectBlock = rng.choose(
                    ::nextSwitchEffectBlock,
                    ::nextTimerEffectBlock,
                    ::nextCounterEffectBlock,
                    ::nextSlopeEffectBlock,
                )

                private fun nextSwitchEffectBlock() = rng.choose(
                    { SwitchEffectBlock.SetSwitch(nextIntExpression(), nextBooleanExpression()) },
                    { SwitchEffectBlock.ToggleSwitch(nextIntExpression()) },
                )

                private fun nextTimerEffectBlock() = rng.choose(
                    { TimerEffectBlock.PauseTimer(nextIntExpression()) },
                    { TimerEffectBlock.ResumeTimer(nextIntExpression()) },
                    { TimerEffectBlock.ResetTimer(nextIntExpression()) },
                    { TimerEffectBlock.RestartTimer(nextIntExpression()) },
                )

                private fun nextCounterEffectBlock() = rng.choose(
                    { CounterEffectBlock.SetCounter(nextIntExpression(), nextIntExpression()) },
                    { CounterEffectBlock.IncrementCounter(nextIntExpression(), nextIntExpression()) },
                )

                private fun nextSlopeEffectBlock() = rng.choose(
                    { SlopeEffectBlock.SetSlope(nextIntExpression(), nextDoubleExpression()) },
                    { SlopeEffectBlock.IncreaseSlope(nextIntExpression(), nextDoubleExpression()) },
                )

                private fun nextAwaitBlock() = Await(nextBooleanResourceExpression())

                private fun nextSpawnBlock() = Spawn(nextStatementList())

                private fun nextReportBlock() = rng.choose(
                    { ReportBlock.ReportBoolean(nextBooleanExpression()) },
                    { ReportBlock.ReportInt(nextIntExpression()) },
                    { ReportBlock.ReportDouble(nextDoubleExpression()) },
                    { ReportBlock.ReportDuration(nextDurationExpression()) },
                )

                private fun nextSaveValue() = rng.choose(
                    { SaveValue.SaveBoolean(nextBooleanExpression()) },
                    { SaveValue.SaveInt(nextIntExpression()) },
                    { SaveValue.SaveDouble(nextDoubleExpression()) },
                    { SaveValue.SaveDuration(nextDurationExpression()) },
                )

                private fun nextBooleanExpression(): BooleanExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantBoolean(rng.chance())
                } else rng.choose(
                    { ReadSavedBoolean },
                    { ReadSwitch(nextIntExpression()) },
                    { CompareInt(nextIntExpression(), nextIntExpression()) },
                    { CompareDouble(nextDoubleExpression(), nextDoubleExpression()) },
                    { CompareDuration(nextDurationExpression(), nextDurationExpression()) },
                    { And(nextBooleanExpression(), nextBooleanExpression()) },
                    { Or(nextBooleanExpression(), nextBooleanExpression()) },
                    { Not(nextBooleanExpression()) },
                )

                private fun nextIntExpression(): IntExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantInt(rng.nextInt(-100, 100))
                } else rng.choose(
                    { ReadSavedInt },
                    { ReadCounter(nextIntExpression()) },
                    { AddInts(nextIntExpression(), nextIntExpression()) },
                    { SubtractInts(nextIntExpression(), nextIntExpression()) },
                    { IntFromDouble(nextDoubleExpression()) },
                )

                private fun nextDoubleExpression(): DoubleExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDouble(rng.nextDouble(-100.0, 100.0))
                } else rng.choose(
                    { ReadSavedDouble },
                    { ReadSlope(nextIntExpression()) },
                    { ReadIntegral(nextIntExpression()) },
                    { AddDoubles(nextDoubleExpression(), nextDoubleExpression()) },
                    { SubtractDoubles(nextDoubleExpression(), nextDoubleExpression()) },
                    { DoubleFromInt(nextIntExpression()) },
                )

                private fun nextDurationExpression(): DurationExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDuration(rng.nextDouble(-100.0, 100.0).seconds)
                } else rng.choose(
                    { ReadSavedDuration },
                    { ReadTimer(nextIntExpression()) },
                    { DurationFromDouble(nextDoubleExpression()) },
                )

                private fun nextBooleanResourceExpression(): BooleanResourceExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantBooleanResource(nextBooleanExpression())
                } else rng.choose(
                    { Switch(nextIntExpression()) },
                    { AndResource(nextBooleanResourceExpression(), nextBooleanResourceExpression()) },
                    { OrResource(nextBooleanResourceExpression(), nextBooleanResourceExpression()) },
                    { NotResource(nextBooleanResourceExpression()) },
                    { CompareIntResource(nextIntResourceExpression(), nextIntResourceExpression()) },
                    { CompareDoubleResource(nextDoubleResourceExpression(), nextDoubleResourceExpression()) },
                    { CompareTimerResource(nextTimerResourceExpression(), nextTimerResourceExpression()) },
                    { ComparePolynomialResource(nextPolynomialResourceExpression(), nextPolynomialResourceExpression()) },
                )

                private fun nextIntResourceExpression(): IntResourceExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantIntResource(nextIntExpression())
                } else rng.choose(
                    { Counter(nextIntExpression()) },
                    { AddIntResources(nextIntResourceExpression(), nextIntResourceExpression()) },
                    { SubtractIntResources(nextIntResourceExpression(), nextIntResourceExpression()) },
                )

                private fun nextDoubleResourceExpression(): DoubleResourceExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDoubleResource(nextDoubleExpression())
                } else rng.choose(
                    { Slope(nextIntExpression()) },
                    { AddDoubleResources(nextDoubleResourceExpression(), nextDoubleResourceExpression()) },
                    { SubtractDoubleResources(nextDoubleResourceExpression(), nextDoubleResourceExpression()) },
                )

                private fun nextTimerResourceExpression(): TimerResourceExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantTimerResource(nextDurationExpression())
                } else rng.choose(
                    { Timer(nextIntExpression()) },
                    { AddTimerResources(nextTimerResourceExpression(), nextTimerResourceExpression()) },
                    { SubtractTimerResources(nextTimerResourceExpression(), nextTimerResourceExpression()) },
                )

                private fun nextPolynomialResourceExpression(): PolynomialResourceExpression = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantPolynomialResourceExpression(nextDoubleExpression())
                } else rng.choose(
                    { Integral(nextIntExpression()) },
                    { AddPolynomialResources(nextPolynomialResourceExpression(), nextPolynomialResourceExpression()) },
                    { SubtractPolynomialResources(nextPolynomialResourceExpression(), nextPolynomialResourceExpression()) },
                    { PolynomialFromDoubleResource(nextDoubleResourceExpression()) },
                    { PolynomialFromTimerResource(nextTimerResourceExpression()) },
                )
            }
        }
    }

    /**
     * Identical to [`random plan edits conform to fundamental incremental sim guarantee -- model 2`],
     * but seeded with a smaller set of seeds.
     * This provides some coverage on every build, with the option to run the more-intensive version only on request.
     */
    @ParameterizedTest
    @MethodSource("fuzzingSeeds -- lightweight")
    fun `random plan edits conform to fundamental incremental sim guarantee -- model 2 lightweight`(seed: Int) {
        `random plan edits conform to fundamental incremental sim guarantee -- model 2`(seed)
    }

    interface RandomActivityGenerator<M> {
        fun constructModel(initScope: InitScope) : M
        fun nextActivity(): Activity<M>
        fun randomizeArgs(activity: Activity<M>): Activity<M>
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
    fun <M : Any> `random plan edits conform to fundamental incremental sim guarantee`(
        seed: Int,
        randomActivityGeneratorConstructor: (Random) -> RandomActivityGenerator<M>,
        ) {
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

        val rng = Random(seed)
        val activityGenerator = randomActivityGeneratorConstructor(rng)
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
        val activities = mutableListOf<GroundedActivity<M>>()
        repeat(numberOfInitialActivities) {
            activities += GroundedActivity(
                rng.nextInstant(startTime..endTime),
                rng.nextActivityId(),
                activityGenerator.nextActivity()).also { println("Add $it") }
        }
        endBlock()
        // Verify the incremental simulator can handle that initial plan
        var tester = test(activityGenerator::constructModel, activities)
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
                val newActivities = mutableListOf<GroundedActivity<M>>()
                repeat (10.0.pow(rng.nextDouble(1.0, 3.0)).toInt()) {
                    newActivities += GroundedActivity(
                        rng.nextInstant(startTime..endTime),
                        rng.nextActivityId(),
                        activityGenerator.nextActivity()).also { println("Add $it") }
                }
                // Then build a new incremental tester with those time bounds, saving and restoring from a checkpoint
                tester = test(
                    constructModel = activityGenerator::constructModel,
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
            var edits = PlanEdits<M>()
            startBlock("Choosing $numberOfEdits random edits")
            // Pick random edits to make. If we edit an activity, remove it from activities so it doesn't get edited twice.
            repeat (numberOfEdits) {
                when (rng.nextInt(1..4)) {
                    1 -> {
                        // Add an activity
                        edits += GroundedActivity(
                            rng.nextInstant(startTime..endTime),
                            rng.nextActivityId(),
                            activityGenerator.nextActivity()
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
                        val newActivity = activityGenerator.randomizeArgs(activity.activity)
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
            // Removals are done in-place as we go
            activities += edits.additions
            endBlock()
        }
    }

    private fun Random.nextInstant(range: ClosedRange<Instant>): Instant =
        range.start + nextLong(0..range.start.until(range.endInclusive, DateTimeUnit.MICROSECOND)).microseconds

    private fun Random.chance(p: Double = 0.5): Boolean = nextDouble() < p

    private fun <R> Random.choose(vararg branches: () -> R): R {
        return branches[nextInt(branches.indices)]()
    }

    private fun <R> Random.choose(vararg branches: Pair<Double, () -> R>): R {
        val totalWeight = branches.sumOf { it.first }
        var p = nextDouble(0.0, totalWeight)
        for ((weight, branch) in branches) {
            if (p < weight) return branch()
            else p -= weight
        }
        // Edge case, likely due to FP error. Run the last branch as a fallback.
        return branches.last().second()
    }

    private fun <T> MutableList<T>.randomRemove(rng: Random): T =
        removeAt(rng.nextInt(0..<size))

    companion object {
        @JvmStatic
        fun fuzzingSeeds(): IntStream = IntStream.rangeClosed(1, 100_000)
        @JvmStatic
        fun `fuzzingSeeds -- lightweight`(): IntStream = IntStream.rangeClosed(1, 100)
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
        // Resources:
        for ((resourceName, baselineResource) in baseResults.resources) {
            assert(resourceName in testResults.resources)
            val testResource = testResults.resources.getValue(resourceName)
            assertEquals(baselineResource.metadata, testResource.metadata)
            if (resourceName in setOf("stdout", "stderr", "activities").map(::Name)) {
                // For these channels, we tolerate some nondeterminism in the ordering of simultaneous messages.
                // This should be "modded out" by a proper interpretation of the channel.
                val remainingTestReports = testResource.data.toMutableList()
                val testReportBatch: MutableList<ChannelData<*>> = mutableListOf()
                var batchTime = Instant.DISTANT_PAST
                for (baselineReport in baselineResource.data) {
                    // First, check if we've passed the last-collected batch time
                    if (baselineReport.time > batchTime) {
                        // If so, make sure we matched the full batch
                        assert(testReportBatch.isEmpty()) {
                            "Extra reports on $resourceName: $testReportBatch"
                        }
                        // Then collect the next batch
                        assert(remainingTestReports.isNotEmpty()) {
                            "Missing report on $resourceName: $baselineReport"
                        }
                        batchTime = remainingTestReports.first().time
                        while (remainingTestReports.firstOrNull()?.time == batchTime) {
                            testReportBatch += remainingTestReports.removeFirst()
                        }
                    }
                    // Then, match the report in this batch
                    if (resourceName == Name("stderr")) {
                        // Special case - stderr reports may have stack traces.
                        // We don't need to match stack frames. Fitler those out.
                        val normalizedBaselineReport = normalizeErrorReport(baselineReport)
                        val n = testReportBatch.indexOfFirst { normalizeErrorReport(it) == normalizedBaselineReport }
                        assert(n >= 0) {
                            "Missing report on $resourceName: $baselineReport"
                        }
                        testReportBatch.removeAt(n)
                    } else {
                        assert(testReportBatch.remove(baselineReport)) {
                            "Missing report on $resourceName: $baselineReport"
                        }
                    }
                }
                // Finally, ensure all the test reports were consumed
                assert(testReportBatch.isEmpty()) {
                    "Extra reports on $resourceName: $testReportBatch"
                }
                assert(remainingTestReports.isEmpty()) {
                    "Extra reports on $resourceName: $remainingTestReports"
                }
            } else {
                // For general channels, demand a stricter correspondence - even reports at the same time must be in the same order.
                // This is because a resource may change value multiple times in one instant, and must settle on the correct value.
                for ((baselineReport, testReport) in baselineResource.data zip testResource.data) {
                    assertEquals(baselineReport, testReport)
                }
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

    @Suppress("UNCHECKED_CAST")
    private fun normalizeErrorReport(report: ChannelData<*>): ChannelData<String> =
        // Error messages are generally brittle, sensitive to minor changes in ordering that we can't / don't want to worry about.
        // Normalize to just the first line of text, which tends to be more stable.
        (report as ChannelData<String>).copy(data = report.data.split('\n', limit=2).first())

    override fun save(time: Instant): Checkpoint<M> {
        // Save checkpoints from both, and assert they're equivalent
        val baselineCheckpoint = baselineSimulation.save(time)
        val testCheckpoint = testSimulation.save(time)

        // Since a checkpoint is likely to have issues, do a fine-grained comparison to aid debugging
        assertEquals(baselineCheckpoint.time, testCheckpoint.time)
        assert(baselineCheckpoint.cells.valueEquals(testCheckpoint.cells) { x, y ->
            if (x is Result<*>) {
                // Special handling for Result, so we can mod out the exceptions
                y as Result<*>
                // If both are successes, compare them
                if (x.isSuccess && y.isSuccess) {
                    x.getOrThrow() == y.getOrThrow()
                } else {
                    // If they're both failures, they're equal, because we don't introspect on the exceptions.
                    // One failure and one success are not equal.
                    x.isFailure && y.isFailure
                }
            } else {
                // General case: just use regular object equality
                x == y
            }
        })

        // Daemons are stored as a list, but order isn't relevant
        val remainingTestDaemons = testCheckpoint.daemons.toMutableList()
        for (baselineDaemon in baselineCheckpoint.daemons) {
            // Find the relevant daemon entry by name and remove it
            val n = remainingTestDaemons.indexOfFirst { it.name == baselineDaemon.name }
            require(n >= 0) {
                "Checkpoint is missing daemon ${baselineDaemon.name}"
            }
            val testDaemon = remainingTestDaemons.removeAt(n)
            // Assert that each field is equal separately to aid debugging
            assertEquals(baselineDaemon.name, testDaemon.name)
            assertEquals(baselineDaemon.root, testDaemon.root)
            assertEquals(baselineDaemon.time, testDaemon.time)
            if (baselineDaemon.history == null) {
                assert(testDaemon.history == null)
            } else {
                assertNotNull(testDaemon.history)
                assert(baselineDaemon.history.valueEquals(testDaemon.history) { x, y ->
                    if (x is PureTask.TaskHistoryStep.ReadMarker<*> && x.value is Result<*>) {
                        // Special case for results, to mod out exceptions
                        val xResult = x.value
                        val yResult = (y as PureTask.TaskHistoryStep.ReadMarker<*>).value as Result<*>
                        if (xResult.isSuccess && yResult.isSuccess) {
                            // If both reads were successful, compare their value
                            xResult.getOrThrow() == yResult.getOrThrow()
                        } else {
                            // Otherwise, they're equal if they're both failures; don't inspect the exceptions.
                            // One failure and one success are never equal.
                            xResult.isFailure && yResult.isFailure
                        }
                    } else {
                        // General case: fall back on simple object equality
                        x == y
                    }
                })
            }
        }
        assert(remainingTestDaemons.isEmpty()) {
            "Checkpoint has unexpected daemons: " + remainingTestDaemons.joinToString(", ") { it.name.toString() }
        }

        // Activities are stored as a list, but order isn't relevant
        val remainingTestActivities = testCheckpoint.activities.toMutableList()
        for (baselineActivity in baselineCheckpoint.activities) {
            // Non-root activities might not have unique names.
            // This is an expected situation, though it makes debugging the tests a little awkward.
            val candidates = remainingTestActivities
                .withIndex()
                .filter { it.value.name == baselineActivity.name }
            require(candidates.isNotEmpty()) { "No activity named ${baselineActivity.name}" }
            if (candidates.size == 1) {
                val (n, testActivity) = candidates.single()
                // In the very common case of a single candidate,
                // assert that each field is equal separately to aid debugging
                assertEquals(baselineActivity.name, testActivity.name)
                assertEquals(baselineActivity.time, testActivity.time)
                assertEquals(baselineActivity.activity, testActivity.activity)
                assertEquals(baselineActivity.history, testActivity.history)
                remainingTestActivities.removeAt(n)
            } else {
                // Otherwise, perform the harder-to-debug but still correct filtering of candidates
                val candidate = candidates.firstOrNull { (_, testActivity) -> baselineActivity == testActivity }
                assertNotNull(candidate) { "No checkpoint matching $baselineActivity" }
                remainingTestActivities.removeAt(candidate.index)
            }

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

    // Designed to easily force simultaneous activities, to flush out problems caused by concurrency.
    data class SpawnChildPair(val child1: Activity<TestModel>, val child2: Activity<TestModel>) : Activity<TestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: TestModel) {
            spawn(child1, model)
            spawn(child2, model)
        }
    }

}

/**
 * A second test model for fuzz testing.
 * This model is designed to allow for more flexible but less targeted randomization.
 */
class BlockTestModel(initScope: InitScope) {
    val switches: List<MutableBooleanResource>
    val timers: List<MutableTimerResource>
    val counters: List<MutableIntResource>
    val slopes: List<MutableDoubleResource>
    val integrals: List<IntegralResource>

    init {
        context (initScope) {
            switches = (0..<BLOCK_TEST_MODEL_SIZE).map { discreteResource("switch_$it", false).registered() }
            timers = (0..<BLOCK_TEST_MODEL_SIZE).map { timer("timer_$it", initialRate = 0.0) }
            counters = (0..<BLOCK_TEST_MODEL_SIZE).map { discreteResource("counter_$it", 0).registered() }
            slopes = (0..<BLOCK_TEST_MODEL_SIZE).map { discreteResource("slope_$it", 0.0).registered() }
            integrals = (0..<BLOCK_TEST_MODEL_SIZE).map { slopes[it].asPolynomial().integral("integral_$it", 0.0).registered() }
        }
    }

    companion object {
        /** Number of each kind of resource in [BlockTestModel] */
        const val BLOCK_TEST_MODEL_SIZE = 3
    }

    data class BlockActivity(val statements: List<StatementBlock>) : Activity<BlockTestModel> {
        var savedBoolean: Boolean = false
        var savedInt: Int = 0
        var savedDouble: Double = 0.0
        var savedDuration: Duration = ZERO

        context(scope: TaskScope)
        override suspend fun effectModel(model: BlockTestModel) {
            statements.forEach { it.run(model, this) }
        }
    }

    sealed interface StatementBlock {
        context (_: TaskScope)
        suspend fun run(model: BlockTestModel, activity: BlockActivity)

        sealed interface EffectBlock : StatementBlock {
            sealed interface SwitchEffectBlock : EffectBlock {
                data class SetSwitch(val index: IntExpression, val value: BooleanExpression) : SwitchEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.switches[index.evaluate(model, activity) % model.switches.size].set(value.evaluate(model, activity))
                    }
                }
                data class ToggleSwitch(val index: IntExpression) : SwitchEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.switches[index.evaluate(model, activity) % model.switches.size].toggle()
                    }
                }
            }
            sealed interface TimerEffectBlock : EffectBlock {
                data class PauseTimer(val index: IntExpression) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.timers[index.evaluate(model, activity) % model.timers.size].pause()
                    }
                }
                data class ResumeTimer(val index: IntExpression) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.timers[index.evaluate(model, activity) % model.timers.size].resume()
                    }
                }
                data class ResetTimer(val index: IntExpression) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.timers[index.evaluate(model, activity) % model.timers.size].reset()
                    }
                }
                data class RestartTimer(val index: IntExpression) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.timers[index.evaluate(model, activity) % model.timers.size].restart()
                    }
                }
            }
            sealed interface CounterEffectBlock : EffectBlock {
                data class SetCounter(val index: IntExpression, val value: IntExpression) : CounterEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.counters[index.evaluate(model, activity) % model.counters.size].set(value.evaluate(model, activity))
                    }
                }
                data class IncrementCounter(val index: IntExpression, val amount: IntExpression) : CounterEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.counters[index.evaluate(model, activity) % model.counters.size].increment(amount.evaluate(model, activity))
                    }
                }
            }
            sealed interface SlopeEffectBlock : EffectBlock {
                data class SetSlope(val index: IntExpression, val value: DoubleExpression) : SlopeEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.slopes[index.evaluate(model, activity) % model.slopes.size].set(value.evaluate(model, activity))
                    }
                }
                data class IncreaseSlope(val index: IntExpression, val amount: DoubleExpression) : SlopeEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                        model.slopes[index.evaluate(model, activity) % model.slopes.size].increase(amount.evaluate(model, activity))
                    }
                }
            }
        }
        data class Await(val condition: BooleanResourceExpression) : StatementBlock {
            context(_: TaskScope)
            override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                await(condition.evaluate(model, activity))
            }
        }
        data class Spawn(val body: List<StatementBlock>) : StatementBlock {
            context(_: TaskScope)
            override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                val child = BlockActivity(body)
                // Copy state information from parent to child
                child.savedBoolean = activity.savedBoolean
                child.savedInt = activity.savedInt
                child.savedDouble = activity.savedDouble
                child.savedDuration = activity.savedDuration
                spawn(child, model)
                // However, the parent may not modify the child after the child is spawned.
            }
        }
        sealed interface ReportBlock : StatementBlock {
            data class ReportBoolean(val expression: BooleanExpression) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    stdout.report(expression.evaluate(model, activity).toString())
                }
            }
            data class ReportInt(val expression: IntExpression) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    stdout.report(expression.evaluate(model, activity).toString())
                }
            }
            data class ReportDouble(val expression: DoubleExpression) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    stdout.report(expression.evaluate(model, activity).toString())
                }
            }
            data class ReportDuration(val expression: DurationExpression) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    stdout.report(expression.evaluate(model, activity).toString())
                }
            }
        }
        sealed interface SaveValue : StatementBlock {
            data class SaveBoolean(val expression: BooleanExpression) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    activity.savedBoolean = expression.evaluate(model, activity)
                }
            }
            data class SaveInt(val expression: IntExpression) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    activity.savedInt = expression.evaluate(model, activity)
                }
            }
            data class SaveDouble(val expression: DoubleExpression) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    activity.savedDouble = expression.evaluate(model, activity)
                }
            }
            data class SaveDuration(val expression: DurationExpression) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, activity: BlockActivity) {
                    activity.savedDuration = expression.evaluate(model, activity)
                }
            }
        }
    }
    sealed interface ExpressionBlock<R> {
        context(_: TaskScope)
        fun evaluate(model: BlockTestModel, activity: BlockActivity): R

        sealed interface BooleanExpression : ExpressionBlock<Boolean> {
            data class ConstantBoolean(val value: Boolean) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean = value
            }
            data object ReadSavedBoolean : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean = activity.savedBoolean
            }
            data class ReadSwitch(val indexExpression: IntExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    model.switches[indexExpression.evaluate(model, activity) % model.switches.size].getValue()
            }
            data class CompareInt(val left: IntExpression, val right: IntExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    left.evaluate(model, activity) > right.evaluate(model, activity)
            }
            data class CompareDouble(val left: DoubleExpression, val right: DoubleExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    left.evaluate(model, activity) > right.evaluate(model, activity)
            }
            data class CompareDuration(val left: DurationExpression, val right: DurationExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    left.evaluate(model, activity) > right.evaluate(model, activity)
            }
            data class And(val left: BooleanExpression, val right: BooleanExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    left.evaluate(model, activity) && right.evaluate(model, activity)
            }
            data class Or(val left: BooleanExpression, val right: BooleanExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    left.evaluate(model, activity) || right.evaluate(model, activity)
            }
            data class Not(val expression: BooleanExpression) : BooleanExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Boolean =
                    !expression.evaluate(model, activity)
            }
        }
        sealed interface IntExpression : ExpressionBlock<Int> {
            data class ConstantInt(val value: Int) : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int = value
            }
            data object ReadSavedInt : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int = activity.savedInt
            }
            data class ReadCounter(val indexExpression: IntExpression) : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int =
                    model.counters[indexExpression.evaluate(model, activity) % model.counters.size].getValue()
            }
            data class AddInts(val left: IntExpression, val right: IntExpression) : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractInts(val left: IntExpression, val right: IntExpression) : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
            data class IntFromDouble(val doubleExpression: DoubleExpression) : IntExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Int =
                    doubleExpression.evaluate(model, activity).toInt()
            }
        }
        sealed interface DoubleExpression : ExpressionBlock<Double> {
            data class ConstantDouble(val value: Double) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double = value
            }
            data object ReadSavedDouble : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double = activity.savedDouble
            }
            data class ReadSlope(val indexExpression: IntExpression) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double =
                    model.slopes[indexExpression.evaluate(model, activity) % model.slopes.size].getValue()
            }
            data class ReadIntegral(val indexExpression: IntExpression) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double =
                    model.integrals[indexExpression.evaluate(model, activity) % model.integrals.size].getValue()
            }
            data class AddDoubles(val left: DoubleExpression, val right: DoubleExpression) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractDoubles(val left: DoubleExpression, val right: DoubleExpression) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
            data class DoubleFromInt(val intExpression: IntExpression) : DoubleExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Double =
                    intExpression.evaluate(model, activity).toDouble()
            }
        }
        sealed interface DurationExpression : ExpressionBlock<Duration> {
            data class ConstantDuration(val value: Duration) : DurationExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Duration = value
            }
            data object ReadSavedDuration : DurationExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Duration = activity.savedDuration
            }
            data class ReadTimer(val indexExpression: IntExpression) : DurationExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Duration =
                    model.timers[indexExpression.evaluate(model, activity) % model.timers.size].getValue()
            }
            data class DurationFromDouble(val doubleExpression: DoubleExpression) : DurationExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): Duration =
                    doubleExpression.evaluate(model, activity).seconds
            }
        }
        sealed interface BooleanResourceExpression : ExpressionBlock<BooleanResource> {
            data class ConstantBooleanResource(val value: BooleanExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    DiscreteResourceMonad.pure(value.evaluate(model, activity))
            }
            data class Switch(val indexExpression: IntExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    model.switches[indexExpression.evaluate(model, activity) % model.switches.size]
            }
            data class AndResource(val left: BooleanResourceExpression, val right: BooleanResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) and right.evaluate(model, activity)
            }
            data class OrResource(val left: BooleanResourceExpression, val right: BooleanResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) or right.evaluate(model, activity)
            }
            data class NotResource(val expression: BooleanResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    expression.evaluate(model, activity).not()
            }
            data class CompareIntResource(val left: IntResourceExpression, val right: IntResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) greaterThan right.evaluate(model, activity)
            }
            data class CompareDoubleResource(val left: DoubleResourceExpression, val right: DoubleResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) greaterThan right.evaluate(model, activity)
            }
            data class CompareTimerResource(val left: TimerResourceExpression, val right: TimerResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) greaterThan right.evaluate(model, activity)
            }
            data class ComparePolynomialResource(val left: PolynomialResourceExpression, val right: PolynomialResourceExpression) : BooleanResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): BooleanResource =
                    left.evaluate(model, activity) greaterThan right.evaluate(model, activity)
            }
        }
        sealed interface IntResourceExpression : ExpressionBlock<IntResource> {
            data class ConstantIntResource(val value: IntExpression) : IntResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): IntResource =
                    DiscreteResourceMonad.pure(value.evaluate(model, activity))
            }
            data class Counter(val indexExpression: IntExpression) : IntResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): IntResource =
                    model.counters[indexExpression.evaluate(model, activity) % model.counters.size]
            }
            data class AddIntResources(val left: IntResourceExpression, val right: IntResourceExpression) : IntResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): IntResource =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractIntResources(val left: IntResourceExpression, val right: IntResourceExpression) : IntResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): IntResource =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
        }
        sealed interface DoubleResourceExpression : ExpressionBlock<DoubleResource> {
            data class ConstantDoubleResource(val value: DoubleExpression) : DoubleResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): DoubleResource =
                    DiscreteResourceMonad.pure(value.evaluate(model, activity))
            }
            data class Slope(val indexExpression: IntExpression) : DoubleResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): DoubleResource =
                    model.slopes[indexExpression.evaluate(model, activity) % model.slopes.size]
            }
            data class AddDoubleResources(val left: DoubleResourceExpression, val right: DoubleResourceExpression) : DoubleResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): DoubleResource =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractDoubleResources(val left: DoubleResourceExpression, val right: DoubleResourceExpression) : DoubleResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): DoubleResource =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
        }
        sealed interface TimerResourceExpression : ExpressionBlock<TimerResource> {
            data class ConstantTimerResource(val value: DurationExpression) : TimerResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): TimerResource =
                    TimerResourceOperations.constant(value.evaluate(model, activity))
            }
            data class Timer(val indexExpression: IntExpression) : TimerResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): TimerResource =
                    model.timers[indexExpression.evaluate(model, activity) % model.timers.size]
            }
            data class AddTimerResources(val left: TimerResourceExpression, val right: TimerResourceExpression) : TimerResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): TimerResource =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractTimerResources(val left: TimerResourceExpression, val right: TimerResourceExpression) : TimerResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): TimerResource =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
        }
        sealed interface PolynomialResourceExpression : ExpressionBlock<PolynomialResource> {
            data class ConstantPolynomialResourceExpression(val value: DoubleExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    constant(value.evaluate(model, activity))
            }
            data class Integral(val indexExpression: IntExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    model.integrals[indexExpression.evaluate(model, activity) % model.integrals.size]
            }
            data class AddPolynomialResources(val left: PolynomialResourceExpression, val right: PolynomialResourceExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    left.evaluate(model, activity) + right.evaluate(model, activity)
            }
            data class SubtractPolynomialResources(val left: PolynomialResourceExpression, val right: PolynomialResourceExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    left.evaluate(model, activity) - right.evaluate(model, activity)
            }
            data class PolynomialFromDoubleResource(val doubleResourceExpression: DoubleResourceExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    doubleResourceExpression.evaluate(model, activity).asPolynomial()
            }
            data class PolynomialFromTimerResource(val timerResourceExpression: TimerResourceExpression) : PolynomialResourceExpression {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, activity: BlockActivity): PolynomialResource =
                    timerResourceExpression.evaluate(model, activity).asPolynomial(1.seconds)
            }
        }
    }
}
