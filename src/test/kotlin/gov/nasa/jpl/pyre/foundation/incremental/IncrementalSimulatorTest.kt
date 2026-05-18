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
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.EffectBlock.CounterEffectBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.EffectBlock.SlopeEffectBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.EffectBlock.SwitchEffectBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.EffectBlock.TimerEffectBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.ReportBlock.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.SaveValue.*
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
import kotlin.time.times

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

    private fun <M : Any> test(
        constructModel: (InitScope) -> M,
        vararg activities: GroundedActivity<M>,
        startTime: Instant = day0,
        endTime: Instant = day1,
        incon: Checkpoint<M>? = null,
    ) = test(constructModel, activities.toList(), startTime, endTime, incon)

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

    @Test
    fun `repro by seed`() {
        `random plan edits conform to fundamental incremental sim guarantee -- model 2`(1)
    }

    @Test
    fun `repro directly`() {
        test(::BlockTestModel,
            GroundedActivity(Instant.parse("2025-01-01T00:01:07.691928Z"), Name("483838683711"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ReportBoolean(expression=CompareDuration(left=ReadSavedDuration, right=ReadSavedDuration)), ReportInt(expression=ConstantInt(value=-53)), Await(condition=CompareIntResource(left=AddIntResources(left=SubtractIntResources(left=ConstantIntResource(value=ReadSavedInt), right=SubtractIntResources(left=ConstantIntResource(value=ReadSavedInt), right=ConstantIntResource(value=AddInts(left=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-5.985934404207512)), right=SubtractInts(left=ConstantInt(value=13), right=ConstantInt(value=-2))), right=IntFromDouble(doubleExpression=ConstantDouble(value=-5.023629581985304)))))), right=ConstantIntResource(value=ConstantInt(value=-97))), right=Counter(indexExpression=SubtractInts(left=ConstantInt(value=-33), right=ReadSavedInt)))), RestartTimer(index=ConstantInt(value=17)), ReportDuration(expression=ConstantDuration(value="-55.679727996".toDuration())), ReportDouble(expression=ConstantDouble(value=-30.76013511105313)), ResumeTimer(index=ConstantInt(value=76)), ToggleSwitch(index=SubtractInts(left=ConstantInt(value=-52), right=ConstantInt(value=28))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true)))))),
            GroundedActivity(Instant.parse("2025-01-01T07:21:51.877560Z"), Name("654313001547"), BlockActivity(statements=listOf(Await(condition=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=ConstantDouble(value=-68.97485693066345)), right=AddPolynomialResources(left=ConstantPolynomialResourceExpression(value=ConstantDouble(value=48.94564896013887)), right=PolynomialFromDoubleResource(doubleResourceExpression=Slope(indexExpression=ReadSavedInt))))), PauseTimer(index=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=-79), right=ConstantInt(value=8)))), Await(condition=OrResource(left=CompareIntResource(left=SubtractIntResources(left=ConstantIntResource(value=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=-82)))), right=ConstantIntResource(value=ConstantInt(value=11))), right=Counter(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ReadSavedInt)))), right=OrResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=ConstantBooleanResource(value=ConstantBoolean(value=false))))), SetSwitch(index=ConstantInt(value=-65), value=ConstantBoolean(value=false)), SaveInt(expression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-59)), right=ConstantInt(value=-88))), Await(condition=CompareIntResource(left=ConstantIntResource(value=ConstantInt(value=35)), right=ConstantIntResource(value=ConstantInt(value=14)))), ReportBoolean(expression=ConstantBoolean(value=true)), ReportDuration(expression=ReadSavedDuration), ResetTimer(index=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-89.96945179917834)))), ReportDouble(expression=ConstantDouble(value=8.784717538494036))))),
            GroundedActivity(Instant.parse("2025-01-01T12:44:44.596028Z"), Name("902774936810"), BlockActivity(statements=listOf(SaveBoolean(expression=ReadSwitch(indexExpression=ConstantInt(value=-17))), SaveInt(expression=ConstantInt(value=39)), ReportDouble(expression=ConstantDouble(value=62.63223255855402)), ReportBoolean(expression=Or(left=ConstantBoolean(value=false), right=Or(left=CompareInt(left=IntFromDouble(doubleExpression=ConstantDouble(value=99.56657539078935)), right=AddInts(left=ReadCounter(indexExpression=SubtractInts(left=AddInts(left=AddInts(left=ConstantInt(value=81), right=ReadSavedInt), right=ReadSavedInt), right=ReadSavedInt)), right=ConstantInt(value=26))), right=CompareInt(left=SubtractInts(left=SubtractInts(left=AddInts(left=AddInts(left=AddInts(left=ConstantInt(value=-8), right=ReadSavedInt), right=ConstantInt(value=61)), right=ConstantInt(value=-2)), right=ConstantInt(value=-15)), right=ReadCounter(indexExpression=ConstantInt(value=18))), right=SubtractInts(left=ConstantInt(value=-97), right=AddInts(left=AddInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=14))), right=ConstantInt(value=89)), right=ConstantInt(value=-50))))))), Await(condition=ConstantBooleanResource(value=CompareDouble(left=ConstantDouble(value=-34.49782787465338), right=ConstantDouble(value=22.040758907794228)))), Await(condition=CompareDoubleResource(left=ConstantDoubleResource(value=ConstantDouble(value=20.807108686630116)), right=ConstantDoubleResource(value=ConstantDouble(value=46.54692324345993)))), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=-22))), SaveDouble(expression=ConstantDouble(value=84.54497943701975)), ToggleSwitch(index=ConstantInt(value=52)), Await(condition=ConstantBooleanResource(value=CompareDuration(left=DurationFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=-39.84392128386445), right=AddDoubles(left=ConstantDouble(value=-65.89557264979646), right=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=48))))))), right=ConstantDuration(value="-59.697836601".toDuration()))))))),
            GroundedActivity(Instant.parse("2025-01-01T14:17:41.447131Z"), Name("837261189620"), BlockActivity(statements=listOf(IncreaseSlope(index=AddInts(left=ReadSavedInt, right=ReadCounter(indexExpression=ConstantInt(value=-1))), amount=ConstantDouble(value=31.804500235469447)), IncrementCounter(index=ConstantInt(value=2), amount=ReadCounter(indexExpression=ConstantInt(value=-19))), ReportDouble(expression=ConstantDouble(value=-56.76257629833712)), SaveInt(expression=ConstantInt(value=-53)), IncreaseSlope(index=ConstantInt(value=60), amount=SubtractDoubles(left=ConstantDouble(value=-25.88111796650263), right=ConstantDouble(value=24.184568672694724))), Await(condition=AndResource(left=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=ConstantDouble(value=81.01241674325951)), right=ConstantPolynomialResourceExpression(value=DoubleFromInt(intExpression=ReadSavedInt))), right=AndResource(left=ConstantBooleanResource(value=ConstantBoolean(value=false)), right=CompareDoubleResource(left=ConstantDoubleResource(value=ConstantDouble(value=33.08344895458566)), right=AddDoubleResources(left=Slope(indexExpression=ConstantInt(value=-22)), right=SubtractDoubleResources(left=SubtractDoubleResources(left=AddDoubleResources(left=ConstantDoubleResource(value=ReadSavedDouble), right=Slope(indexExpression=ConstantInt(value=-59))), right=AddDoubleResources(left=ConstantDoubleResource(value=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-55)), right=ConstantDouble(value=48.78584441056904))), right=SubtractDoubleResources(left=SubtractDoubleResources(left=AddDoubleResources(left=Slope(indexExpression=SubtractInts(left=ConstantInt(value=-32), right=ConstantInt(value=-73))), right=ConstantDoubleResource(value=ConstantDouble(value=31.733693986803246))), right=ConstantDoubleResource(value=ConstantDouble(value=39.130064904995635))), right=AddDoubleResources(left=Slope(indexExpression=AddInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=-39)), right=AddInts(left=ConstantInt(value=89), right=ReadCounter(indexExpression=ConstantInt(value=-46))))), right=ConstantDoubleResource(value=ConstantDouble(value=92.3548423745666)))))), right=ConstantDoubleResource(value=ConstantDouble(value=-94.8955748722137)))))))), SaveBoolean(expression=CompareDouble(left=ReadIntegral(indexExpression=AddInts(left=ConstantInt(value=11), right=ReadCounter(indexExpression=ReadSavedInt))), right=ReadIntegral(indexExpression=ReadSavedInt))), ReportDuration(expression=ConstantDuration(value="12.731997187".toDuration())), SetSwitch(index=ConstantInt(value=14), value=Or(left=Or(left=Not(expression=ReadSwitch(indexExpression=ReadSavedInt)), right=ReadSavedBoolean), right=And(left=ConstantBoolean(value=false), right=ConstantBoolean(value=true)))), Await(condition=ConstantBooleanResource(value=CompareDuration(left=ReadTimer(indexExpression=ReadCounter(indexExpression=ConstantInt(value=-72))), right=ConstantDuration(value="-43.590970458".toDuration()))))))),
            GroundedActivity(Instant.parse("2025-01-01T18:49:08.814480Z"), Name("255390846859"), BlockActivity(statements=listOf(SaveDouble(expression=ConstantDouble(value=27.057667995849272)), ReportInt(expression=ReadCounter(indexExpression=ConstantInt(value=51))), ReportBoolean(expression=And(left=ReadSwitch(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=38.671022963350396))), right=ConstantBoolean(value=false))), ReportInt(expression=ConstantInt(value=38)), Spawn(body=listOf(ReportDuration(expression=ConstantDuration(value="-01:04.385971743".toDuration())), SaveBoolean(expression=CompareInt(left=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=68.48682110873014), right=ConstantDouble(value=-66.48819332035771))), right=ConstantInt(value=75))), SetCounter(index=ConstantInt(value=67), value=ConstantInt(value=54)), Await(condition=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=true)))), SaveBoolean(expression=ConstantBoolean(value=true)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ReportInt(expression=IntFromDouble(doubleExpression=ConstantDouble(value=-3.451381332000551))), SaveBoolean(expression=ConstantBoolean(value=true)), IncreaseSlope(index=ConstantInt(value=-84), amount=ConstantDouble(value=-34.24479375542968)), ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)))), ToggleSwitch(index=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-10))), Await(condition=AndResource(left=AndResource(left=ConstantBooleanResource(value=And(left=And(left=Not(expression=ConstantBoolean(value=true)), right=ReadSavedBoolean), right=ReadSavedBoolean)), right=CompareTimerResource(left=AddTimerResources(left=ConstantTimerResource(value=ReadSavedDuration), right=SubtractTimerResources(left=ConstantTimerResource(value=ReadTimer(indexExpression=ReadCounter(indexExpression=ConstantInt(value=22)))), right=ConstantTimerResource(value=ConstantDuration(value="01:38.260819686".toDuration())))), right=AddTimerResources(left=Timer(indexExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-9)), right=ConstantInt(value=-46))), right=SubtractTimerResources(left=AddTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="-01:16.513114630".toDuration())), right=SubtractTimerResources(left=Timer(indexExpression=ReadSavedInt), right=Timer(indexExpression=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=-63))))))), right=ConstantTimerResource(value=ConstantDuration(value="-34.681811930".toDuration())))))), right=ComparePolynomialResource(left=Integral(indexExpression=ReadCounter(indexExpression=ConstantInt(value=86))), right=ConstantPolynomialResourceExpression(value=SubtractDoubles(left=ReadSavedDouble, right=ReadIntegral(indexExpression=SubtractInts(left=ConstantInt(value=-16), right=AddInts(left=SubtractInts(left=AddInts(left=ConstantInt(value=-65), right=ConstantInt(value=88)), right=ConstantInt(value=26)), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=52))))))))))), IncrementCounter(index=ConstantInt(value=-68), amount=ConstantInt(value=45)), ReportBoolean(expression=ReadSavedBoolean), SaveDuration(expression=ConstantDuration(value="-56.933280853".toDuration()))))),
            GroundedActivity(Instant.parse("2025-01-01T13:58:23.529112Z"), Name("574998643161"), BlockActivity(statements=listOf(SetSwitch(index=ReadSavedInt, value=CompareDouble(left=ReadSavedDouble, right=DoubleFromInt(intExpression=ConstantInt(value=-48)))), SaveBoolean(expression=ConstantBoolean(value=false)), SaveDouble(expression=ReadIntegral(indexExpression=ConstantInt(value=-54))), ToggleSwitch(index=ConstantInt(value=-98)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveInt(expression=SubtractInts(left=AddInts(left=ConstantInt(value=30), right=AddInts(left=ConstantInt(value=-20), right=ConstantInt(value=-13))), right=ConstantInt(value=32))), Await(condition=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=82)))), ReportInt(expression=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-30.390646761991547)), right=ConstantInt(value=-59))), Await(condition=ConstantBooleanResource(value=CompareDuration(left=ReadSavedDuration, right=ConstantDuration(value="-08.316223449".toDuration())))), Await(condition=Switch(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-44.65152648104078))))))),
            GroundedActivity(Instant.parse("2025-01-01T20:09:46.963637Z"), Name("857176700904"), BlockActivity(statements=listOf(SaveInt(expression=ConstantInt(value=23)), PauseTimer(index=SubtractInts(left=AddInts(left=ConstantInt(value=84), right=SubtractInts(left=ConstantInt(value=50), right=ConstantInt(value=65))), right=IntFromDouble(doubleExpression=ConstantDouble(value=-55.92525049795587)))), SaveInt(expression=SubtractInts(left=SubtractInts(left=ConstantInt(value=72), right=IntFromDouble(doubleExpression=SubtractDoubles(left=SubtractDoubles(left=AddDoubles(left=ConstantDouble(value=-96.34711526631948), right=ConstantDouble(value=-15.18019815600205)), right=ConstantDouble(value=-57.558710010268996)), right=ConstantDouble(value=-68.1939291169683)))), right=ConstantInt(value=24))), IncrementCounter(index=ReadSavedInt, amount=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=-13), right=IntFromDouble(doubleExpression=ReadSavedDouble)))), SetSwitch(index=SubtractInts(left=ConstantInt(value=3), right=ReadSavedInt), value=ConstantBoolean(value=true)), Await(condition=CompareDoubleResource(left=AddDoubleResources(left=AddDoubleResources(left=AddDoubleResources(left=ConstantDoubleResource(value=SubtractDoubles(left=DoubleFromInt(intExpression=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-70)), right=ReadSavedInt)), right=ReadSavedDouble)), right=ConstantDoubleResource(value=SubtractDoubles(left=ReadSlope(indexExpression=ReadSavedInt), right=ConstantDouble(value=62.90048930041189)))), right=SubtractDoubleResources(left=Slope(indexExpression=ReadSavedInt), right=ConstantDoubleResource(value=ConstantDouble(value=-69.58045801200058)))), right=Slope(indexExpression=ConstantInt(value=11))), right=ConstantDoubleResource(value=ConstantDouble(value=70.60897732697671)))), IncreaseSlope(index=ConstantInt(value=9), amount=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-22.39399025602704)))), SaveBoolean(expression=ReadSwitch(indexExpression=SubtractInts(left=ConstantInt(value=-65), right=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-41)), right=ConstantInt(value=63))))), Await(condition=ConstantBooleanResource(value=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=true)))), SaveDouble(expression=SubtractDoubles(left=ConstantDouble(value=-1.27044799515825), right=ConstantDouble(value=-8.490262473734717)))))),
            GroundedActivity(Instant.parse("2025-01-01T12:51:31.533593Z"), Name("101995554742"), BlockActivity(statements=listOf(SaveBoolean(expression=ReadSavedBoolean), ReportInt(expression=ConstantInt(value=-94)), SaveBoolean(expression=Or(left=ConstantBoolean(value=true), right=Not(expression=ConstantBoolean(value=true)))), SaveBoolean(expression=ConstantBoolean(value=false)), ReportDouble(expression=AddDoubles(left=ConstantDouble(value=-94.49060490744985), right=SubtractDoubles(left=ConstantDouble(value=-12.362422594915401), right=ConstantDouble(value=-52.785829443023104)))), SaveDouble(expression=ConstantDouble(value=-33.318247281529125)), SaveDuration(expression=ConstantDuration(value="-14.209847514".toDuration())), SaveBoolean(expression=ConstantBoolean(value=true)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), SetSwitch(index=ConstantInt(value=33), value=ConstantBoolean(value=false))))),
            GroundedActivity(Instant.parse("2025-01-01T08:43:34.463882Z"), Name("937390277938"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=CompareDuration(left=ConstantDuration(value="-01:11.723796359".toDuration()), right=ConstantDuration(value="01:12.476627774".toDuration())))), ReportDouble(expression=ConstantDouble(value=-72.89132907892304)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), Await(condition=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=-78)))), ResetTimer(index=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=-14), right=AddInts(left=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=-99))), right=ReadSavedInt)))), Spawn(body=listOf(Await(condition=ConstantBooleanResource(value=Not(expression=ConstantBoolean(value=false)))), ToggleSwitch(index=AddInts(left=SubtractInts(left=ConstantInt(value=-25), right=ConstantInt(value=-7)), right=IntFromDouble(doubleExpression=ConstantDouble(value=-10.216255594924121)))), ReportInt(expression=ReadCounter(indexExpression=ConstantInt(value=-2))), ReportDouble(expression=SubtractDoubles(left=SubtractDoubles(left=ConstantDouble(value=-38.2980063604873), right=ConstantDouble(value=-54.89604256599312)), right=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=68)), right=ReadSlope(indexExpression=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=84), right=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-71)), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt))))))))), SaveInt(expression=ConstantInt(value=19)), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=-59))), SaveDouble(expression=ReadIntegral(indexExpression=ReadSavedInt)), SaveDuration(expression=ConstantDuration(value="-30.793239911".toDuration())), IncrementCounter(index=AddInts(left=IntFromDouble(doubleExpression=ReadSavedDouble), right=ConstantInt(value=-95)), amount=ConstantInt(value=-13)), SetCounter(index=SubtractInts(left=ConstantInt(value=54), right=ConstantInt(value=98)), value=ConstantInt(value=-49)))), ToggleSwitch(index=IntFromDouble(doubleExpression=ConstantDouble(value=35.50787082709306))), SaveBoolean(expression=ConstantBoolean(value=false)), ToggleSwitch(index=ConstantInt(value=37))))),
            GroundedActivity(Instant.parse("2025-01-01T15:30:20.661422Z"), Name("608785295009"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=ReadSavedBoolean)), IncreaseSlope(index=ConstantInt(value=56), amount=ReadIntegral(indexExpression=ConstantInt(value=-77))), SetCounter(index=ConstantInt(value=-56), value=ConstantInt(value=-74)), SetCounter(index=ConstantInt(value=-57), value=ConstantInt(value=-56)), SaveInt(expression=ConstantInt(value=61)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveInt(expression=ConstantInt(value=79)), Spawn(body=listOf(SetSlope(index=ReadSavedInt, value=ReadIntegral(indexExpression=ConstantInt(value=-4))), Await(condition=CompareDoubleResource(left=AddDoubleResources(left=AddDoubleResources(left=AddDoubleResources(left=Slope(indexExpression=SubtractInts(left=ConstantInt(value=-19), right=ConstantInt(value=-6))), right=SubtractDoubleResources(left=AddDoubleResources(left=SubtractDoubleResources(left=ConstantDoubleResource(value=ReadSlope(indexExpression=ConstantInt(value=7))), right=ConstantDoubleResource(value=ConstantDouble(value=44.77747791098665))), right=SubtractDoubleResources(left=SubtractDoubleResources(left=ConstantDoubleResource(value=ConstantDouble(value=49.457637120130585)), right=ConstantDoubleResource(value=ReadIntegral(indexExpression=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-53.41532782036682)), right=ConstantInt(value=-70))))), right=ConstantDoubleResource(value=ReadSlope(indexExpression=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=94), right=ConstantInt(value=78))))))), right=ConstantDoubleResource(value=ConstantDouble(value=11.917015646034471)))), right=Slope(indexExpression=ConstantInt(value=33))), right=ConstantDoubleResource(value=ReadSlope(indexExpression=ReadCounter(indexExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=AddDoubles(left=DoubleFromInt(intExpression=ConstantInt(value=28)), right=SubtractDoubles(left=ConstantDouble(value=92.42243125724261), right=ReadSlope(indexExpression=ReadCounter(indexExpression=SubtractInts(left=SubtractInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=46))), right=ConstantInt(value=-43)), right=ConstantInt(value=42)))))), right=AddDoubles(left=SubtractDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-100)), right=ConstantDouble(value=-54.7194278365724)), right=ConstantDouble(value=-39.90243707006762)))))))), right=AddDoubleResources(left=ConstantDoubleResource(value=ConstantDouble(value=-66.70051794825187)), right=Slope(indexExpression=ConstantInt(value=-78))))), Await(condition=NotResource(expression=ConstantBooleanResource(value=Or(left=CompareDuration(left=ReadSavedDuration, right=ReadSavedDuration), right=ConstantBoolean(value=false))))), SetSlope(index=AddInts(left=AddInts(left=SubtractInts(left=ConstantInt(value=17), right=ConstantInt(value=77)), right=ReadSavedInt), right=AddInts(left=AddInts(left=AddInts(left=ConstantInt(value=85), right=ConstantInt(value=-10)), right=IntFromDouble(doubleExpression=ReadSlope(indexExpression=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-21))))), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadCounter(indexExpression=ConstantInt(value=94)))))), value=ReadIntegral(indexExpression=AddInts(left=ConstantInt(value=-2), right=ConstantInt(value=-66)))), SetSwitch(index=ConstantInt(value=90), value=CompareDouble(left=ConstantDouble(value=-69.67701742093762), right=SubtractDoubles(left=SubtractDoubles(left=ReadSlope(indexExpression=SubtractInts(left=AddInts(left=ConstantInt(value=-8), right=ReadSavedInt), right=ReadSavedInt)), right=ConstantDouble(value=38.906438558610546)), right=ReadSavedDouble))), SaveDuration(expression=ConstantDuration(value="01:31.568234672".toDuration())), SaveInt(expression=ConstantInt(value=97)), SaveInt(expression=ConstantInt(value=-53)), ReportInt(expression=ConstantInt(value=78)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))))), ReportDuration(expression=ReadSavedDuration), Await(condition=CompareTimerResource(left=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ReadSavedDouble)), right=Timer(indexExpression=ConstantInt(value=28))))))),
            GroundedActivity(Instant.parse("2025-01-01T02:18:28.568894Z"), Name("353601142381"), BlockActivity(statements=listOf(ToggleSwitch(index=ReadSavedInt), SaveDouble(expression=DoubleFromInt(intExpression=ConstantInt(value=-78))), SaveDouble(expression=DoubleFromInt(intExpression=ConstantInt(value=92))), Await(condition=ConstantBooleanResource(value=Not(expression=Not(expression=Not(expression=ConstantBoolean(value=true)))))), SetSlope(index=ConstantInt(value=-71), value=ConstantDouble(value=81.47219605360752)), ReportInt(expression=IntFromDouble(doubleExpression=ConstantDouble(value=-78.0141992002094))), ReportInt(expression=ConstantInt(value=-24)), SaveBoolean(expression=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=true))), SaveBoolean(expression=ConstantBoolean(value=true)), IncreaseSlope(index=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-27)), right=ConstantInt(value=20)), amount=ConstantDouble(value=53.08518281282929))))),
            GroundedActivity(Instant.parse("2025-01-01T00:36:40.263539Z"), Name("277911849858"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=And(left=ConstantBoolean(value=true), right=CompareDouble(left=ConstantDouble(value=-5.215054620455376), right=ReadIntegral(indexExpression=ConstantInt(value=7)))))), SaveInt(expression=ConstantInt(value=-64)), IncrementCounter(index=ConstantInt(value=-86), amount=IntFromDouble(doubleExpression=ConstantDouble(value=29.274717282324616))), ToggleSwitch(index=ConstantInt(value=-40)), Await(condition=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=false)))), SaveDouble(expression=ConstantDouble(value=24.874634551939906)), ReportBoolean(expression=CompareDuration(left=ConstantDuration(value="32.388014716".toDuration()), right=ConstantDuration(value="01:38.806049449".toDuration()))), IncrementCounter(index=ReadCounter(indexExpression=ConstantInt(value=69)), amount=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=73), right=ReadSavedInt))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ResetTimer(index=ConstantInt(value=4))))),
            GroundedActivity(Instant.parse("2025-01-01T22:59:49.132893Z"), Name("246497586105"), BlockActivity(statements=listOf(ReportInt(expression=ReadSavedInt), ReportDouble(expression=SubtractDoubles(left=ConstantDouble(value=-43.72005123793807), right=ConstantDouble(value=-33.553780057139804))), SaveDouble(expression=ConstantDouble(value=39.58344959703982)), IncrementCounter(index=ReadCounter(indexExpression=ConstantInt(value=-6)), amount=ConstantInt(value=-54)), Await(condition=ConstantBooleanResource(value=Or(left=And(left=ReadSavedBoolean, right=And(left=ConstantBoolean(value=true), right=CompareDouble(left=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-51)), right=ConstantDouble(value=-37.30141480583822)), right=ConstantDouble(value=-27.077719431640517)))), right=ReadSavedBoolean))), ReportInt(expression=AddInts(left=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=22.15050193631312), right=AddDoubles(left=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=58)), right=SubtractDoubles(left=DoubleFromInt(intExpression=ReadCounter(indexExpression=ConstantInt(value=-1))), right=SubtractDoubles(left=ConstantDouble(value=41.2138613725067), right=ConstantDouble(value=-76.21280911153742)))), right=ConstantDouble(value=16.85046389534162)))), right=ConstantInt(value=-94))), ResumeTimer(index=ReadSavedInt), ReportDuration(expression=ReadSavedDuration), SaveDouble(expression=ReadSlope(indexExpression=ReadSavedInt)), SetCounter(index=ConstantInt(value=-63), value=ConstantInt(value=-67))))),
            GroundedActivity(Instant.parse("2025-01-01T22:13:52.938713Z"), Name("321923042996"), BlockActivity(statements=listOf(ReportInt(expression=ReadSavedInt), ReportDouble(expression=AddDoubles(left=ReadSavedDouble, right=ReadSlope(indexExpression=AddInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=-68)), right=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=-64.45010789197256), right=DoubleFromInt(intExpression=ConstantInt(value=76)))))))), SaveInt(expression=ReadSavedInt), ReportDouble(expression=ConstantDouble(value=-92.66442848229045)), ReportInt(expression=ConstantInt(value=17)), SaveBoolean(expression=ReadSavedBoolean), SaveInt(expression=SubtractInts(left=ConstantInt(value=-12), right=IntFromDouble(doubleExpression=ConstantDouble(value=-47.84327284878604)))), SaveDuration(expression=ConstantDuration(value="-23.398142383".toDuration())), SetSwitch(index=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=98), right=ConstantInt(value=58))), value=Or(left=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=true)), right=And(left=CompareDouble(left=ConstantDouble(value=90.9022541577736), right=ConstantDouble(value=83.50160934725693)), right=ConstantBoolean(value=false)))), RestartTimer(index=ConstantInt(value=-17))))),
            GroundedActivity(Instant.parse("2025-01-01T10:42:48.473786Z"), Name("624386624395"), BlockActivity(statements=listOf(SaveDuration(expression=ConstantDuration(value="01:35.289346995".toDuration())), RestartTimer(index=ConstantInt(value=-85)), SaveInt(expression=IntFromDouble(doubleExpression=ReadSavedDouble)), ReportBoolean(expression=Not(expression=ConstantBoolean(value=false))), SetSlope(index=ReadSavedInt, value=ReadSavedDouble), ResumeTimer(index=ConstantInt(value=-13)), RestartTimer(index=AddInts(left=ConstantInt(value=-77), right=AddInts(left=ConstantInt(value=45), right=ReadSavedInt))), IncrementCounter(index=ConstantInt(value=-42), amount=ConstantInt(value=-92)), SaveInt(expression=SubtractInts(left=SubtractInts(left=ConstantInt(value=-78), right=ConstantInt(value=-97)), right=IntFromDouble(doubleExpression=ConstantDouble(value=-63.214228816889985)))), Spawn(body=listOf(ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), IncrementCounter(index=ConstantInt(value=-10), amount=ConstantInt(value=28)), ReportDuration(expression=ReadSavedDuration), SaveInt(expression=ConstantInt(value=82)), ReportDouble(expression=AddDoubles(left=ReadSavedDouble, right=ReadSavedDouble)), PauseTimer(index=ConstantInt(value=18)), SaveDuration(expression=ReadSavedDuration), SaveBoolean(expression=ConstantBoolean(value=true)), SetCounter(index=ConstantInt(value=3), value=ConstantInt(value=-17)), ReportDuration(expression=ConstantDuration(value="14.741911434".toDuration()))))))),
            GroundedActivity(Instant.parse("2025-01-01T06:38:32.704615Z"), Name("396730047877"), BlockActivity(statements=listOf(ReportBoolean(expression=ConstantBoolean(value=false)), SaveBoolean(expression=And(left=ConstantBoolean(value=true), right=ConstantBoolean(value=false))), ReportInt(expression=ConstantInt(value=13)), Await(condition=ConstantBooleanResource(value=Or(left=ReadSavedBoolean, right=CompareInt(left=ReadSavedInt, right=ConstantInt(value=57))))), SetSlope(index=ConstantInt(value=-70), value=ConstantDouble(value=-58.168674153107844)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), RestartTimer(index=SubtractInts(left=ConstantInt(value=-65), right=ReadCounter(indexExpression=ConstantInt(value=-42)))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveDouble(expression=ConstantDouble(value=64.4018368009759)), SaveDouble(expression=ConstantDouble(value=-30.236350910963196))))),
            GroundedActivity(Instant.parse("2025-01-01T08:35:55.730539Z"), Name("772646701987"), BlockActivity(statements=listOf(Await(condition=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=false)))), ReportDuration(expression=DurationFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=78.27000013890623), right=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=5)), right=ConstantDouble(value=96.07170944525848))))), IncrementCounter(index=SubtractInts(left=ConstantInt(value=-74), right=ReadCounter(indexExpression=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=83.60297408036959))))), amount=ReadCounter(indexExpression=ConstantInt(value=25))), SetSlope(index=ConstantInt(value=0), value=ConstantDouble(value=-84.92356829256605)), SaveDouble(expression=ConstantDouble(value=-24.7613882487463)), SaveInt(expression=ConstantInt(value=-28)), SetSwitch(index=ConstantInt(value=63), value=CompareDouble(left=ReadSlope(indexExpression=ConstantInt(value=84)), right=ConstantDouble(value=-42.86925893753122))), ReportInt(expression=ConstantInt(value=72)), SaveBoolean(expression=ConstantBoolean(value=true)), SetSlope(index=SubtractInts(left=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=96), right=ReadSavedInt)), right=ReadSavedInt), value=ConstantDouble(value=20.365385209928505))))),
            GroundedActivity(Instant.parse("2025-01-01T13:53:12.464638Z"), Name("530515633729"), BlockActivity(statements=listOf(SaveInt(expression=ReadSavedInt), ReportBoolean(expression=ConstantBoolean(value=false)), ReportDuration(expression=ConstantDuration(value="04.620289034".toDuration())), Await(condition=ConstantBooleanResource(value=CompareInt(left=ConstantInt(value=-25), right=ReadSavedInt))), ToggleSwitch(index=ConstantInt(value=26)), ReportInt(expression=ConstantInt(value=-23)), SaveDouble(expression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ReadSavedDouble, right=ConstantDouble(value=-21.59039208230587))))), ResetTimer(index=SubtractInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=67)), right=ConstantInt(value=51))), ResumeTimer(index=AddInts(left=AddInts(left=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=66.1276045337759))), right=ConstantInt(value=-96)), right=ConstantInt(value=4))), SaveBoolean(expression=ConstantBoolean(value=true))))),
            GroundedActivity(Instant.parse("2025-01-01T00:13:08.586389Z"), Name("932404337583"), BlockActivity(statements=listOf(Await(condition=Switch(indexExpression=ReadSavedInt)), RestartTimer(index=ConstantInt(value=24)), ResetTimer(index=ConstantInt(value=8)), ReportInt(expression=SubtractInts(left=ConstantInt(value=18), right=ConstantInt(value=-99))), ReportInt(expression=ConstantInt(value=4)), SaveDouble(expression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=71.252526254889), right=ReadSlope(indexExpression=ConstantInt(value=21)))))), Spawn(body=listOf(ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), PauseTimer(index=ReadCounter(indexExpression=ConstantInt(value=-62))), Await(condition=CompareIntResource(left=AddIntResources(left=ConstantIntResource(value=ReadCounter(indexExpression=ConstantInt(value=-55))), right=AddIntResources(left=SubtractIntResources(left=Counter(indexExpression=ConstantInt(value=92)), right=ConstantIntResource(value=ConstantInt(value=-63))), right=Counter(indexExpression=ConstantInt(value=14)))), right=Counter(indexExpression=ConstantInt(value=81)))), SaveDouble(expression=SubtractDoubles(left=ReadSavedDouble, right=ReadSlope(indexExpression=ConstantInt(value=-57)))), ReportDouble(expression=ReadSavedDouble), Await(condition=ConstantBooleanResource(value=ReadSavedBoolean)), Await(condition=NotResource(expression=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=-48))))), Spawn(body=listOf(ReportDuration(expression=ConstantDuration(value="-01:20.178618929".toDuration())), ReportBoolean(expression=CompareDuration(left=ConstantDuration(value="-01:14.300647480".toDuration()), right=ConstantDuration(value="37.516004462".toDuration()))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ResetTimer(index=ReadCounter(indexExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-38)), right=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=50), right=ConstantInt(value=-57)))))), SaveDuration(expression=ConstantDuration(value="01:34.747799811".toDuration())), ToggleSwitch(index=SubtractInts(left=ConstantInt(value=65), right=ReadSavedInt)), ReportDuration(expression=ConstantDuration(value="-01:30.955243137".toDuration())), Spawn(body=listOf(Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ReportDuration(expression=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=24), right=SubtractInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt)), right=SubtractInts(left=ConstantInt(value=-65), right=ReadCounter(indexExpression=ReadSavedInt)))))))), SaveBoolean(expression=Not(expression=Not(expression=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=false))))), IncreaseSlope(index=ConstantInt(value=37), amount=ReadSavedDouble), Await(condition=ConstantBooleanResource(value=Not(expression=Not(expression=Or(left=CompareDouble(left=ConstantDouble(value=-1.0664155656394882), right=ReadSavedDouble), right=ReadSavedBoolean))))), Await(condition=ConstantBooleanResource(value=Or(left=ConstantBoolean(value=true), right=CompareDouble(left=ConstantDouble(value=-97.0009185070913), right=ConstantDouble(value=29.966662583792072))))), SetCounter(index=IntFromDouble(doubleExpression=ConstantDouble(value=-75.62592609797834)), value=ConstantInt(value=14)), SaveInt(expression=ConstantInt(value=26)), ReportDouble(expression=ReadSlope(indexExpression=ReadSavedInt)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))))), SaveInt(expression=ConstantInt(value=-18)), IncreaseSlope(index=ReadCounter(indexExpression=ReadSavedInt), amount=ConstantDouble(value=61.655579103496024)))), IncrementCounter(index=ConstantInt(value=-10), amount=ReadSavedInt), ReportBoolean(expression=ConstantBoolean(value=false)))), SetCounter(index=SubtractInts(left=IntFromDouble(doubleExpression=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=39)), right=ConstantDouble(value=-0.8222882702965393))), right=AddInts(left=ConstantInt(value=-24), right=ConstantInt(value=3))), value=ConstantInt(value=43)), IncreaseSlope(index=IntFromDouble(doubleExpression=ConstantDouble(value=95.07795871869521)), amount=SubtractDoubles(left=ConstantDouble(value=37.709450865609796), right=ReadSavedDouble)), ReportDouble(expression=ConstantDouble(value=18.60356564114028)))))
        )
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
                    { SetSwitch(nextIntExpression(), nextBooleanExpression()) },
                    { ToggleSwitch(nextIntExpression()) },
                )

                private fun nextTimerEffectBlock() = rng.choose(
                    { PauseTimer(nextIntExpression()) },
                    { ResumeTimer(nextIntExpression()) },
                    { ResetTimer(nextIntExpression()) },
                    { RestartTimer(nextIntExpression()) },
                )

                private fun nextCounterEffectBlock() = rng.choose(
                    { SetCounter(nextIntExpression(), nextIntExpression()) },
                    { IncrementCounter(nextIntExpression(), nextIntExpression()) },
                )

                private fun nextSlopeEffectBlock() = rng.choose(
                    { SetSlope(nextIntExpression(), nextDoubleExpression()) },
                    { IncreaseSlope(nextIntExpression(), nextDoubleExpression()) },
                )

                private fun nextAwaitBlock() = Await(nextBooleanResourceExpression())

                private fun nextSpawnBlock() = Spawn(nextStatementList())

                private fun nextReportBlock() = rng.choose(
                    { ReportBoolean(nextBooleanExpression()) },
                    { ReportInt(nextIntExpression()) },
                    { ReportDouble(nextDoubleExpression()) },
                    { ReportDuration(nextDurationExpression()) },
                )

                private fun nextSaveValue() = rng.choose(
                    { SaveBoolean(nextBooleanExpression()) },
                    { SaveInt(nextIntExpression()) },
                    { SaveDouble(nextDoubleExpression()) },
                    { SaveDuration(nextDurationExpression()) },
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

// Private test-ism to read a duration in a legible string format, used to print BlockActivity duration args.
// Explanation of the regex: Full format is <sign><days>T<hours>:<minutes>:<seconds>
// Leading fields (days; days and hours; or days, hours, and minutes) may be omitted; omitted fields are implicitly 0.
// Sign may be omitted for positive durations
// seconds may optionally include a fractional part
private val DURATION_PATTERN = Regex("(?<sign>[-+])?(?:(?:(?:(?<days>\\d*)T)?(?<hours>\\d*):)?(?<minutes>\\d*):)?(?<seconds>\\d*(?:.\\d*)?)")
private fun String.toDuration(): Duration {
    val match = requireNotNull(DURATION_PATTERN.matchEntire(this)) {
        "Duration string '$this' does not match duration format $DURATION_PATTERN"
    }
    val signum = if ((match.groups["sign"]?.value ?: "") == "-") -1 else 1
    return signum * (
            (match.groups["days"]?.value?.takeUnless { it.isEmpty() }?.toInt() ?: 0).days
            + (match.groups["hours"]?.value?.takeUnless { it.isEmpty() }?.toInt() ?: 0).hours
            + (match.groups["minutes"]?.value?.takeUnless { it.isEmpty() }?.toInt() ?: 0).minutes
            + (match.groups["seconds"]?.value?.takeUnless { it.isEmpty() }?.toDouble() ?: 0.0).seconds
    )
}
/** Python-style "string that evaluates to the object" repr-string, for durations. */
private fun Duration.repr(): String {
    val sign = if (this < 0.seconds) "-" else ""
    return absoluteValue.toComponents { days, hours, minutes, seconds, nanoseconds ->
        val daysStr = if (days == 0L) "" else "%dT".format(days)
        val hoursStr = if (hours == 0) "" else "%02d:".format(hours)
        val minutesStr = if (minutes == 0) "" else "%02d:".format(minutes)
        val secondsStr = "%02d.%09d".format(seconds, nanoseconds)
        "\"%s\".toDuration()".format(sign + daysStr + hoursStr + minutesStr + secondsStr)
    }
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

        override fun toString(): String = "BlockActivity(statements=listOf(${statements.joinToString()}))"
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

            override fun toString(): String = "Spawn(body=listOf(${body.joinToString()}))"
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

                override fun toString(): String {
                    return "ConstantDuration(value=${value.repr()})"
                }
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
