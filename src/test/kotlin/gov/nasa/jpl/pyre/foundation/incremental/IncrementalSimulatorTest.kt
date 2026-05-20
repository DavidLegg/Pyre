package gov.nasa.jpl.pyre.foundation.incremental

import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.Expression.*
import gov.nasa.jpl.pyre.foundation.incremental.BlockTestModel.StatementBlock.*
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
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.unaryPlus
import gov.nasa.jpl.pyre.foundation.incremental.TestModel.*
import gov.nasa.jpl.pyre.foundation.resources.Resource
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
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
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
import java.lang.Math.floorMod
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
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.times

class IncrementalSimulatorTest {
    private val day0 = Instant.parse("2025-01-01T00:00:00Z")
    private val day1 = day0 + 1.days
    private val day2 = day1 + 1.days
    private val day3 = day2 + 1.days
    private val day4 = day3 + 1.days
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

    /**
     * Again, this is more of a test of the test harness than of the simulator itself.
     * We're checking that the saved vars are locals that don't leak between the simulators.
     */
    @Test
    fun `locals are reset after runs`() {
        test(::BlockTestModel,
            GroundedActivity(Instant.parse("2025-01-01T12:00:00Z"), BlockActivity(listOf(
                ReportInt(ReadSavedInt),
                SaveInt(ConstantInt(1)),
            ))),
        )
    }

    @Test
    fun `fine-grained timer intercepts`() {
        // Similar to some other later tests in this file, this is more of a test for foundation itself than for incremental sim in particular.
        // This tests the way we calculate timer comparisons for rates and values that don't precisely hit 0.
        // Sloppy intercept calculation stalled the single-shot simulator at one point.
        test(::BlockTestModel, endTime = day4, activities = listOf(
            GroundedActivity(Instant.parse("2025-01-01T01:00:00Z"), Name("Awaiter"), BlockActivity(listOf(
                Await(CompareTimerResource(
                    left = AddTimerResources(
                        left = Timer(ConstantInt(2)),
                        right = Timer(ConstantInt(2)),
                    ),
                    right = ConstantTimerResource(ConstantDuration(1.nanoseconds)),
                )),
            ))),
            GroundedActivity(Instant.parse("2025-01-01T02:00:00Z"), Name("Start Timer"), BlockActivity(listOf(
                ResumeTimer(ConstantInt(2)),
            ))),
        ))
    }

    @Test
    fun `repro by seed`() {
        `random plan edits conform to fundamental incremental sim guarantee -- model 2`(1)
    }

    @Test
    fun `repro directly`() {
        var tester = test(::BlockTestModel,
            endTime = day4,
            activities = listOf(
                GroundedActivity(Instant.parse("2025-01-01T13:58:23.529112Z"), Name("574998643161"), BlockActivity(statements=listOf(SetSwitch(index=ReadSavedInt, value=CompareDouble(left=ReadSavedDouble, right=DoubleFromInt(intExpression=ConstantInt(value=-48)))), SaveBoolean(expression=ConstantBoolean(value=false)), SaveDouble(expression=ReadIntegral(indexExpression=ConstantInt(value=-54))), ToggleSwitch(index=ConstantInt(value=-98)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveInt(expression=SubtractInts(left=AddInts(left=ConstantInt(value=30), right=AddInts(left=ConstantInt(value=-20), right=ConstantInt(value=-13))), right=ConstantInt(value=32))), Await(condition=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=82)))), ReportInt(expression=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-30.390646761991547)), right=ConstantInt(value=-59))), Await(condition=ConstantBooleanResource(value=CompareDuration(left=ReadSavedDuration, right=ConstantDuration(value="-08.316223449".toDuration())))), Await(condition=Switch(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-44.65152648104078))))))),
                GroundedActivity(Instant.parse("2025-01-01T20:09:46.963637Z"), Name("857176700904"), BlockActivity(statements=listOf(SaveInt(expression=ConstantInt(value=23)), PauseTimer(index=SubtractInts(left=AddInts(left=ConstantInt(value=84), right=SubtractInts(left=ConstantInt(value=50), right=ConstantInt(value=65))), right=IntFromDouble(doubleExpression=ConstantDouble(value=-55.92525049795587)))), SaveInt(expression=SubtractInts(left=SubtractInts(left=ConstantInt(value=72), right=IntFromDouble(doubleExpression=SubtractDoubles(left=SubtractDoubles(left=AddDoubles(left=ConstantDouble(value=-96.34711526631948), right=ConstantDouble(value=-15.18019815600205)), right=ConstantDouble(value=-57.558710010268996)), right=ConstantDouble(value=-68.1939291169683)))), right=ConstantInt(value=24))), IncrementCounter(index=ReadSavedInt, amount=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=-13), right=IntFromDouble(doubleExpression=ReadSavedDouble)))), SetSwitch(index=SubtractInts(left=ConstantInt(value=3), right=ReadSavedInt), value=ConstantBoolean(value=true)), Await(condition=CompareDoubleResource(left=AddDoubleResources(left=AddDoubleResources(left=AddDoubleResources(left=ConstantDoubleResource(value=SubtractDoubles(left=DoubleFromInt(intExpression=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-70)), right=ReadSavedInt)), right=ReadSavedDouble)), right=ConstantDoubleResource(value=SubtractDoubles(left=ReadSlope(indexExpression=ReadSavedInt), right=ConstantDouble(value=62.90048930041189)))), right=SubtractDoubleResources(left=Slope(indexExpression=ReadSavedInt), right=ConstantDoubleResource(value=ConstantDouble(value=-69.58045801200058)))), right=Slope(indexExpression=ConstantInt(value=11))), right=ConstantDoubleResource(value=ConstantDouble(value=70.60897732697671)))), IncreaseSlope(index=ConstantInt(value=9), amount=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-22.39399025602704)))), SaveBoolean(expression=ReadSwitch(indexExpression=SubtractInts(left=ConstantInt(value=-65), right=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-41)), right=ConstantInt(value=63))))), Await(condition=ConstantBooleanResource(value=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=true)))), SaveDouble(expression=SubtractDoubles(left=ConstantDouble(value=-1.27044799515825), right=ConstantDouble(value=-8.490262473734717)))))),
                GroundedActivity(Instant.parse("2025-01-01T10:42:48.473786Z"), Name("624386624395"), BlockActivity(statements=listOf(SaveDuration(expression=ConstantDuration(value="01:35.289346995".toDuration())), RestartTimer(index=ConstantInt(value=-85)), SaveInt(expression=IntFromDouble(doubleExpression=ReadSavedDouble)), ReportBoolean(expression=Not(expression=ConstantBoolean(value=false))), SetSlope(index=ReadSavedInt, value=ReadSavedDouble), ResumeTimer(index=ConstantInt(value=-13)), RestartTimer(index=AddInts(left=ConstantInt(value=-77), right=AddInts(left=ConstantInt(value=45), right=ReadSavedInt))), IncrementCounter(index=ConstantInt(value=-42), amount=ConstantInt(value=-92)), SaveInt(expression=SubtractInts(left=SubtractInts(left=ConstantInt(value=-78), right=ConstantInt(value=-97)), right=IntFromDouble(doubleExpression=ConstantDouble(value=-63.214228816889985)))), Spawn(body=listOf(ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), IncrementCounter(index=ConstantInt(value=-10), amount=ConstantInt(value=28)), ReportDuration(expression=ReadSavedDuration), SaveInt(expression=ConstantInt(value=82)), ReportDouble(expression=AddDoubles(left=ReadSavedDouble, right=ReadSavedDouble)), PauseTimer(index=ConstantInt(value=18)), SaveDuration(expression=ReadSavedDuration), SaveBoolean(expression=ConstantBoolean(value=true)), SetCounter(index=ConstantInt(value=3), value=ConstantInt(value=-17)), ReportDuration(expression=ConstantDuration(value="14.741911434".toDuration()))))))),
                GroundedActivity(Instant.parse("2025-01-01T08:35:55.730539Z"), Name("772646701987"), BlockActivity(statements=listOf(Await(condition=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=false)))), ReportDuration(expression=DurationFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=78.27000013890623), right=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=5)), right=ConstantDouble(value=96.07170944525848))))), IncrementCounter(index=SubtractInts(left=ConstantInt(value=-74), right=ReadCounter(indexExpression=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=83.60297408036959))))), amount=ReadCounter(indexExpression=ConstantInt(value=25))), SetSlope(index=ConstantInt(value=0), value=ConstantDouble(value=-84.92356829256605)), SaveDouble(expression=ConstantDouble(value=-24.7613882487463)), SaveInt(expression=ConstantInt(value=-28)), SetSwitch(index=ConstantInt(value=63), value=CompareDouble(left=ReadSlope(indexExpression=ConstantInt(value=84)), right=ConstantDouble(value=-42.86925893753122))), ReportInt(expression=ConstantInt(value=72)), SaveBoolean(expression=ConstantBoolean(value=true)), SetSlope(index=SubtractInts(left=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=96), right=ReadSavedInt)), right=ReadSavedInt), value=ConstantDouble(value=20.365385209928505))))),
                GroundedActivity(Instant.parse("2025-01-01T00:13:08.586389Z"), Name("932404337583"), BlockActivity(statements=listOf(Await(condition=Switch(indexExpression=ReadSavedInt)), RestartTimer(index=ConstantInt(value=24)), ResetTimer(index=ConstantInt(value=8)), ReportInt(expression=SubtractInts(left=ConstantInt(value=18), right=ConstantInt(value=-99))), ReportInt(expression=ConstantInt(value=4)), SaveDouble(expression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=71.252526254889), right=ReadSlope(indexExpression=ConstantInt(value=21)))))), Spawn(body=listOf(ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), PauseTimer(index=ReadCounter(indexExpression=ConstantInt(value=-62))), Await(condition=CompareIntResource(left=AddIntResources(left=ConstantIntResource(value=ReadCounter(indexExpression=ConstantInt(value=-55))), right=AddIntResources(left=SubtractIntResources(left=Counter(indexExpression=ConstantInt(value=92)), right=ConstantIntResource(value=ConstantInt(value=-63))), right=Counter(indexExpression=ConstantInt(value=14)))), right=Counter(indexExpression=ConstantInt(value=81)))), SaveDouble(expression=SubtractDoubles(left=ReadSavedDouble, right=ReadSlope(indexExpression=ConstantInt(value=-57)))), ReportDouble(expression=ReadSavedDouble), Await(condition=ConstantBooleanResource(value=ReadSavedBoolean)), Await(condition=NotResource(expression=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=-48))))), Spawn(body=listOf(ReportDuration(expression=ConstantDuration(value="-01:20.178618929".toDuration())), ReportBoolean(expression=CompareDuration(left=ConstantDuration(value="-01:14.300647480".toDuration()), right=ConstantDuration(value="37.516004462".toDuration()))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ResetTimer(index=ReadCounter(indexExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-38)), right=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=50), right=ConstantInt(value=-57)))))), SaveDuration(expression=ConstantDuration(value="01:34.747799811".toDuration())), ToggleSwitch(index=SubtractInts(left=ConstantInt(value=65), right=ReadSavedInt)), ReportDuration(expression=ConstantDuration(value="-01:30.955243137".toDuration())), Spawn(body=listOf(Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ReportDuration(expression=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=24), right=SubtractInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt)), right=SubtractInts(left=ConstantInt(value=-65), right=ReadCounter(indexExpression=ReadSavedInt)))))))), SaveBoolean(expression=Not(expression=Not(expression=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=false))))), IncreaseSlope(index=ConstantInt(value=37), amount=ReadSavedDouble), Await(condition=ConstantBooleanResource(value=Not(expression=Not(expression=Or(left=CompareDouble(left=ConstantDouble(value=-1.0664155656394882), right=ReadSavedDouble), right=ReadSavedBoolean))))), Await(condition=ConstantBooleanResource(value=Or(left=ConstantBoolean(value=true), right=CompareDouble(left=ConstantDouble(value=-97.0009185070913), right=ConstantDouble(value=29.966662583792072))))), SetCounter(index=IntFromDouble(doubleExpression=ConstantDouble(value=-75.62592609797834)), value=ConstantInt(value=14)), SaveInt(expression=ConstantInt(value=26)), ReportDouble(expression=ReadSlope(indexExpression=ReadSavedInt)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))))), SaveInt(expression=ConstantInt(value=-18)), IncreaseSlope(index=ReadCounter(indexExpression=ReadSavedInt), amount=ConstantDouble(value=61.655579103496024)))), IncrementCounter(index=ConstantInt(value=-10), amount=ReadSavedInt), ReportBoolean(expression=ConstantBoolean(value=false)))), SetCounter(index=SubtractInts(left=IntFromDouble(doubleExpression=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=39)), right=ConstantDouble(value=-0.8222882702965393))), right=AddInts(left=ConstantInt(value=-24), right=ConstantInt(value=3))), value=ConstantInt(value=43)), IncreaseSlope(index=IntFromDouble(doubleExpression=ConstantDouble(value=95.07795871869521)), amount=SubtractDoubles(left=ConstantDouble(value=37.709450865609796), right=ReadSavedDouble)), ReportDouble(expression=ConstantDouble(value=18.60356564114028))))),
                GroundedActivity(Instant.parse("2025-01-02T12:57:22.033555Z"), Name("311298831536"), BlockActivity(statements=listOf(RestartTimer(index=ConstantInt(value=-39)), SaveInt(expression=ReadSavedInt), SaveInt(expression=ConstantInt(value=-21)), Await(condition=CompareTimerResource(left=SubtractTimerResources(left=Timer(indexExpression=ConstantInt(value=23)), right=AddTimerResources(left=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ConstantDouble(value=-23.570594184988053))), right=SubtractTimerResources(left=SubtractTimerResources(left=ConstantTimerResource(value=ReadTimer(indexExpression=ReadSavedInt)), right=Timer(indexExpression=AddInts(left=ConstantInt(value=-65), right=SubtractInts(left=SubtractInts(left=ConstantInt(value=-31), right=SubtractInts(left=ConstantInt(value=70), right=ConstantInt(value=-23))), right=ReadCounter(indexExpression=ConstantInt(value=-46)))))), right=ConstantTimerResource(value=ConstantDuration(value="-01:30.483628010".toDuration()))))), right=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ReadSavedDouble)))), SetSlope(index=ConstantInt(value=-2), value=ConstantDouble(value=7.074983866542112)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ReportInt(expression=SubtractInts(left=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=31), right=ConstantInt(value=-53))), right=ConstantInt(value=51))), IncrementCounter(index=IntFromDouble(doubleExpression=ConstantDouble(value=91.93161208369116)), amount=ConstantInt(value=-55)), IncrementCounter(index=ConstantInt(value=-40), amount=ReadSavedInt), Await(condition=Switch(indexExpression=ConstantInt(value=-78)))))),
                GroundedActivity(Instant.parse("2025-01-02T20:33:33.650093Z"), Name("663470021030"), BlockActivity(statements=listOf(PauseTimer(index=ConstantInt(value=-86)), ReportDuration(expression=ReadTimer(indexExpression=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-39.36850310210103)), right=ConstantInt(value=47)))), SaveBoolean(expression=ConstantBoolean(value=false)), IncrementCounter(index=ConstantInt(value=-3), amount=ConstantInt(value=-30)), ReportDouble(expression=ConstantDouble(value=-11.5308692969965)), SetSwitch(index=ReadCounter(indexExpression=ConstantInt(value=35)), value=ConstantBoolean(value=true)), ReportDuration(expression=ConstantDuration(value="-13.559503071".toDuration())), SetSwitch(index=ConstantInt(value=65), value=ConstantBoolean(value=false)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), SaveDouble(expression=AddDoubles(left=ConstantDouble(value=-29.220804082786998), right=ReadSlope(indexExpression=ConstantInt(value=-93))))))),
                GroundedActivity(Instant.parse("2025-01-02T19:27:33.441911Z"), Name("843626626026"), BlockActivity(statements=listOf(ReportDuration(expression=ConstantDuration(value="-18.433346777".toDuration())), ReportInt(expression=ConstantInt(value=36)), Await(condition=CompareIntResource(left=AddIntResources(left=SubtractIntResources(left=SubtractIntResources(left=ConstantIntResource(value=IntFromDouble(doubleExpression=ConstantDouble(value=-86.05746699933876))), right=ConstantIntResource(value=AddInts(left=ConstantInt(value=-72), right=ConstantInt(value=-32)))), right=Counter(indexExpression=ReadSavedInt)), right=Counter(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=SubtractInts(left=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-36)), right=ConstantInt(value=-69)))))), right=ConstantIntResource(value=IntFromDouble(doubleExpression=ConstantDouble(value=7.982164366001541))))), ReportInt(expression=ReadCounter(indexExpression=ReadSavedInt)), SaveDouble(expression=SubtractDoubles(left=SubtractDoubles(left=ConstantDouble(value=-9.933295816661158), right=DoubleFromInt(intExpression=SubtractInts(left=ConstantInt(value=72), right=ConstantInt(value=28)))), right=AddDoubles(left=ConstantDouble(value=-75.16890879623897), right=ReadSavedDouble))), ReportInt(expression=ConstantInt(value=-64)), SaveInt(expression=ConstantInt(value=-57)), ToggleSwitch(index=ConstantInt(value=-91)), SetSwitch(index=IntFromDouble(doubleExpression=ConstantDouble(value=41.80291831828157)), value=ConstantBoolean(value=false)), SaveDouble(expression=ConstantDouble(value=-14.122532299052807))))),
                GroundedActivity(Instant.parse("2025-01-02T11:52:17.328975Z"), Name("356994499933"), BlockActivity(statements=listOf(Await(condition=CompareDoubleResource(left=AddDoubleResources(left=AddDoubleResources(left=ConstantDoubleResource(value=ReadSlope(indexExpression=SubtractInts(left=ConstantInt(value=92), right=ConstantInt(value=-49)))), right=ConstantDoubleResource(value=ConstantDouble(value=-51.461744349431186))), right=Slope(indexExpression=ConstantInt(value=21))), right=ConstantDoubleResource(value=ConstantDouble(value=-51.13396033435309)))), ReportDouble(expression=SubtractDoubles(left=ConstantDouble(value=40.390033119132596), right=AddDoubles(left=DoubleFromInt(intExpression=ReadSavedInt), right=ConstantDouble(value=35.998344080396976)))), IncreaseSlope(index=AddInts(left=SubtractInts(left=ConstantInt(value=90), right=ConstantInt(value=74)), right=ConstantInt(value=-92)), amount=ConstantDouble(value=23.10698134851583)), SetSwitch(index=ConstantInt(value=23), value=Not(expression=ConstantBoolean(value=true))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), SaveDuration(expression=ConstantDuration(value="01:39.593408010".toDuration())), ReportInt(expression=ConstantInt(value=85)), ReportInt(expression=ConstantInt(value=-47)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), IncrementCounter(index=ReadCounter(indexExpression=ReadSavedInt), amount=ReadSavedInt)))),
                GroundedActivity(Instant.parse("2025-01-02T21:35:21.234466Z"), Name("784073460995"), BlockActivity(statements=listOf(Await(condition=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=ReadSavedDouble), right=Integral(indexExpression=ConstantInt(value=86)))), SetSlope(index=ConstantInt(value=-90), value=ConstantDouble(value=98.66918226853062)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ReportBoolean(expression=CompareDuration(left=DurationFromDouble(doubleExpression=ConstantDouble(value=-30.403910421170877)), right=ConstantDuration(value="15.012191997".toDuration()))), Spawn(body=listOf(ReportDuration(expression=ConstantDuration(value="-01:23.655710815".toDuration())), ReportInt(expression=ConstantInt(value=-11)), Spawn(body=listOf(SaveDuration(expression=DurationFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=68)))), RestartTimer(index=ReadCounter(indexExpression=ConstantInt(value=83))), IncrementCounter(index=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=-42))), amount=ConstantInt(value=87)), SetSwitch(index=ConstantInt(value=-75), value=ConstantBoolean(value=true)), SetCounter(index=ReadCounter(indexExpression=ConstantInt(value=-13)), value=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=53.131808960388526)))), IncreaseSlope(index=SubtractInts(left=ReadSavedInt, right=AddInts(left=ConstantInt(value=-25), right=ReadCounter(indexExpression=ReadCounter(indexExpression=ReadSavedInt)))), amount=SubtractDoubles(left=ConstantDouble(value=-43.54241878891485), right=ReadIntegral(indexExpression=ConstantInt(value=-42)))), SaveInt(expression=ConstantInt(value=50)), SaveDouble(expression=SubtractDoubles(left=ReadIntegral(indexExpression=ReadSavedInt), right=ConstantDouble(value=-69.24985518840387))), SaveInt(expression=ConstantInt(value=79)), SaveBoolean(expression=ConstantBoolean(value=true)))), SaveBoolean(expression=ConstantBoolean(value=false)), SetCounter(index=SubtractInts(left=ConstantInt(value=-67), right=ConstantInt(value=67)), value=ReadSavedInt), Await(condition=ConstantBooleanResource(value=CompareInt(left=ConstantInt(value=7), right=AddInts(left=ConstantInt(value=56), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=74))))))), ReportInt(expression=ReadSavedInt), SetCounter(index=AddInts(left=AddInts(left=ConstantInt(value=-71), right=ReadSavedInt), right=ReadSavedInt), value=ReadSavedInt), Await(condition=ConstantBooleanResource(value=Or(left=CompareDuration(left=ReadSavedDuration, right=ReadTimer(indexExpression=ReadSavedInt)), right=CompareInt(left=ConstantInt(value=64), right=ConstantInt(value=-20))))), IncrementCounter(index=ReadSavedInt, amount=ReadSavedInt))), Spawn(body=listOf(ResumeTimer(index=IntFromDouble(doubleExpression=ConstantDouble(value=-57.37081165855875))), ReportInt(expression=ConstantInt(value=-74)), SaveDuration(expression=ConstantDuration(value="56.683585911".toDuration())), SaveInt(expression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=89.69791547125959))))), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=78))), SaveInt(expression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ReadCounter(indexExpression=ReadCounter(indexExpression=ConstantInt(value=-33)))))), SaveBoolean(expression=ConstantBoolean(value=false)), Await(condition=Switch(indexExpression=ReadCounter(indexExpression=ConstantInt(value=5)))), ReportInt(expression=ConstantInt(value=-48)), SaveBoolean(expression=ConstantBoolean(value=false)))), SaveDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=-31.954931854747514))), SaveDuration(expression=ConstantDuration(value="-43.538467473".toDuration())), SaveDouble(expression=DoubleFromInt(intExpression=ConstantInt(value=-6))), Await(condition=ConstantBooleanResource(value=Or(left=And(left=Not(expression=Not(expression=ReadSwitch(indexExpression=ConstantInt(value=-86)))), right=CompareDuration(left=ConstantDuration(value="-36.808224029".toDuration()), right=ConstantDuration(value="40.086262551".toDuration()))), right=ConstantBoolean(value=false))))))),
                GroundedActivity(Instant.parse("2025-01-02T21:38:34.616233Z"), Name("713163819373"), BlockActivity(statements=listOf(SaveInt(expression=ReadSavedInt), SetCounter(index=IntFromDouble(doubleExpression=ConstantDouble(value=-31.735557667318417)), value=SubtractInts(left=ConstantInt(value=54), right=ConstantInt(value=13))), PauseTimer(index=ConstantInt(value=-4)), ReportDouble(expression=ConstantDouble(value=2.801223160553306)), SetCounter(index=ConstantInt(value=56), value=ConstantInt(value=-24)), SaveDouble(expression=ConstantDouble(value=36.834389398613155)), SaveBoolean(expression=ConstantBoolean(value=false)), IncreaseSlope(index=ConstantInt(value=-70), amount=ConstantDouble(value=60.11684015755438)), Await(condition=Switch(indexExpression=ConstantInt(value=54))), Await(condition=CompareIntResource(left=Counter(indexExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=31.15521729846722), right=ConstantDouble(value=-23.976199872015627)))), right=Counter(indexExpression=ConstantInt(value=-49))))))),
                GroundedActivity(Instant.parse("2025-01-02T17:42:11.234916Z"), Name("242927517557"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=And(left=ConstantBoolean(value=false), right=ConstantBoolean(value=true)))), ReportDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=-43.387853423428055))), SetSwitch(index=SubtractInts(left=ConstantInt(value=-65), right=AddInts(left=ConstantInt(value=-31), right=ReadSavedInt)), value=CompareDouble(left=ConstantDouble(value=-91.33492741091374), right=DoubleFromInt(intExpression=ConstantInt(value=-48)))), SetSlope(index=ConstantInt(value=34), value=DoubleFromInt(intExpression=AddInts(left=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=9.270892858619106), right=ConstantDouble(value=-69.4041460029446))), right=ConstantInt(value=23)))), IncrementCounter(index=ReadSavedInt, amount=ReadSavedInt), ReportBoolean(expression=ConstantBoolean(value=true)), ReportDouble(expression=ConstantDouble(value=-40.115994241722234)), SetSwitch(index=ConstantInt(value=-46), value=CompareDouble(left=ReadSlope(indexExpression=ReadCounter(indexExpression=ReadSavedInt)), right=ConstantDouble(value=28.638646497552685))), ResetTimer(index=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=19)))), ReportDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=35.58307996247416)))))),
                GroundedActivity(Instant.parse("2025-01-02T15:55:19.893532Z"), Name("802313230759"), BlockActivity(statements=listOf(SetSwitch(index=ConstantInt(value=-64), value=Not(expression=ReadSwitch(indexExpression=AddInts(left=ReadSavedInt, right=SubtractInts(left=ConstantInt(value=22), right=SubtractInts(left=ConstantInt(value=58), right=ReadSavedInt)))))), SaveDouble(expression=ConstantDouble(value=40.34281503713592)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ReportDouble(expression=ReadSavedDouble), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ReportDouble(expression=ConstantDouble(value=-6.0466082076065675)), Await(condition=ConstantBooleanResource(value=CompareDouble(left=ConstantDouble(value=-3.142044918013397), right=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-16.560965917739168)))))), Await(condition=CompareDoubleResource(left=ConstantDoubleResource(value=ConstantDouble(value=25.14559462046742)), right=AddDoubleResources(left=ConstantDoubleResource(value=DoubleFromInt(intExpression=ConstantInt(value=-45))), right=ConstantDoubleResource(value=SubtractDoubles(left=ConstantDouble(value=13.674094345741963), right=ConstantDouble(value=-0.933531935044158)))))), ReportBoolean(expression=CompareInt(left=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=39.87868838623234)), right=AddInts(left=ConstantInt(value=-90), right=AddInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=49)), right=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=-97)))))), right=ConstantInt(value=67))), Await(condition=ConstantBooleanResource(value=And(left=ConstantBoolean(value=false), right=CompareDouble(left=SubtractDoubles(left=ConstantDouble(value=-2.9407354038215345), right=ConstantDouble(value=-92.05350325391886)), right=ConstantDouble(value=91.4323002239943)))))))),
                GroundedActivity(Instant.parse("2025-01-02T12:44:33.518901Z"), Name("757558613893"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=73)), SetSwitch(index=ConstantInt(value=-1), value=CompareDouble(left=ConstantDouble(value=-96.18144607106953), right=SubtractDoubles(left=ConstantDouble(value=29.816627442742373), right=ReadSavedDouble))), IncrementCounter(index=AddInts(left=SubtractInts(left=ConstantInt(value=-5), right=ConstantInt(value=29)), right=ConstantInt(value=74)), amount=ConstantInt(value=-49)), ToggleSwitch(index=ReadCounter(indexExpression=ConstantInt(value=9))), IncreaseSlope(index=ReadSavedInt, amount=AddDoubles(left=ConstantDouble(value=27.412795825091635), right=ConstantDouble(value=28.64472507010646))), IncrementCounter(index=SubtractInts(left=IntFromDouble(doubleExpression=ReadSlope(indexExpression=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=86.51808675304804)), right=ConstantInt(value=86)))), right=ConstantInt(value=-72)), amount=ReadSavedInt), PauseTimer(index=ConstantInt(value=-89)), ToggleSwitch(index=SubtractInts(left=ConstantInt(value=-84), right=ConstantInt(value=69))), SetCounter(index=ConstantInt(value=-73), value=ReadCounter(indexExpression=ReadSavedInt)), ReportInt(expression=ConstantInt(value=-96))))),
                GroundedActivity(Instant.parse("2025-01-02T16:39:29.601830Z"), Name("114024713177"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=-66)), IncreaseSlope(index=SubtractInts(left=IntFromDouble(doubleExpression=ReadSavedDouble), right=ConstantInt(value=38)), amount=ReadSavedDouble), SetCounter(index=SubtractInts(left=ConstantInt(value=97), right=IntFromDouble(doubleExpression=ConstantDouble(value=-66.02743732144936))), value=AddInts(left=ConstantInt(value=75), right=SubtractInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=28)), right=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=-98.89790651905959), right=AddDoubles(left=ReadSlope(indexExpression=ReadSavedInt), right=ConstantDouble(value=34.82236918319552))))))), ReportInt(expression=AddInts(left=ConstantInt(value=21), right=ConstantInt(value=-54))), ResetTimer(index=ReadCounter(indexExpression=ConstantInt(value=84))), ToggleSwitch(index=ConstantInt(value=94)), SetSlope(index=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=52), right=ReadSavedInt)), value=ReadSavedDouble), Await(condition=CompareTimerResource(left=Timer(indexExpression=ConstantInt(value=-28)), right=ConstantTimerResource(value=ReadTimer(indexExpression=ConstantInt(value=-36))))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), Await(condition=AndResource(left=ConstantBooleanResource(value=And(left=ConstantBoolean(value=false), right=ConstantBoolean(value=true))), right=ConstantBooleanResource(value=ConstantBoolean(value=true))))))),
                GroundedActivity(Instant.parse("2025-01-02T21:32:08.861617Z"), Name("571513881841"), BlockActivity(statements=listOf(IncreaseSlope(index=ConstantInt(value=-16), amount=ReadIntegral(indexExpression=SubtractInts(left=ConstantInt(value=11), right=ConstantInt(value=12)))), ResetTimer(index=ConstantInt(value=-32)), ReportDouble(expression=ConstantDouble(value=-84.17144016504352)), SaveInt(expression=ReadCounter(indexExpression=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=-55.90485214957057), right=DoubleFromInt(intExpression=ConstantInt(value=92)))))), ReportInt(expression=ReadCounter(indexExpression=ConstantInt(value=45))), ReportDuration(expression=ConstantDuration(value="27.013225932".toDuration())), ReportDouble(expression=ConstantDouble(value=-1.8460863837472772)), ReportInt(expression=ConstantInt(value=40)), SaveDuration(expression=ReadSavedDuration), SetSwitch(index=ConstantInt(value=-31), value=ConstantBoolean(value=false))))),
                GroundedActivity(Instant.parse("2025-01-02T14:03:44.782721Z"), Name("680255665071"), BlockActivity(statements=listOf(Await(condition=NotResource(expression=OrResource(left=ConstantBooleanResource(value=CompareDuration(left=ConstantDuration(value="-09.640437846".toDuration()), right=ReadTimer(indexExpression=ConstantInt(value=61)))), right=CompareDoubleResource(left=Slope(indexExpression=ReadSavedInt), right=SubtractDoubleResources(left=SubtractDoubleResources(left=ConstantDoubleResource(value=ReadSavedDouble), right=ConstantDoubleResource(value=ReadSavedDouble)), right=ConstantDoubleResource(value=SubtractDoubles(left=ConstantDouble(value=34.01724023383997), right=ConstantDouble(value=37.875635472811865)))))))), SaveInt(expression=AddInts(left=ConstantInt(value=20), right=ConstantInt(value=-13))), SaveBoolean(expression=ConstantBoolean(value=false)), Await(condition=CompareTimerResource(left=ConstantTimerResource(value=ConstantDuration(value="38.928722682".toDuration())), right=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ReadSlope(indexExpression=AddInts(left=ConstantInt(value=7), right=ConstantInt(value=91))))))), Await(condition=AndResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=ConstantBooleanResource(value=ConstantBoolean(value=true)))), SetSlope(index=IntFromDouble(doubleExpression=ConstantDouble(value=77.61852316539844)), value=ConstantDouble(value=1.05947801185539)), RestartTimer(index=ReadSavedInt), SaveDouble(expression=DoubleFromInt(intExpression=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=87), right=ConstantInt(value=6))))), SaveDouble(expression=ConstantDouble(value=-89.22338627026379)), Await(condition=AndResource(left=ConstantBooleanResource(value=ReadSwitch(indexExpression=ReadSavedInt)), right=ConstantBooleanResource(value=ConstantBoolean(value=false))))))),
                GroundedActivity(Instant.parse("2025-01-02T20:15:47.431712Z"), Name("990763531528"), BlockActivity(statements=listOf(ReportDouble(expression=ReadSavedDouble), SetSlope(index=AddInts(left=ConstantInt(value=-82), right=ConstantInt(value=39)), value=ReadIntegral(indexExpression=ConstantInt(value=72))), ReportDouble(expression=AddDoubles(left=ConstantDouble(value=23.31709347838307), right=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=83.9674058376585))))), SaveInt(expression=ConstantInt(value=-44)), SaveBoolean(expression=CompareDuration(left=ConstantDuration(value="01:30.457977946".toDuration()), right=ConstantDuration(value="-01:39.052238609".toDuration()))), Await(condition=Switch(indexExpression=ConstantInt(value=-88))), SaveDuration(expression=ConstantDuration(value="09.921334509".toDuration())), ReportDouble(expression=ConstantDouble(value=66.39195526926608)), Await(condition=CompareDoubleResource(left=AddDoubleResources(left=ConstantDoubleResource(value=ConstantDouble(value=99.34429859203235)), right=SubtractDoubleResources(left=SubtractDoubleResources(left=ConstantDoubleResource(value=ReadIntegral(indexExpression=ConstantInt(value=90))), right=SubtractDoubleResources(left=SubtractDoubleResources(left=AddDoubleResources(left=ConstantDoubleResource(value=ReadIntegral(indexExpression=AddInts(left=ReadSavedInt, right=AddInts(left=ConstantInt(value=56), right=ReadSavedInt)))), right=ConstantDoubleResource(value=SubtractDoubles(left=ConstantDouble(value=94.47399441419174), right=ConstantDouble(value=81.51694927684113)))), right=ConstantDoubleResource(value=SubtractDoubles(left=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=26.461610233897375))), right=ConstantDouble(value=-36.47560444309414)))), right=AddDoubleResources(left=ConstantDoubleResource(value=AddDoubles(left=ConstantDouble(value=91.10091719858255), right=ConstantDouble(value=-59.89788973726047))), right=SubtractDoubleResources(left=ConstantDoubleResource(value=ConstantDouble(value=-4.421048491470543)), right=ConstantDoubleResource(value=ConstantDouble(value=-49.121505854774924)))))), right=SubtractDoubleResources(left=AddDoubleResources(left=AddDoubleResources(left=AddDoubleResources(left=SubtractDoubleResources(left=Slope(indexExpression=SubtractInts(left=SubtractInts(left=ConstantInt(value=3), right=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=-6)))), right=ConstantInt(value=98))), right=ConstantDoubleResource(value=ReadSlope(indexExpression=SubtractInts(left=ConstantInt(value=-5), right=ConstantInt(value=-17))))), right=ConstantDoubleResource(value=DoubleFromInt(intExpression=ConstantInt(value=-57)))), right=Slope(indexExpression=AddInts(left=ConstantInt(value=-38), right=AddInts(left=ConstantInt(value=-69), right=SubtractInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=AddInts(left=AddInts(left=SubtractInts(left=SubtractInts(left=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=45.70233078798532)), right=ConstantInt(value=73)), right=SubtractInts(left=ConstantInt(value=5), right=SubtractInts(left=ConstantInt(value=-91), right=ReadSavedInt))), right=ConstantInt(value=50)), right=ConstantInt(value=22)), right=AddInts(left=ConstantInt(value=3), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt)))))), right=ReadCounter(indexExpression=ConstantInt(value=-53))))))), right=ConstantDoubleResource(value=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=91.00020067066166))))), right=SubtractDoubleResources(left=ConstantDoubleResource(value=ReadSavedDouble), right=SubtractDoubleResources(left=Slope(indexExpression=SubtractInts(left=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ReadSavedInt)), right=ConstantInt(value=-35))), right=SubtractDoubleResources(left=SubtractDoubleResources(left=Slope(indexExpression=ConstantInt(value=12)), right=ConstantDoubleResource(value=ReadIntegral(indexExpression=AddInts(left=ConstantInt(value=-62), right=ConstantInt(value=68))))), right=Slope(indexExpression=SubtractInts(left=AddInts(left=SubtractInts(left=SubtractInts(left=ConstantInt(value=-39), right=SubtractInts(left=ConstantInt(value=1), right=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=-33))))), right=ConstantInt(value=-59)), right=ReadCounter(indexExpression=ReadSavedInt)), right=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-42)))))))))), right=ConstantDoubleResource(value=AddDoubles(left=ConstantDouble(value=98.1220873634158), right=AddDoubles(left=ConstantDouble(value=-60.93019032725764), right=AddDoubles(left=ConstantDouble(value=-37.243360554923925), right=ConstantDouble(value=36.440834320925035))))))), SaveInt(expression=ConstantInt(value=82))))),
                GroundedActivity(Instant.parse("2025-01-02T16:10:22.975337Z"), Name("735968309665"), BlockActivity(statements=listOf(ReportBoolean(expression=ConstantBoolean(value=false)), ResetTimer(index=IntFromDouble(doubleExpression=ReadSlope(indexExpression=AddInts(left=ConstantInt(value=83), right=ConstantInt(value=0))))), SaveBoolean(expression=Not(expression=ReadSwitch(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=11.323089393992888))))), Await(condition=CompareTimerResource(left=SubtractTimerResources(left=Timer(indexExpression=IntFromDouble(doubleExpression=AddDoubles(left=ReadSlope(indexExpression=SubtractInts(left=ConstantInt(value=-51), right=ReadSavedInt)), right=AddDoubles(left=AddDoubles(left=SubtractDoubles(left=ConstantDouble(value=-7.011255675915521), right=ConstantDouble(value=-87.53793265257687)), right=ConstantDouble(value=38.708816477850604)), right=ConstantDouble(value=56.11793226105482))))), right=AddTimerResources(left=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="01:22.057186677".toDuration())), right=ConstantTimerResource(value=ReadTimer(indexExpression=SubtractInts(left=ConstantInt(value=92), right=ReadCounter(indexExpression=ReadSavedInt))))), right=SubtractTimerResources(left=AddTimerResources(left=SubtractTimerResources(left=SubtractTimerResources(left=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ConstantDouble(value=61.828083902749086))), right=Timer(indexExpression=ConstantInt(value=13))), right=Timer(indexExpression=ConstantInt(value=-16))), right=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="48.515058568".toDuration())), right=ConstantTimerResource(value=ConstantDuration(value="-23.419982828".toDuration())))), right=Timer(indexExpression=ConstantInt(value=-24))))), right=SubtractTimerResources(left=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="01:27.814690312".toDuration())), right=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="01:16.950364428".toDuration())), right=ConstantTimerResource(value=ConstantDuration(value="01:07.513541762".toDuration())))), right=ConstantTimerResource(value=ReadSavedDuration)))), ReportDouble(expression=ReadSavedDouble), Await(condition=Switch(indexExpression=ConstantInt(value=45))), SaveInt(expression=ConstantInt(value=41)), IncreaseSlope(index=ConstantInt(value=-22), amount=AddDoubles(left=ConstantDouble(value=71.2967243082789), right=ReadSavedDouble)), Await(condition=ConstantBooleanResource(value=Not(expression=ConstantBoolean(value=false)))), ReportInt(expression=ConstantInt(value=28))))),
                GroundedActivity(Instant.parse("2025-01-02T20:01:45.772337Z"), Name("377222919038"), BlockActivity(statements=listOf(RestartTimer(index=ReadCounter(indexExpression=ReadSavedInt)), ReportDouble(expression=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-96.00815555963695)))), Await(condition=ConstantBooleanResource(value=CompareInt(left=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=59), right=ReadCounter(indexExpression=ConstantInt(value=-98)))), right=ConstantInt(value=6)))), SaveBoolean(expression=ConstantBoolean(value=false)), SaveDouble(expression=ConstantDouble(value=53.967785465568056)), IncreaseSlope(index=IntFromDouble(doubleExpression=ConstantDouble(value=39.0860472610118)), amount=ConstantDouble(value=-78.92577244152632)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveDouble(expression=ReadSavedDouble), SaveDuration(expression=ConstantDuration(value="38.234815873".toDuration())), ReportDouble(expression=ConstantDouble(value=-88.07003494097538))))),
                GroundedActivity(Instant.parse("2025-01-02T17:55:02.532771Z"), Name("897563646777"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=-52)), IncreaseSlope(index=IntFromDouble(doubleExpression=ReadSavedDouble), amount=ConstantDouble(value=72.90157396852433)), SetCounter(index=ReadCounter(indexExpression=ReadSavedInt), value=ConstantInt(value=58)), SetSwitch(index=IntFromDouble(doubleExpression=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=-10)), right=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=20)))))), value=ReadSwitch(indexExpression=ReadCounter(indexExpression=ConstantInt(value=-59)))), SetCounter(index=SubtractInts(left=ConstantInt(value=-97), right=ReadSavedInt), value=ReadSavedInt), ReportDuration(expression=ReadTimer(indexExpression=SubtractInts(left=ConstantInt(value=-85), right=ConstantInt(value=-48)))), Await(condition=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=ReadIntegral(indexExpression=ConstantInt(value=7))), right=ConstantPolynomialResourceExpression(value=ConstantDouble(value=95.02878431014526)))), ToggleSwitch(index=ConstantInt(value=6)), Await(condition=ConstantBooleanResource(value=And(left=Not(expression=ConstantBoolean(value=true)), right=ReadSwitch(indexExpression=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=80))))))), ReportBoolean(expression=ConstantBoolean(value=true))))),
                GroundedActivity(Instant.parse("2025-01-02T18:50:40.135777Z"), Name("137008184992"), BlockActivity(statements=listOf(ReportDuration(expression=ConstantDuration(value="-55.878413065".toDuration())), SaveBoolean(expression=Or(left=And(left=ReadSavedBoolean, right=CompareDuration(left=ConstantDuration(value="-01:37.725216767".toDuration()), right=DurationFromDouble(doubleExpression=ReadSlope(indexExpression=SubtractInts(left=ConstantInt(value=-58), right=ConstantInt(value=86)))))), right=ConstantBoolean(value=true))), SetSlope(index=ConstantInt(value=-86), value=ReadSavedDouble), SaveBoolean(expression=ReadSavedBoolean), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=95))), Await(condition=NotResource(expression=OrResource(left=ConstantBooleanResource(value=And(left=CompareDuration(left=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=43))))), right=ConstantDuration(value="01:18.674361697".toDuration())), right=CompareInt(left=ConstantInt(value=-6), right=ReadSavedInt))), right=CompareTimerResource(left=SubtractTimerResources(left=AddTimerResources(left=AddTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="-40.921130787".toDuration())), right=ConstantTimerResource(value=ConstantDuration(value="22.200661446".toDuration()))), right=ConstantTimerResource(value=ConstantDuration(value="01:38.852627908".toDuration()))), right=AddTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="-01:21.759697182".toDuration())), right=ConstantTimerResource(value=ReadTimer(indexExpression=AddInts(left=ConstantInt(value=-76), right=ConstantInt(value=-85)))))), right=Timer(indexExpression=ConstantInt(value=62)))))), ReportDouble(expression=ReadSavedDouble), SaveBoolean(expression=CompareDuration(left=ConstantDuration(value="-01:09.732159931".toDuration()), right=DurationFromDouble(doubleExpression=ConstantDouble(value=43.90451709296454)))), SetSlope(index=ReadSavedInt, value=ConstantDouble(value=30.83303932383552)), ReportInt(expression=ConstantInt(value=27))))),
                GroundedActivity(Instant.parse("2025-01-02T16:23:18.039125Z"), Name("478797133161"), BlockActivity(statements=listOf(SetSlope(index=IntFromDouble(doubleExpression=ConstantDouble(value=-48.57955525617372)), value=SubtractDoubles(left=ConstantDouble(value=73.89886653473138), right=ConstantDouble(value=65.4504128373926))), SaveBoolean(expression=ConstantBoolean(value=false)), SaveDuration(expression=ConstantDuration(value="26.264886052".toDuration())), Await(condition=ConstantBooleanResource(value=And(left=ConstantBoolean(value=true), right=ReadSwitch(indexExpression=ConstantInt(value=-1))))), RestartTimer(index=ConstantInt(value=-69)), SetCounter(index=ConstantInt(value=79), value=ReadSavedInt), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), Await(condition=ComparePolynomialResource(left=PolynomialFromDoubleResource(doubleResourceExpression=ConstantDoubleResource(value=ConstantDouble(value=71.57194605844612))), right=ConstantPolynomialResourceExpression(value=ConstantDouble(value=41.73724928671152)))), Await(condition=ConstantBooleanResource(value=Not(expression=ConstantBoolean(value=false)))), Spawn(body=listOf(ResetTimer(index=ReadSavedInt), SaveInt(expression=ConstantInt(value=62)), Spawn(body=listOf(ReportDuration(expression=DurationFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=-82.16713792521055), right=DoubleFromInt(intExpression=ReadSavedInt)))), ResumeTimer(index=SubtractInts(left=SubtractInts(left=SubtractInts(left=ConstantInt(value=24), right=AddInts(left=ConstantInt(value=55), right=AddInts(left=ConstantInt(value=-2), right=IntFromDouble(doubleExpression=ConstantDouble(value=-77.25511275579476))))), right=IntFromDouble(doubleExpression=ConstantDouble(value=-28.146131527667336))), right=ConstantInt(value=-96))), SaveDouble(expression=ConstantDouble(value=-3.620221619638798)), Await(condition=CompareIntResource(left=Counter(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ReadSavedInt))), right=AddIntResources(left=Counter(indexExpression=ConstantInt(value=-30)), right=AddIntResources(left=ConstantIntResource(value=SubtractInts(left=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=34)), right=IntFromDouble(doubleExpression=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-49)), right=ConstantDouble(value=-12.430523948074466)))))), right=ConstantInt(value=37))), right=Counter(indexExpression=ConstantInt(value=8)))))), SaveBoolean(expression=ReadSavedBoolean), IncreaseSlope(index=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=-18.452607546439026), right=DoubleFromInt(intExpression=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=-75), right=ReadCounter(indexExpression=ReadSavedInt)))))), amount=ConstantDouble(value=-68.02056650420025)), SaveBoolean(expression=ConstantBoolean(value=false)), ReportBoolean(expression=ConstantBoolean(value=false)), ReportInt(expression=IntFromDouble(doubleExpression=ConstantDouble(value=9.005377540229006))), ResumeTimer(index=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-19)), right=ConstantInt(value=30))))), SaveDouble(expression=SubtractDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-19)), right=ReadSavedDouble)), ReportBoolean(expression=ConstantBoolean(value=true)), ReportBoolean(expression=CompareDuration(left=ConstantDuration(value="-21.413220198".toDuration()), right=ConstantDuration(value="-15.857603837".toDuration()))), SetSwitch(index=SubtractInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=-71)), right=AddInts(left=ReadCounter(indexExpression=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=94), right=ReadSavedInt))), right=ConstantInt(value=-55))), value=ConstantBoolean(value=true)), SaveInt(expression=ConstantInt(value=-48)), ReportInt(expression=ConstantInt(value=29)), ReportDuration(expression=ConstantDuration(value="01:37.371373008".toDuration()))))))),
                GroundedActivity(Instant.parse("2025-01-02T15:21:04.386838Z"), Name("756745505917"), BlockActivity(statements=listOf(PauseTimer(index=ReadSavedInt), SetCounter(index=ConstantInt(value=86), value=IntFromDouble(doubleExpression=ConstantDouble(value=-31.60574375605063))), Await(condition=ConstantBooleanResource(value=And(left=ConstantBoolean(value=false), right=ReadSavedBoolean))), ReportDouble(expression=ReadSavedDouble), SetSlope(index=ConstantInt(value=-98), value=ReadSavedDouble), PauseTimer(index=SubtractInts(left=ConstantInt(value=76), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-15.856667318131784)))))), IncreaseSlope(index=SubtractInts(left=ConstantInt(value=-54), right=ConstantInt(value=-89)), amount=SubtractDoubles(left=ConstantDouble(value=-62.095179909123985), right=ReadSlope(indexExpression=ConstantInt(value=16)))), SetSlope(index=ConstantInt(value=-41), value=ConstantDouble(value=83.23625938557268)), ReportBoolean(expression=ConstantBoolean(value=false)), Await(condition=CompareTimerResource(left=ConstantTimerResource(value=ConstantDuration(value="01:31.072445120".toDuration())), right=ConstantTimerResource(value=ReadTimer(indexExpression=ConstantInt(value=75)))))))),
                GroundedActivity(Instant.parse("2025-01-02T17:48:08.695262Z"), Name("699143295477"), BlockActivity(statements=listOf(IncreaseSlope(index=ConstantInt(value=79), amount=AddDoubles(left=SubtractDoubles(left=ConstantDouble(value=4.086747653865459), right=ConstantDouble(value=24.764034893011825)), right=ConstantDouble(value=67.9817878274855))), SaveBoolean(expression=ConstantBoolean(value=false)), ReportDouble(expression=ReadSavedDouble), SetCounter(index=ConstantInt(value=-57), value=SubtractInts(left=ReadSavedInt, right=ReadSavedInt)), Await(condition=ConstantBooleanResource(value=CompareDuration(left=ReadSavedDuration, right=ReadSavedDuration))), ReportDuration(expression=ConstantDuration(value="59.584538434".toDuration())), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveDuration(expression=ConstantDuration(value="-01:37.963228731".toDuration())), SetSwitch(index=ConstantInt(value=-68), value=CompareDouble(left=AddDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=-52)), right=ConstantDouble(value=-30.308996277242528)), right=ConstantDouble(value=59.767101084491))), Spawn(body=listOf(Await(condition=CompareTimerResource(left=AddTimerResources(left=ConstantTimerResource(value=ReadTimer(indexExpression=ConstantInt(value=-72))), right=AddTimerResources(left=Timer(indexExpression=ReadSavedInt), right=ConstantTimerResource(value=ConstantDuration(value="31.605085157".toDuration())))), right=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="-09.237914196".toDuration())), right=ConstantTimerResource(value=ReadSavedDuration)))), ReportInt(expression=ConstantInt(value=98)), ReportDouble(expression=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=-28)), right=ConstantDouble(value=-21.47699226242483))), SaveInt(expression=ConstantInt(value=-60)), SetSlope(index=ConstantInt(value=61), value=ConstantDouble(value=-61.18934842832131)), ToggleSwitch(index=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=15))), ReportDouble(expression=ConstantDouble(value=-40.10426676123369)), ReportInt(expression=ReadSavedInt), IncrementCounter(index=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=41), right=ConstantInt(value=-73))), amount=AddInts(left=ConstantInt(value=57), right=ConstantInt(value=35))), SetCounter(index=ConstantInt(value=-90), value=IntFromDouble(doubleExpression=ConstantDouble(value=79.05277856545311)))))))),
                GroundedActivity(Instant.parse("2025-01-02T21:36:49.012349Z"), Name("845048417654"), BlockActivity(statements=listOf(Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SaveDuration(expression=ConstantDuration(value="01:39.150254659".toDuration())), IncrementCounter(index=ConstantInt(value=83), amount=ConstantInt(value=15)), Spawn(body=listOf(PauseTimer(index=ConstantInt(value=-2)), Await(condition=ConstantBooleanResource(value=ReadSwitch(indexExpression=ConstantInt(value=-26)))), RestartTimer(index=SubtractInts(left=ConstantInt(value=-96), right=ConstantInt(value=-93))), ToggleSwitch(index=ReadCounter(indexExpression=AddInts(left=ConstantInt(value=-30), right=ReadCounter(indexExpression=ConstantInt(value=-16))))), SaveDuration(expression=ReadTimer(indexExpression=ReadSavedInt)), ReportDuration(expression=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=34)))), SaveDuration(expression=ConstantDuration(value="-10.451691715".toDuration())), Spawn(body=listOf(SetSlope(index=ReadSavedInt, value=DoubleFromInt(intExpression=ReadCounter(indexExpression=AddInts(left=ReadSavedInt, right=ConstantInt(value=-58))))), SetSwitch(index=ConstantInt(value=-17), value=ConstantBoolean(value=false)), IncreaseSlope(index=ConstantInt(value=18), amount=ConstantDouble(value=-93.47334767889815)), SaveInt(expression=ConstantInt(value=-86)), Await(condition=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=true)))), ReportDouble(expression=ConstantDouble(value=-83.11374843434969)), PauseTimer(index=ConstantInt(value=-51)), ReportDuration(expression=ConstantDuration(value="-05.845276070".toDuration())), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), Await(condition=CompareTimerResource(left=AddTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="55.022388817".toDuration())), right=AddTimerResources(left=SubtractTimerResources(left=AddTimerResources(left=ConstantTimerResource(value=ReadTimer(indexExpression=SubtractInts(left=AddInts(left=AddInts(left=ReadCounter(indexExpression=AddInts(left=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=68))), right=ReadSavedInt)), right=ConstantInt(value=-68)), right=ConstantInt(value=97)), right=ConstantInt(value=-20)))), right=SubtractTimerResources(left=ConstantTimerResource(value=ConstantDuration(value="-01:19.098821046".toDuration())), right=Timer(indexExpression=SubtractInts(left=ConstantInt(value=61), right=AddInts(left=AddInts(left=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=-30))), right=AddInts(left=SubtractInts(left=SubtractInts(left=ConstantInt(value=40), right=ConstantInt(value=-13)), right=ConstantInt(value=-61)), right=AddInts(left=ConstantInt(value=-75), right=ConstantInt(value=23)))), right=IntFromDouble(doubleExpression=ReadSavedDouble)))))), right=Timer(indexExpression=ReadCounter(indexExpression=ConstantInt(value=46)))), right=Timer(indexExpression=ConstantInt(value=13)))), right=ConstantTimerResource(value=ReadSavedDuration))))), ResetTimer(index=AddInts(left=ConstantInt(value=-24), right=ReadSavedInt)), Await(condition=CompareIntResource(left=SubtractIntResources(left=ConstantIntResource(value=IntFromDouble(doubleExpression=ReadSavedDouble)), right=AddIntResources(left=AddIntResources(left=AddIntResources(left=AddIntResources(left=ConstantIntResource(value=ConstantInt(value=28)), right=ConstantIntResource(value=ConstantInt(value=14))), right=ConstantIntResource(value=ConstantInt(value=-82))), right=AddIntResources(left=ConstantIntResource(value=AddInts(left=ConstantInt(value=-65), right=ConstantInt(value=-34))), right=ConstantIntResource(value=ConstantInt(value=95)))), right=ConstantIntResource(value=ConstantInt(value=70)))), right=ConstantIntResource(value=ConstantInt(value=-96)))))), ReportDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), ReportDouble(expression=ReadIntegral(indexExpression=ReadSavedInt)), IncrementCounter(index=ConstantInt(value=-26), amount=AddInts(left=ReadSavedInt, right=ReadCounter(indexExpression=ConstantInt(value=85)))), RestartTimer(index=ConstantInt(value=-10)), SaveDouble(expression=ReadIntegral(indexExpression=ConstantInt(value=-31))), SetSlope(index=ReadCounter(indexExpression=ConstantInt(value=3)), value=AddDoubles(left=SubtractDoubles(left=ConstantDouble(value=66.19997675335944), right=ConstantDouble(value=-47.70149934268528)), right=ReadSlope(indexExpression=ReadSavedInt)))))),
                GroundedActivity(Instant.parse("2025-01-02T21:12:44.878778Z"), Name("179560179788"), BlockActivity(statements=listOf(SetCounter(index=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=63))), value=ReadCounter(indexExpression=ConstantInt(value=-86))), SaveDuration(expression=ReadSavedDuration), ReportBoolean(expression=CompareDuration(left=ConstantDuration(value="27.242566669".toDuration()), right=DurationFromDouble(doubleExpression=ConstantDouble(value=-98.3414502761097)))), ReportBoolean(expression=CompareInt(left=ReadSavedInt, right=ConstantInt(value=85))), ReportBoolean(expression=ReadSavedBoolean), ReportDuration(expression=ConstantDuration(value="01:25.521422546".toDuration())), SetSwitch(index=ReadCounter(indexExpression=ConstantInt(value=39)), value=ConstantBoolean(value=false)), SaveDuration(expression=ReadSavedDuration), ReportInt(expression=ReadCounter(indexExpression=ConstantInt(value=29))), SetSwitch(index=ConstantInt(value=-81), value=ConstantBoolean(value=false))))),
                GroundedActivity(Instant.parse("2025-01-02T16:09:26.263381Z"), Name("702485303332"), BlockActivity(statements=listOf(ReportDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=6.703429947347544))), Await(condition=OrResource(left=NotResource(expression=ConstantBooleanResource(value=ConstantBoolean(value=true))), right=OrResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=ConstantBooleanResource(value=And(left=Not(expression=Not(expression=ReadSavedBoolean)), right=Not(expression=CompareDuration(left=DurationFromDouble(doubleExpression=ReadSavedDouble), right=ConstantDuration(value="01:22.800222880".toDuration())))))))), ReportDuration(expression=DurationFromDouble(doubleExpression=ReadSlope(indexExpression=ReadCounter(indexExpression=ConstantInt(value=64))))), SaveDouble(expression=ConstantDouble(value=-89.42310242143516)), IncreaseSlope(index=SubtractInts(left=ConstantInt(value=28), right=ConstantInt(value=0)), amount=SubtractDoubles(left=AddDoubles(left=ConstantDouble(value=84.21475761810237), right=ConstantDouble(value=-73.69071985017717)), right=ConstantDouble(value=94.51469040699945))), IncrementCounter(index=SubtractInts(left=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=-34))), right=ConstantInt(value=-22)), amount=ConstantInt(value=32)), ReportBoolean(expression=ConstantBoolean(value=false)), Spawn(body=listOf(Await(condition=NotResource(expression=ConstantBooleanResource(value=And(left=ConstantBoolean(value=true), right=ReadSwitch(indexExpression=ConstantInt(value=-10)))))), ReportBoolean(expression=CompareInt(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=78)), right=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-23)))), Await(condition=Switch(indexExpression=SubtractInts(left=ConstantInt(value=17), right=ConstantInt(value=54)))), IncrementCounter(index=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=-82.13683330060672), right=ConstantDouble(value=86.68976508001015))), amount=ConstantInt(value=74)), SaveDuration(expression=ConstantDuration(value="-01:13.882964706".toDuration())), Spawn(body=listOf(ReportDuration(expression=ConstantDuration(value="-01:29.410587823".toDuration())), SaveDouble(expression=AddDoubles(left=DoubleFromInt(intExpression=ConstantInt(value=24)), right=ReadSavedDouble)), ToggleSwitch(index=ConstantInt(value=-64)), ResetTimer(index=ConstantInt(value=94)), Await(condition=AndResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=ConstantBooleanResource(value=ConstantBoolean(value=false)))), PauseTimer(index=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=92.67252948911607)), right=ConstantInt(value=-3))), IncreaseSlope(index=AddInts(left=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=9)), right=ConstantInt(value=-18)), right=ReadSavedInt), amount=ReadSlope(indexExpression=ConstantInt(value=-51))), Spawn(body=listOf(ReportBoolean(expression=ConstantBoolean(value=true)), IncrementCounter(index=ReadSavedInt, amount=ConstantInt(value=-40)), Await(condition=Switch(indexExpression=ConstantInt(value=82))), IncrementCounter(index=ConstantInt(value=-15), amount=ConstantInt(value=13)), SaveBoolean(expression=ConstantBoolean(value=true)), SaveInt(expression=ReadSavedInt), SaveDouble(expression=ConstantDouble(value=67.70488848775557)), Await(condition=ConstantBooleanResource(value=CompareInt(left=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=70))), right=ConstantInt(value=38)))), IncrementCounter(index=AddInts(left=ConstantInt(value=-18), right=ConstantInt(value=-92)), amount=ConstantInt(value=23)), ToggleSwitch(index=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=75.08736375288373)), right=SubtractInts(left=AddInts(left=ConstantInt(value=-33), right=SubtractInts(left=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=31)), right=IntFromDouble(doubleExpression=ReadSlope(indexExpression=SubtractInts(left=ConstantInt(value=-28), right=ConstantInt(value=-29))))), right=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-52)))), right=ConstantInt(value=-75)))))), ToggleSwitch(index=ConstantInt(value=70)), SaveDouble(expression=ReadSavedDouble))), SaveInt(expression=ConstantInt(value=3)), ReportDouble(expression=ReadSavedDouble), Await(condition=OrResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=OrResource(left=ConstantBooleanResource(value=Not(expression=Not(expression=ConstantBoolean(value=false)))), right=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=AddDoubles(left=ReadSavedDouble, right=ConstantDouble(value=-14.97597584538714))), right=ConstantPolynomialResourceExpression(value=SubtractDoubles(left=SubtractDoubles(left=ConstantDouble(value=-54.620325904018976), right=ReadSavedDouble), right=ConstantDouble(value=1.895073070877146))))))), ResumeTimer(index=ConstantInt(value=13)))), SaveDuration(expression=ConstantDuration(value="-34.946992312".toDuration())), SaveDouble(expression=ReadIntegral(indexExpression=ConstantInt(value=36)))))),
                GroundedActivity(Instant.parse("2025-01-03T02:20:03.377847Z"), Name("831718055369"), BlockActivity(statements=listOf(SaveBoolean(expression=CompareDouble(left=ConstantDouble(value=51.67381454210775), right=ReadSlope(indexExpression=ConstantInt(value=53)))), Await(condition=AndResource(left=ConstantBooleanResource(value=CompareInt(left=ConstantInt(value=39), right=ReadCounter(indexExpression=AddInts(left=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-66.93863918941202)), right=IntFromDouble(doubleExpression=ReadSlope(indexExpression=ConstantInt(value=-10)))))), right=ConstantInt(value=92))))), right=ConstantBooleanResource(value=ReadSavedBoolean))), IncrementCounter(index=ConstantInt(value=-96), amount=ConstantInt(value=44)), SaveBoolean(expression=ReadSwitch(indexExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-29)), right=ConstantInt(value=-51)))), SaveInt(expression=ConstantInt(value=-29)), ReportBoolean(expression=ConstantBoolean(value=true)), SetSwitch(index=ConstantInt(value=-90), value=ConstantBoolean(value=true)), ReportDuration(expression=ConstantDuration(value="-32.583185072".toDuration())), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=28))), ReportInt(expression=ConstantInt(value=-36))))),
                GroundedActivity(Instant.parse("2025-01-02T18:06:24.781590Z"), Name("706420677638"), BlockActivity(statements=listOf(IncreaseSlope(index=AddInts(left=ConstantInt(value=-7), right=SubtractInts(left=AddInts(left=ConstantInt(value=65), right=ConstantInt(value=-54)), right=ReadSavedInt)), amount=AddDoubles(left=DoubleFromInt(intExpression=ConstantInt(value=68)), right=ConstantDouble(value=-71.23550402352647))), Await(condition=ConstantBooleanResource(value=CompareDouble(left=ReadSlope(indexExpression=ConstantInt(value=-28)), right=ReadSlope(indexExpression=AddInts(left=ConstantInt(value=-12), right=ConstantInt(value=-13)))))), ResetTimer(index=ConstantInt(value=68)), SetCounter(index=ConstantInt(value=74), value=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-51.53931753582288)))), SaveDouble(expression=DoubleFromInt(intExpression=ReadSavedInt)), ReportBoolean(expression=CompareInt(left=SubtractInts(left=ConstantInt(value=-71), right=AddInts(left=AddInts(left=ConstantInt(value=30), right=ConstantInt(value=28)), right=SubtractInts(left=ConstantInt(value=84), right=SubtractInts(left=AddInts(left=ConstantInt(value=-34), right=ReadSavedInt), right=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=SubtractInts(left=ConstantInt(value=53), right=ConstantInt(value=36)))))))))), right=ConstantInt(value=-82))), Await(condition=AndResource(left=CompareDoubleResource(left=ConstantDoubleResource(value=ConstantDouble(value=84.38211874145625)), right=ConstantDoubleResource(value=ConstantDouble(value=5.267899750761202))), right=AndResource(left=AndResource(left=ConstantBooleanResource(value=ConstantBoolean(value=true)), right=ConstantBooleanResource(value=CompareDouble(left=ReadSavedDouble, right=ConstantDouble(value=-75.93976424738808)))), right=ConstantBooleanResource(value=CompareDouble(left=ConstantDouble(value=11.562277606159029), right=AddDoubles(left=ConstantDouble(value=66.89234390419156), right=DoubleFromInt(intExpression=ConstantInt(value=7)))))))), ResetTimer(index=ReadCounter(indexExpression=ConstantInt(value=79))), ToggleSwitch(index=AddInts(left=ConstantInt(value=-32), right=ConstantInt(value=-82))), SaveInt(expression=ConstantInt(value=-72))))),
                GroundedActivity(Instant.parse("2025-01-02T15:16:06.138984Z"), Name("627339548602"), BlockActivity(statements=listOf(ReportInt(expression=SubtractInts(left=ConstantInt(value=-3), right=AddInts(left=ConstantInt(value=62), right=AddInts(left=SubtractInts(left=ConstantInt(value=28), right=ConstantInt(value=98)), right=ReadCounter(indexExpression=ReadCounter(indexExpression=ConstantInt(value=82))))))), PauseTimer(index=ConstantInt(value=73)), ToggleSwitch(index=ConstantInt(value=27)), SaveInt(expression=SubtractInts(left=ConstantInt(value=44), right=ConstantInt(value=31))), SaveDuration(expression=ConstantDuration(value="-01:27.276719025".toDuration())), SaveDuration(expression=ConstantDuration(value="01:22.624806639".toDuration())), ReportDouble(expression=SubtractDoubles(left=ReadSlope(indexExpression=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=16.05460062908881)), right=SubtractInts(left=ConstantInt(value=53), right=ConstantInt(value=1)))), right=ReadSavedDouble)), SetSlope(index=ReadSavedInt, value=ConstantDouble(value=-24.607083204437743)), SaveBoolean(expression=ConstantBoolean(value=false)), SaveInt(expression=ConstantInt(value=-92))))),
                GroundedActivity(Instant.parse("2025-01-02T11:57:00.360807Z"), Name("122612138933"), BlockActivity(statements=listOf(ResumeTimer(index=ConstantInt(value=-84)), RestartTimer(index=ReadCounter(indexExpression=ConstantInt(value=-53))), Spawn(body=listOf(SetSlope(index=AddInts(left=ReadSavedInt, right=SubtractInts(left=SubtractInts(left=ConstantInt(value=-3), right=ReadCounter(indexExpression=ConstantInt(value=-2))), right=AddInts(left=ReadSavedInt, right=ConstantInt(value=-54)))), value=ReadSlope(indexExpression=ConstantInt(value=-25))), SetSlope(index=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ReadSavedDouble, right=ConstantDouble(value=-86.7248899413645)))))))), value=ConstantDouble(value=-5.892841856095927)), ReportDouble(expression=AddDoubles(left=ReadIntegral(indexExpression=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-22.657178302233064)), right=ConstantInt(value=7))), right=ConstantDouble(value=-57.03452827883906))), PauseTimer(index=AddInts(left=ConstantInt(value=-76), right=ReadCounter(indexExpression=ReadCounter(indexExpression=ConstantInt(value=-88))))), Await(condition=Switch(indexExpression=ConstantInt(value=34))), Await(condition=ComparePolynomialResource(left=ConstantPolynomialResourceExpression(value=ConstantDouble(value=-85.34477525145667)), right=PolynomialFromTimerResource(timerResourceExpression=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ReadSavedDouble))))), ReportDouble(expression=ReadSavedDouble), ToggleSwitch(index=ConstantInt(value=-78)), SaveDouble(expression=ReadIntegral(indexExpression=SubtractInts(left=ConstantInt(value=-75), right=IntFromDouble(doubleExpression=ConstantDouble(value=44.0897751935093))))), ReportDuration(expression=DurationFromDouble(doubleExpression=ReadIntegral(indexExpression=SubtractInts(left=ConstantInt(value=-84), right=ConstantInt(value=69))))))), ReportDuration(expression=ConstantDuration(value="06.534750072".toDuration())), SaveDuration(expression=ConstantDuration(value="23.709822844".toDuration())), IncreaseSlope(index=ConstantInt(value=29), amount=ReadSavedDouble), SetSlope(index=AddInts(left=ConstantInt(value=4), right=ReadSavedInt), value=ConstantDouble(value=-98.13484077236694)), SaveDuration(expression=DurationFromDouble(doubleExpression=ReadSavedDouble)), ToggleSwitch(index=ReadCounter(indexExpression=ReadCounter(indexExpression=ReadSavedInt))), SaveBoolean(expression=ReadSavedBoolean)))),
                GroundedActivity(Instant.parse("2025-01-02T21:07:41.979867Z"), Name("209233331359"), BlockActivity(statements=listOf(IncreaseSlope(index=SubtractInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=-10.913592448473182)), right=ReadSavedInt), amount=ConstantDouble(value=-47.725722040381655)), IncrementCounter(index=ConstantInt(value=67), amount=IntFromDouble(doubleExpression=ConstantDouble(value=16.744455197283983))), SetSlope(index=ConstantInt(value=34), value=ConstantDouble(value=-45.52119478532883)), SaveBoolean(expression=ConstantBoolean(value=true)), Await(condition=CompareTimerResource(left=ConstantTimerResource(value=ConstantDuration(value="-25.007524074".toDuration())), right=ConstantTimerResource(value=ConstantDuration(value="01:12.079624693".toDuration())))), ReportDuration(expression=ConstantDuration(value="-01:23.529952555".toDuration())), IncreaseSlope(index=ConstantInt(value=-20), amount=SubtractDoubles(left=SubtractDoubles(left=SubtractDoubles(left=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=31.96379350933961))), right=ConstantDouble(value=18.79201719690944)), right=ConstantDouble(value=-76.6308241162605)), right=ReadIntegral(indexExpression=ConstantInt(value=11)))), ResetTimer(index=ReadCounter(indexExpression=ReadCounter(indexExpression=ReadSavedInt))), SaveDuration(expression=ReadTimer(indexExpression=ConstantInt(value=-27))), SaveInt(expression=ConstantInt(value=3))))),
                GroundedActivity(Instant.parse("2025-01-02T19:30:30.525330Z"), Name("662871814902"), BlockActivity(statements=listOf(SetSlope(index=ConstantInt(value=14), value=ReadIntegral(indexExpression=ConstantInt(value=-25))), ReportDuration(expression=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=-13)))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), SetSlope(index=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=-40))), value=ReadSavedDouble), ReportDouble(expression=AddDoubles(left=ReadSlope(indexExpression=ConstantInt(value=-2)), right=ConstantDouble(value=42.40191440303164))), SaveDouble(expression=ConstantDouble(value=55.29548481165483)), ReportDouble(expression=ConstantDouble(value=-70.66550868067367)), IncrementCounter(index=ConstantInt(value=-85), amount=ConstantInt(value=69)), SaveDuration(expression=ConstantDuration(value="01:20.470417122".toDuration()))))),
                GroundedActivity(Instant.parse("2025-01-02T13:51:52.269374Z"), Name("677325364958"), BlockActivity(statements=listOf(SaveInt(expression=ReadCounter(indexExpression=ConstantInt(value=39))), SaveBoolean(expression=ConstantBoolean(value=true)), SaveInt(expression=ReadCounter(indexExpression=ReadSavedInt)), SaveDouble(expression=ConstantDouble(value=-36.41601107424943)), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), ResumeTimer(index=ReadSavedInt), IncreaseSlope(index=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=AddInts(left=IntFromDouble(doubleExpression=ConstantDouble(value=29.315279593531983)), right=ConstantInt(value=50)))), amount=ReadIntegral(indexExpression=ConstantInt(value=-82))), SaveDouble(expression=ConstantDouble(value=-18.665197088934036)), Await(condition=CompareDoubleResource(left=ConstantDoubleResource(value=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ConstantDouble(value=-22.97196553440537)))), right=Slope(indexExpression=IntFromDouble(doubleExpression=ReadSavedDouble)))), RestartTimer(index=AddInts(left=ConstantInt(value=3), right=AddInts(left=ReadSavedInt, right=ConstantInt(value=77))))))),
                GroundedActivity(Instant.parse("2025-01-02T21:30:37.779015Z"), Name("293192788085"), BlockActivity(statements=listOf(ReportDouble(expression=ConstantDouble(value=-61.501065334299)), ReportBoolean(expression=ConstantBoolean(value=false)), ReportDuration(expression=ReadTimer(indexExpression=ConstantInt(value=-26))), IncreaseSlope(index=ConstantInt(value=48), amount=ConstantDouble(value=34.223871836029616)), SetCounter(index=ReadSavedInt, value=AddInts(left=ConstantInt(value=-81), right=AddInts(left=ConstantInt(value=93), right=ConstantInt(value=5)))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), RestartTimer(index=ConstantInt(value=54)), SaveBoolean(expression=ConstantBoolean(value=false)), SaveInt(expression=ConstantInt(value=19)), ReportBoolean(expression=ConstantBoolean(value=false))))),
                GroundedActivity(Instant.parse("2025-01-02T21:21:39.838987Z"), Name("911899878423"), BlockActivity(statements=listOf(SetCounter(index=ConstantInt(value=38), value=SubtractInts(left=SubtractInts(left=IntFromDouble(doubleExpression=SubtractDoubles(left=ReadSlope(indexExpression=AddInts(left=ConstantInt(value=-79), right=ConstantInt(value=-82))), right=ConstantDouble(value=-65.008080589531))), right=ConstantInt(value=52)), right=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt)))), Spawn(body=listOf(SetSlope(index=ReadCounter(indexExpression=AddInts(left=AddInts(left=ConstantInt(value=-95), right=ReadSavedInt), right=SubtractInts(left=ReadSavedInt, right=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=56), right=AddInts(left=ConstantInt(value=92), right=ConstantInt(value=39))))))), value=AddDoubles(left=DoubleFromInt(intExpression=ConstantInt(value=-45)), right=ReadSavedDouble)), ReportDuration(expression=DurationFromDouble(doubleExpression=ReadIntegral(indexExpression=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt))))), ToggleSwitch(index=ConstantInt(value=-17)), SaveInt(expression=ConstantInt(value=-23)), SetSlope(index=ConstantInt(value=4), value=ConstantDouble(value=21.415062874849752)), SaveInt(expression=IntFromDouble(doubleExpression=ConstantDouble(value=91.3670123161929))), Await(condition=ComparePolynomialResource(left=PolynomialFromTimerResource(timerResourceExpression=ConstantTimerResource(value=DurationFromDouble(doubleExpression=ConstantDouble(value=27.97005861371538)))), right=ConstantPolynomialResourceExpression(value=ConstantDouble(value=-12.65927568934704)))), PauseTimer(index=ReadCounter(indexExpression=ConstantInt(value=-3))), Await(condition=Switch(indexExpression=SubtractInts(left=AddInts(left=AddInts(left=ReadSavedInt, right=ConstantInt(value=-58)), right=ReadCounter(indexExpression=ConstantInt(value=96))), right=ConstantInt(value=-22)))), SetSwitch(index=ConstantInt(value=-73), value=ConstantBoolean(value=true)))), ReportBoolean(expression=ConstantBoolean(value=false)), IncrementCounter(index=SubtractInts(left=ReadCounter(indexExpression=ReadCounter(indexExpression=SubtractInts(left=ConstantInt(value=-44), right=ConstantInt(value=-61)))), right=ConstantInt(value=65)), amount=ConstantInt(value=29)), IncrementCounter(index=ConstantInt(value=-10), amount=AddInts(left=ReadCounter(indexExpression=SubtractInts(left=IntFromDouble(doubleExpression=ReadSlope(indexExpression=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=96)), right=ReadCounter(indexExpression=ReadCounter(indexExpression=ConstantInt(value=55)))))), right=ReadSavedInt)), right=ReadCounter(indexExpression=IntFromDouble(doubleExpression=AddDoubles(left=DoubleFromInt(intExpression=ConstantInt(value=1)), right=SubtractDoubles(left=AddDoubles(left=ConstantDouble(value=-67.17014639473416), right=ConstantDouble(value=57.436932082710115)), right=ConstantDouble(value=-61.80167674963046))))))), ReportDuration(expression=ReadSavedDuration), Spawn(body=listOf(ReportDouble(expression=SubtractDoubles(left=ConstantDouble(value=67.24906444820644), right=ReadSlope(indexExpression=SubtractInts(left=AddInts(left=ReadSavedInt, right=SubtractInts(left=ConstantInt(value=-38), right=SubtractInts(left=ConstantInt(value=-27), right=ConstantInt(value=84)))), right=SubtractInts(left=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=16)), right=ConstantInt(value=72)))))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=false))), ReportBoolean(expression=ConstantBoolean(value=true)), ReportDuration(expression=ReadSavedDuration), IncreaseSlope(index=ReadSavedInt, amount=SubtractDoubles(left=ReadIntegral(indexExpression=ConstantInt(value=80)), right=AddDoubles(left=SubtractDoubles(left=ConstantDouble(value=-11.312092924521664), right=ConstantDouble(value=-13.665901397410508)), right=ReadSavedDouble))), SaveBoolean(expression=ReadSavedBoolean), IncrementCounter(index=ConstantInt(value=8), amount=SubtractInts(left=ConstantInt(value=-73), right=ConstantInt(value=-17))), PauseTimer(index=ConstantInt(value=72)), ReportBoolean(expression=ConstantBoolean(value=true)), SaveBoolean(expression=ConstantBoolean(value=true)))), SetSwitch(index=ConstantInt(value=48), value=And(left=ConstantBoolean(value=false), right=And(left=ConstantBoolean(value=false), right=ConstantBoolean(value=true)))), SaveDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=16.70978424743805))), ReportInt(expression=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=ConstantInt(value=-30))))))),
                GroundedActivity(Instant.parse("2025-01-02T17:02:01.634103Z"), Name("927975692092"), BlockActivity(statements=listOf(SaveDuration(expression=ReadSavedDuration), IncrementCounter(index=SubtractInts(left=ConstantInt(value=93), right=ConstantInt(value=-79)), amount=ConstantInt(value=32)), IncreaseSlope(index=ConstantInt(value=0), amount=ConstantDouble(value=89.88759374438453)), ReportDuration(expression=ReadTimer(indexExpression=ConstantInt(value=59))), ReportInt(expression=ConstantInt(value=-38)), ToggleSwitch(index=ConstantInt(value=89)), SaveDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=86.79991646866426))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), Await(condition=ConstantBooleanResource(value=CompareDuration(left=DurationFromDouble(doubleExpression=ConstantDouble(value=3.5127791192128655)), right=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-60)), right=ConstantInt(value=-93))))))), ResumeTimer(index=ReadCounter(indexExpression=ConstantInt(value=-50)))))),
                GroundedActivity(Instant.parse("2025-01-02T16:59:47.931983Z"), Name("221169964769"), BlockActivity(statements=listOf(ReportDuration(expression=ReadTimer(indexExpression=ConstantInt(value=-71))), IncrementCounter(index=IntFromDouble(doubleExpression=ConstantDouble(value=51.705140401636754)), amount=SubtractInts(left=AddInts(left=SubtractInts(left=ConstantInt(value=-81), right=AddInts(left=ReadSavedInt, right=AddInts(left=ConstantInt(value=5), right=ConstantInt(value=61)))), right=SubtractInts(left=IntFromDouble(doubleExpression=SubtractDoubles(left=ReadSavedDouble, right=ConstantDouble(value=-2.451037160865127))), right=ConstantInt(value=11))), right=IntFromDouble(doubleExpression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=AddDoubles(left=ConstantDouble(value=43.96190463159169), right=ConstantDouble(value=-67.19404636380754))))))), RestartTimer(index=ConstantInt(value=-76)), SaveDouble(expression=ReadSavedDouble), SetCounter(index=ConstantInt(value=-87), value=ConstantInt(value=-44)), SaveInt(expression=ConstantInt(value=40)), SaveDouble(expression=SubtractDoubles(left=ConstantDouble(value=57.209497802144625), right=ConstantDouble(value=82.1013486617149))), ReportDuration(expression=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=IntFromDouble(doubleExpression=ConstantDouble(value=18.100233599086195))))), ToggleSwitch(index=ReadCounter(indexExpression=ConstantInt(value=27))), ReportDouble(expression=ReadSlope(indexExpression=ConstantInt(value=-35)))))),
            )
        )
        tester.add(GroundedActivity(Instant.parse("2025-01-02T20:41:10.824345Z"), Name("820842477867"), BlockActivity(statements=listOf(ReportDuration(expression=ReadTimer(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=IntFromDouble(doubleExpression=AddDoubles(left=ReadSavedDouble, right=ConstantDouble(value=-93.98346632086741))))))), ReportBoolean(expression=Or(left=CompareDuration(left=DurationFromDouble(doubleExpression=ConstantDouble(value=-38.09024858656087)), right=ConstantDuration(value="-01:31.583368799".toDuration())), right=ConstantBoolean(value=false))), SetSwitch(index=SubtractInts(left=ReadSavedInt, right=ConstantInt(value=-13)), value=CompareDouble(left=ConstantDouble(value=54.968976308380576), right=DoubleFromInt(intExpression=ConstantInt(value=-61)))), IncrementCounter(index=ConstantInt(value=-100), amount=ReadSavedInt), IncreaseSlope(index=IntFromDouble(doubleExpression=ReadSavedDouble), amount=ConstantDouble(value=-46.59462598937543)), ReportBoolean(expression=Or(left=ConstantBoolean(value=true), right=ConstantBoolean(value=true))), ResetTimer(index=ConstantInt(value=-86)), PauseTimer(index=AddInts(left=ConstantInt(value=-64), right=ConstantInt(value=-56))), RestartTimer(index=AddInts(left=ConstantInt(value=47), right=ConstantInt(value=-55))), ResetTimer(index=SubtractInts(left=SubtractInts(left=ConstantInt(value=23), right=AddInts(left=AddInts(left=ConstantInt(value=96), right=SubtractInts(left=ConstantInt(value=14), right=ConstantInt(value=93))), right=ConstantInt(value=42))), right=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ReadSlope(indexExpression=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ConstantInt(value=37))))))))))))
        tester.remove(GroundedActivity(Instant.parse("2025-01-02T17:02:01.634103Z"), Name("927975692092"), BlockActivity(statements=listOf(SaveDuration(expression=ReadSavedDuration), IncrementCounter(index=SubtractInts(left=ConstantInt(value=93), right=ConstantInt(value=-79)), amount=ConstantInt(value=32)), IncreaseSlope(index=ConstantInt(value=0), amount=ConstantDouble(value=89.88759374438453)), ReportDuration(expression=ReadTimer(indexExpression=ConstantInt(value=59))), ReportInt(expression=ConstantInt(value=-38)), ToggleSwitch(index=ConstantInt(value=89)), SaveDuration(expression=DurationFromDouble(doubleExpression=ConstantDouble(value=86.79991646866426))), Await(condition=ConstantBooleanResource(value=ConstantBoolean(value=true))), Await(condition=ConstantBooleanResource(value=CompareDuration(left=DurationFromDouble(doubleExpression=ConstantDouble(value=3.5127791192128655)), right=DurationFromDouble(doubleExpression=DoubleFromInt(intExpression=SubtractInts(left=ReadCounter(indexExpression=ConstantInt(value=-60)), right=ConstantInt(value=-93))))))), ResumeTimer(index=ReadCounter(indexExpression=ConstantInt(value=-50)))))))
    }

    @Tag("long-test")
    @ParameterizedTest
    @MethodSource("fuzzingSeeds")
    fun `random plan edits conform to fundamental incremental sim guarantee -- model 1`(seed: Int) {
        `random plan edits conform to fundamental incremental sim guarantee`(seed) { rng ->
            object : FuzzTestSettings<TestModel> {
                override val numberOfRounds: Int = 100

                override fun numberOfInitialActivities(): Int = 10.0.pow(rng.nextDouble(1.0, 3.0)).toInt()

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
            object : FuzzTestSettings<BlockTestModel> {
                override val numberOfRounds: Int = 100

                override fun numberOfInitialActivities(): Int = 10.0.pow(rng.nextDouble(1.0, 3.0)).toInt()

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

                private fun nextBooleanExpression(): Expression<Boolean> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
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

                private fun nextIntExpression(): Expression<Int> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantInt(rng.nextInt(-100, 100))
                } else rng.choose(
                    { ReadSavedInt },
                    { ReadCounter(nextIntExpression()) },
                    { AddInts(nextIntExpression(), nextIntExpression()) },
                    { SubtractInts(nextIntExpression(), nextIntExpression()) },
                    { IntFromDouble(nextDoubleExpression()) },
                )

                private fun nextDoubleExpression(): Expression<Double> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDouble(rng.nextDouble(-100.0, 100.0))
                } else rng.choose(
                    { ReadSavedDouble },
                    { ReadSlope(nextIntExpression()) },
                    { ReadIntegral(nextIntExpression()) },
                    { AddDoubles(nextDoubleExpression(), nextDoubleExpression()) },
                    { SubtractDoubles(nextDoubleExpression(), nextDoubleExpression()) },
                    { DoubleFromInt(nextIntExpression()) },
                )

                private fun nextDurationExpression(): Expression<Duration> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDuration(rng.nextDouble(-100.0, 100.0).seconds)
                } else rng.choose(
                    { ReadSavedDuration },
                    { ReadTimer(nextIntExpression()) },
                    { DurationFromDouble(nextDoubleExpression()) },
                )

                private fun nextBooleanResourceExpression(): Expression<BooleanResource> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
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

                private fun nextIntResourceExpression(): Expression<IntResource> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantIntResource(nextIntExpression())
                } else rng.choose(
                    { Counter(nextIntExpression()) },
                    { AddIntResources(nextIntResourceExpression(), nextIntResourceExpression()) },
                    { SubtractIntResources(nextIntResourceExpression(), nextIntResourceExpression()) },
                )

                private fun nextDoubleResourceExpression(): Expression<DoubleResource> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantDoubleResource(nextDoubleExpression())
                } else rng.choose(
                    { Slope(nextIntExpression()) },
                    { AddDoubleResources(nextDoubleResourceExpression(), nextDoubleResourceExpression()) },
                    { SubtractDoubleResources(nextDoubleResourceExpression(), nextDoubleResourceExpression()) },
                )

                private fun nextTimerResourceExpression(): Expression<TimerResource> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
                    ConstantTimerResource(nextDurationExpression())
                } else rng.choose(
                    { Timer(nextIntExpression()) },
                    { AddTimerResources(nextTimerResourceExpression(), nextTimerResourceExpression()) },
                    { SubtractTimerResources(nextTimerResourceExpression(), nextTimerResourceExpression()) },
                )

                private fun nextPolynomialResourceExpression(): Expression<PolynomialResource> = if (rng.chance(CHANCE_OF_CONSTANT_EXPRESSION)) {
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

    interface FuzzTestSettings<M> {
        val numberOfRounds: Int
        fun numberOfInitialActivities(): Int
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
        settingsConstructor: (Random) -> FuzzTestSettings<M>,
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
        val settings = settingsConstructor(rng)
        val numberOfInitialActivities = settings.numberOfInitialActivities()
        println("Running $numberOfInitialActivities activities through ${settings.numberOfRounds} rounds of edits...")

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
                settings.nextActivity()).also { println("Add $it") }
        }
        endBlock()
        // Verify the incremental simulator can handle that initial plan
        var tester = test(settings::constructModel, activities)
        println("Initial simulation complete")

        // For as many rounds of edits as we've decided to do...
        for (roundNumber in 1..settings.numberOfRounds) {
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
                        settings.nextActivity()).also { println("Add $it") }
                }
                // Then build a new incremental tester with those time bounds, saving and restoring from a checkpoint
                tester = test(
                    constructModel = settings::constructModel,
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
                            settings.nextActivity()
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
                        val newActivity = settings.randomizeArgs(activity.activity)
                        println("Edit $activity to $newActivity")
                        edits += edit(activity to newActivity)
                    }
                    else -> throw AssertionError("Code path should never run")
                }
            }
            endBlock()
            println("Running edits (${activities.size} activities total)")
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

    data class BlockActivity(val statements: List<StatementBlock>, val startingLocals: BlockLocals = BlockLocals()) : Activity<BlockTestModel> {
        context(scope: TaskScope)
        override suspend fun effectModel(model: BlockTestModel) {
            // Create some local variables, which are the permissible way to carry state from one step to the next
            // startingLocals is effectively-final to maintain the activity effectModel contract - that effectModel is a deterministic function.
            val locals = startingLocals.copy()
            statements.forEach { it.run(model, locals) }
        }

        override fun toString(): String = "BlockActivity(statements=listOf(${statements.joinToString()}))"
    }

    data class BlockLocals(
        var savedBoolean: Boolean = false,
        var savedInt: Int = 0,
        var savedDouble: Double = 0.0,
        var savedDuration: Duration = ZERO,
    )

    sealed interface StatementBlock {
        context(_: TaskScope)
        suspend fun run(model: BlockTestModel, locals: BlockLocals)

        sealed interface EffectBlock : StatementBlock {
            sealed interface SwitchEffectBlock : EffectBlock {
                data class SetSwitch(val index: Expression<Int>, val value: Expression<Boolean>) : SwitchEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.switches[floorMod(index.evaluate(model, locals), model.switches.size)].set(value.evaluate(model, locals))
                    }
                }
                data class ToggleSwitch(val index: Expression<Int>) : SwitchEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.switches[floorMod(index.evaluate(model, locals), model.switches.size)].toggle()
                    }
                }
            }
            sealed interface TimerEffectBlock : EffectBlock {
                data class PauseTimer(val index: Expression<Int>) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.timers[floorMod(index.evaluate(model, locals), model.timers.size)].pause()
                    }
                }
                data class ResumeTimer(val index: Expression<Int>) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.timers[floorMod(index.evaluate(model, locals), model.timers.size)].resume()
                    }
                }
                data class ResetTimer(val index: Expression<Int>) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.timers[floorMod(index.evaluate(model, locals), model.timers.size)].reset()
                    }
                }
                data class RestartTimer(val index: Expression<Int>) : TimerEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.timers[floorMod(index.evaluate(model, locals), model.timers.size)].restart()
                    }
                }
            }
            sealed interface CounterEffectBlock : EffectBlock {
                data class SetCounter(val index: Expression<Int>, val value: Expression<Int>) : CounterEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.counters[floorMod(index.evaluate(model, locals), model.counters.size)].set(value.evaluate(model, locals))
                    }
                }
                data class IncrementCounter(val index: Expression<Int>, val amount: Expression<Int>) : CounterEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.counters[floorMod(index.evaluate(model, locals), model.counters.size)].increment(amount.evaluate(model, locals))
                    }
                }
            }
            sealed interface SlopeEffectBlock : EffectBlock {
                data class SetSlope(val index: Expression<Int>, val value: Expression<Double>) : SlopeEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.slopes[floorMod(index.evaluate(model, locals), model.slopes.size)].set(value.evaluate(model, locals))
                    }
                }
                data class IncreaseSlope(val index: Expression<Int>, val amount: Expression<Double>) : SlopeEffectBlock {
                    context(_: TaskScope)
                    override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                        model.slopes[floorMod(index.evaluate(model, locals), model.slopes.size)].increase(amount.evaluate(model, locals))
                    }
                }
            }
        }
        data class Await(val condition: Expression<BooleanResource>) : StatementBlock {
            context(_: TaskScope)
            override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                await(condition.evaluate(model, locals))
            }
        }
        data class Spawn(val body: List<StatementBlock>) : StatementBlock {
            context(_: TaskScope)
            override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                // Copy state information from parent to child
                val child = BlockActivity(body, locals.copy())
                spawn(child, model)
                // However, the parent may not modify the child after the child is spawned.
            }

            override fun toString(): String = "Spawn(body=listOf(${body.joinToString()}))"
        }
        sealed interface ReportBlock : StatementBlock {
            data class ReportBoolean(val expression: Expression<Boolean>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(expression.evaluate(model, locals).toString())
                }
            }
            data class ReportInt(val expression: Expression<Int>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(expression.evaluate(model, locals).toString())
                }
            }
            data class ReportDouble(val expression: Expression<Double>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(expression.evaluate(model, locals).toString())
                }
            }
            data class ReportDuration(val expression: Expression<Duration>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(expression.evaluate(model, locals).toString())
                }
            }
        }
        sealed interface SaveValue : StatementBlock {
            data class SaveBoolean(val expression: Expression<Boolean>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedBoolean = expression.evaluate(model, locals)
                }
            }
            data class SaveInt(val expression: Expression<Int>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedInt = expression.evaluate(model, locals)
                }
            }
            data class SaveDouble(val expression: Expression<Double>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedDouble = expression.evaluate(model, locals)
                }
            }
            data class SaveDuration(val expression: Expression<Duration>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedDuration = expression.evaluate(model, locals)
                }
            }
        }
    }
    interface Expression<R> {
        context(_: TaskScope)
        fun evaluate(model: BlockTestModel, locals: BlockLocals): R

        // Boolean
        data class ConstantBoolean(val value: Boolean) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean = value
        }
        data object ReadSavedBoolean : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean = locals.savedBoolean
        }
        data class ReadSwitch(val indexExpression: Expression<Int>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                model.switches[floorMod(indexExpression.evaluate(model, locals), model.switches.size)].getValue()
        }
        data class CompareInt(val left: Expression<Int>, val right: Expression<Int>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                left.evaluate(model, locals) > right.evaluate(model, locals)
        }
        data class CompareDouble(val left: Expression<Double>, val right: Expression<Double>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                left.evaluate(model, locals) > right.evaluate(model, locals)
        }
        data class CompareDuration(val left: Expression<Duration>, val right: Expression<Duration>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                left.evaluate(model, locals) > right.evaluate(model, locals)
        }
        data class And(val left: Expression<Boolean>, val right: Expression<Boolean>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                left.evaluate(model, locals) && right.evaluate(model, locals)
        }
        data class Or(val left: Expression<Boolean>, val right: Expression<Boolean>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                left.evaluate(model, locals) || right.evaluate(model, locals)
        }
        data class Not(val expression: Expression<Boolean>) : Expression<Boolean> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Boolean =
                !expression.evaluate(model, locals)
        }

        // Int
        data class ConstantInt(val value: Int) : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int = value
        }
        data object ReadSavedInt : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int = locals.savedInt
        }
        data class ReadCounter(val indexExpression: Expression<Int>) : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int =
                model.counters[floorMod(indexExpression.evaluate(model, locals), model.counters.size)].getValue()
        }
        data class AddInts(val left: Expression<Int>, val right: Expression<Int>) : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractInts(val left: Expression<Int>, val right: Expression<Int>) : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }
        data class IntFromDouble(val doubleExpression: Expression<Double>) : Expression<Int> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Int =
                doubleExpression.evaluate(model, locals).toInt()
        }

        // Double
        data class ConstantDouble(val value: Double) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double = value
        }
        data object ReadSavedDouble : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double = locals.savedDouble
        }
        data class ReadSlope(val indexExpression: Expression<Int>) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double =
                model.slopes[floorMod(indexExpression.evaluate(model, locals), model.slopes.size)].getValue()
        }
        data class ReadIntegral(val indexExpression: Expression<Int>) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double =
                model.integrals[floorMod(indexExpression.evaluate(model, locals), model.integrals.size)].getValue()
        }
        data class AddDoubles(val left: Expression<Double>, val right: Expression<Double>) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractDoubles(val left: Expression<Double>, val right: Expression<Double>) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }
        data class DoubleFromInt(val intExpression: Expression<Int>) : Expression<Double> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Double =
                intExpression.evaluate(model, locals).toDouble()
        }

        // Duration
        data class ConstantDuration(val value: Duration) : Expression<Duration> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Duration = value

            override fun toString(): String {
                return "ConstantDuration(value=${value.repr()})"
            }
        }
        data object ReadSavedDuration : Expression<Duration> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Duration = locals.savedDuration
        }
        data class ReadTimer(val indexExpression: Expression<Int>) : Expression<Duration> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Duration =
                model.timers[floorMod(indexExpression.evaluate(model, locals), model.timers.size)].getValue()
        }
        data class DurationFromDouble(val doubleExpression: Expression<Double>) : Expression<Duration> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): Duration =
                doubleExpression.evaluate(model, locals).seconds
        }

        // BooleanResource
        data class ConstantBooleanResource(val value: Expression<Boolean>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                DiscreteResourceMonad.pure(value.evaluate(model, locals))
        }
        data class Switch(val indexExpression: Expression<Int>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                model.switches[floorMod(indexExpression.evaluate(model, locals), model.switches.size)]
        }
        data class AndResource(val left: Expression<BooleanResource>, val right: Expression<BooleanResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) and right.evaluate(model, locals)
        }
        data class OrResource(val left: Expression<BooleanResource>, val right: Expression<BooleanResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) or right.evaluate(model, locals)
        }
        data class NotResource(val expression: Expression<BooleanResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                expression.evaluate(model, locals).not()
        }
        data class CompareIntResource(val left: Expression<IntResource>, val right: Expression<IntResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) greaterThan right.evaluate(model, locals)
        }
        data class CompareDoubleResource(val left: Expression<DoubleResource>, val right: Expression<DoubleResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) greaterThan right.evaluate(model, locals)
        }
        data class CompareTimerResource(val left: Expression<TimerResource>, val right: Expression<TimerResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) greaterThan right.evaluate(model, locals)
        }
        data class ComparePolynomialResource(val left: Expression<PolynomialResource>, val right: Expression<PolynomialResource>) : Expression<BooleanResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): BooleanResource =
                left.evaluate(model, locals) greaterThan right.evaluate(model, locals)
        }

        // IntResource
        data class ConstantIntResource(val value: Expression<Int>) : Expression<IntResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): IntResource =
                DiscreteResourceMonad.pure(value.evaluate(model, locals))
        }
        data class Counter(val indexExpression: Expression<Int>) : Expression<IntResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): IntResource =
                model.counters[floorMod(indexExpression.evaluate(model, locals), model.counters.size)]
        }
        data class AddIntResources(val left: Expression<IntResource>, val right: Expression<IntResource>) : Expression<IntResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): IntResource =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractIntResources(val left: Expression<IntResource>, val right: Expression<IntResource>) : Expression<IntResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): IntResource =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }

        // DoubleResource
        data class ConstantDoubleResource(val value: Expression<Double>) : Expression<DoubleResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): DoubleResource =
                DiscreteResourceMonad.pure(value.evaluate(model, locals))
        }
        data class Slope(val indexExpression: Expression<Int>) : Expression<DoubleResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): DoubleResource =
                model.slopes[floorMod(indexExpression.evaluate(model, locals), model.slopes.size)]
        }
        data class AddDoubleResources(val left: Expression<DoubleResource>, val right: Expression<DoubleResource>) : Expression<DoubleResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): DoubleResource =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractDoubleResources(val left: Expression<DoubleResource>, val right: Expression<DoubleResource>) : Expression<DoubleResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): DoubleResource =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }

        // TimerResource
        data class ConstantTimerResource(val value: Expression<Duration>) : Expression<TimerResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): TimerResource =
                TimerResourceOperations.constant(value.evaluate(model, locals))
        }
        data class Timer(val indexExpression: Expression<Int>) : Expression<TimerResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): TimerResource =
                model.timers[floorMod(indexExpression.evaluate(model, locals), model.timers.size)]
        }
        data class AddTimerResources(val left: Expression<TimerResource>, val right: Expression<TimerResource>) : Expression<TimerResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): TimerResource =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractTimerResources(val left: Expression<TimerResource>, val right: Expression<TimerResource>) : Expression<TimerResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): TimerResource =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }

        // PolynomialResource
        data class ConstantPolynomialResourceExpression(val value: Expression<Double>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                constant(value.evaluate(model, locals))
        }
        data class Integral(val indexExpression: Expression<Int>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                model.integrals[floorMod(indexExpression.evaluate(model, locals), model.integrals.size)]
        }
        data class AddPolynomialResources(val left: Expression<PolynomialResource>, val right: Expression<PolynomialResource>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                left.evaluate(model, locals) + right.evaluate(model, locals)
        }
        data class SubtractPolynomialResources(val left: Expression<PolynomialResource>, val right: Expression<PolynomialResource>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                left.evaluate(model, locals) - right.evaluate(model, locals)
        }
        data class PolynomialFromDoubleResource(val doubleResourceExpression: Expression<DoubleResource>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                doubleResourceExpression.evaluate(model, locals).asPolynomial()
        }
        data class PolynomialFromTimerResource(val timerResourceExpression: Expression<TimerResource>) : Expression<PolynomialResource> {
            context(_: TaskScope)
            override fun evaluate(model: BlockTestModel, locals: BlockLocals): PolynomialResource =
                timerResourceExpression.evaluate(model, locals).asPolynomial(1.seconds)
        }

        companion object {
            private var logIndex = 0
            fun <R> Expression<R>.log(name: String? = null) = object : Expression<R> {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, locals: BlockLocals): R =
                    this@log.evaluate(model, locals).also { result ->
                        println("Log ${++logIndex}: ${name?.let { "$it = " } ?: ""}$result")
                    }
            }

            fun <V> Expression<Resource<V>>.logSamples(name: String? = null) = object : Expression<Resource<V>> {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, locals: BlockLocals): Resource<V> {
                    val originalResource = this@logSamples.evaluate(model, locals)
                    return Resource { originalResource.getDynamics().also { result ->
                        println("Log sample ${++logIndex}: ${name ?: originalResource.toString()} = $result")
                    }}.fullyNamed { originalResource.name }
                }
            }
        }
    }
}
