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
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.isEmpty
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
import java.io.File
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
    fun `await interrupts for merge nodes`() {
        // This test sets up (rather obtusely) a situation where an await is interrupted by a merge node.
        // Updates are then made to cell writes feeding into that await node.
        // Incorrect handling of re-computed await interruptions led to an extraneous "read" edge, from one of those writes
        // feeding the cell merge, to the interrupting await node.
        // This in turn tripped up some cell read update logic on further re-working of the cell nodes,
        // eventually fooling the system into not re-evaluating a condition it ought to.
        // There is almost certainly a better way to detect this situation than this test,
        // and this test will likely stop testing this case if the incremental simulator is changed much.
        // For now it's worth keeping, since I have neither the time nor interest in designing a more streamlined test case.
        val activity = GroundedActivity(Instant.parse("2025-01-02T17:00:00Z"), Name("slope_0 += 1e-3"), BlockActivity(listOf(
            IncreaseSlope(ConstantInt(0), ConstantDouble(1e-3)),
        )))
        val activity2 = GroundedActivity(Instant.parse("2025-01-02T21:00:00Z"), Name("counter_2 += 3"), BlockActivity(listOf(
            IncrementCounter(ConstantInt(2), ConstantInt(3))
        )))
        val tester = test(::BlockTestModel,
            endTime = day4,
            activities = listOf(
                activity,
                activity2,
                GroundedActivity(Instant.parse("2025-01-02T21:00:00Z"), Name("slope_0 = integral_0.value()"), BlockActivity(listOf(
                    SetSlope(ConstantInt(2), ReadIntegral(ConstantInt(0))),
                ))),
                GroundedActivity(Instant.parse("2025-01-02T22:00:00Z"), Name("bump counters"), BlockActivity(listOf(
                    Spawn(
                        listOf(
                            SetCounter(ReadCounter(ConstantInt(2)), ReadCounter(ConstantInt(2))),
                        )
                    ),
                    Await(
                        ConstantBooleanResource(
                            CompareDouble(
                                ConstantDouble(0.0),
                                ReadIntegral(ConstantInt(2)),
                            )
                        )
                    ),
                    Await(ConstantBooleanResource(ConstantBoolean(true))),
                    IncrementCounter(ConstantInt(0), ConstantInt(0))
                ))),
            )
        )
        tester.add(GroundedActivity(Instant.parse("2025-01-02T20:30:00Z"), Name("slope_0 -= 1"), BlockActivity(listOf(
            IncreaseSlope(ConstantInt(0), ConstantDouble(-1.0)),
        ))))
        tester.run(
            remove(activity)
            + edit(activity2 to BlockActivity(listOf(IncrementCounter(ConstantInt(2), ConstantInt(0)))))
        )
    }

    @Test
    fun `repro by seed`() {
         // simplifyTranscriptOnFailure = true
        `random plan edits conform to fundamental incremental sim guarantee -- model 2`(2)
    }

    @Test
    fun `repro directly`() {
        val tester = test(::BlockTestModel,
            startTime = Instant.parse("2025-01-01T00:00:00Z"),
            endTime = Instant.parse("2025-01-03T00:12:04.322638Z"),
            activities = listOf(
                GroundedActivity(Instant.parse("2025-01-01T23:19:26.478898Z"), Name("521455532975"), BlockActivity(statements=listOf(IncreaseSlope(index=ConstantInt(value=-84), amount=ConstantDouble(value=-1.3099852473623498E17))))),
                GroundedActivity(Instant.parse("2025-01-01T21:27:50.034961Z"), Name("840438594862"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=5))))),
                GroundedActivity(Instant.parse("2025-01-01T22:58:02.355312Z"), Name("754911604940"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=-61))))),
                GroundedActivity(Instant.parse("2025-01-02T00:42:12.133Z"), Name("570730811939"), BlockActivity(statements=listOf(SetSlope(index=ConstantInt(value=-90), value=ConstantDouble(value=1018819.1136304578))))),
                GroundedActivity(Instant.parse("2025-01-02T04:24:02.230089Z"), Name("680193939163"), BlockActivity(statements=listOf(SetSlope(index=ConstantInt(value=137), value=ReadIntegral(indexExpression=ReadSavedInt))))),
                GroundedActivity(Instant.parse("2025-01-02T03:41:20.235635Z"), Name("445471211820"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=74))))),
                GroundedActivity(Instant.parse("2025-01-02T04:07:39.728694Z"), Name("296744037140"), BlockActivity(statements=listOf(SaveBoolean(value=CompareInt(left=AddInts(left=ReadCounter(indexExpression=ConstantInt(value=-46)), right=SubtractInts(left=IntFromDouble(doubleExpression=AddDoubles(left=SubtractDoubles(left=SubtractDoubles(left=ConstantDouble(value=-20.081075196544717), right=ReadSavedDouble), right=SubtractDoubles(left=ConstantDouble(value=-69.5766476943823), right=ConstantDouble(value=92.00419361926095))), right=ReadSavedDouble)), right=ReadCounter(indexExpression=IntFromDouble(doubleExpression=ReadIntegral(indexExpression=ReadSavedInt))))), right=IntFromDouble(doubleExpression=ConstantDouble(value=-73.34076401136508))))))),
                GroundedActivity(Instant.parse("2025-01-02T03:06:05.231838Z"), Name("170230978116"), BlockActivity(statements=listOf(Await(condition=Switch(indexExpression=IntFromDouble(doubleExpression=SubtractDoubles(left=ConstantDouble(value=-85.70909151094965), right=ConstantDouble(value=-12.211252192377174))))), IncreaseSlope(index=ConstantInt(value=3), amount=ConstantDouble(value=10.474136831022818))))),
            )
        )
        tester.move(GroundedActivity(Instant.parse("2025-01-02T03:41:20.235635Z"), Name("445471211820"), BlockActivity(statements=listOf(ToggleSwitch(index=ConstantInt(value=74))))) to Instant.parse("2025-01-02T03:40:14.549194Z"))
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

                override fun numberOfInitialActivities(): Int = 10.0.pow(rng.nextDouble(1.0, 2.0)).toInt()

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

    data class FuzzTestTranscript<M>(
        val initialPlan: Plan<M>,
        val rounds: List<FuzzTestRound<M>> = listOf()
    )

    private operator fun <M> FuzzTestTranscript<M>?.plus(round: FuzzTestRound<M>) = this?.copy(rounds = rounds + round)

    sealed interface FuzzTestRound<M> {
        data class RunEdit<M>(val edits: PlanEdits<M>) : FuzzTestRound<M> {
            operator fun plus(otherEdits: PlanEdits<M>): RunEdit<M> = copy(edits = edits + otherEdits)
            operator fun minus(otherEdits: PlanEdits<M>): RunEdit<M> = copy(edits = edits - otherEdits)
        }
        data class SaveRestore<M>(val nextPlan: Plan<M>) : FuzzTestRound<M>
    }

    private var simplifyTranscriptOnFailure = false

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

        var transcript: FuzzTestTranscript<M>? = null
        val rng = Random(seed)
        val settings = settingsConstructor(rng)

        try {
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
                    settings.nextActivity())
            }
            endBlock()

            if (simplifyTranscriptOnFailure) {
                // When requested, start a transcript to record what this test is doing as it does it.
                transcript = FuzzTestTranscript(Plan(startTime, endTime, activities.toList()))
            }

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
                    // Activities that were saved through the checkpoint can't then be changed incrementally,
                    // so choose a new set of activities to work with instead.
                    // TODO: Think through whether this must be the case... If an activity comes from an incon, can it be incrementally edited?
                    val newActivities = mutableListOf<GroundedActivity<M>>()
                    repeat (settings.numberOfInitialActivities()) {
                        newActivities += GroundedActivity(
                            rng.nextInstant(startTime..endTime),
                            rng.nextActivityId(),
                            settings.nextActivity())
                    }
                    transcript += FuzzTestRound.SaveRestore(Plan(startTime, endTime, newActivities))
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
                    rng.choose(
                        {
                            // Add an activity
                            edits += GroundedActivity(
                                rng.nextInstant(startTime..endTime),
                                rng.nextActivityId(),
                                settings.nextActivity()
                            )
                        },
                        {
                            // Remove an activity
                            edits -= activities.randomRemove(rng)
                        },
                        {
                            // Move an activity (by up to 10 minutes)
                            val activity = activities.randomRemove(rng)
                            val time = rng.nextInstant(activity.time - 10.minutes..activity.time + 10.minutes)
                                .coerceIn(startTime..endTime - 1.microseconds)
                            edits += move(activity to time)
                        },
                        {
                            // Edit an activity's arguments
                            val activity = activities.randomRemove(rng)
                            val newActivity = settings.randomizeArgs(activity.activity)
                            edits += edit(activity to newActivity)
                        }
                    )
                }
                endBlock()
                println("Running edits (${activities.size} activities total)")
                transcript += FuzzTestRound.RunEdit(edits)
                // Now run those randomly-chosen edits, asserting the single-shot and incremental simulators agree
                tester.run(edits)
                // Also apply the edits to our list of activities, to know what we can edit next round
                // Removals are done in-place as we go
                activities += edits.additions
                endBlock()
            }
        } catch (e: Throwable) {
            // If transcription was enabled, simplify and print the code to directly reproduce this failure.
            transcript?.simplify(settings::constructModel)?.let {
                val file = File("inc-sim-debug/test-repro.kt")
                println("Writing final code to $file")
                it.printAsCode(file)
            }
            throw e
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
        removeAt(rng.nextInt(indices))

    private fun <M : Any> FuzzTestTranscript<M>.simplify(constructModel: (InitScope) -> M): FuzzTestTranscript<M> {
        context (constructModel) {
            println("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits.")
            var result = this
            var lastResult: FuzzTestTranscript<M>
            do {
                lastResult = result
                result = result.simplifyByRemovingEdits()
                result = result.simplifyByRemovingEmptyRounds()
                result = result.simplifyByCondensingEdits()
                result = result.simplifyByRemovingEmptyRounds()
                result = result.simplifyByRemovingSaveRestore()
                result = result.simplifyByRemovingEmptyRounds()
                result = result.simplifyActivities()
                result = result.simplifyByRemovingEmptyRounds()
            } while (result != lastResult)
            return result
        }
    }

    /**
     * Most edits in a fuzz test aren't necessary. Try removing each one individually.
     */
    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.simplifyByRemovingEdits(): FuzzTestTranscript<M> {
        println("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits, by removing edits.")
        var result = this
        for ((roundIndex, round) in rounds.withIndex().reversed()) {
            when (round) {
                is FuzzTestRound.RunEdit -> {
                    val condensedEdits = round.edits.condensedEdits().toList()
                    print("Simplifying round $roundIndex, starting with ${condensedEdits.size} edits ")
                    System.out.flush()
                    for (edit in condensedEdits) {
                        val candidate = result.copy(rounds = result.rounds.toMutableList().also {
                            it[roundIndex] = (it[roundIndex] as FuzzTestRound.RunEdit) - edit
                        })
                        if (candidate.exposesSomeBug()) {
                            result = candidate
                            print(".")
                        } else {
                            print("X")
                        }
                        System.out.flush()
                        // Otherwise, leave result as-is; reverting edit would no longer expose the bug
                    }
                    val finalEdits = (result.rounds[roundIndex] as FuzzTestRound.RunEdit).edits
                    println("\nSimplified round $roundIndex, ending with ${finalEdits.condensedEdits().toList().size} edits")
                }
                is FuzzTestRound.SaveRestore -> {
                    print("Simplifying round $roundIndex, starting with ${round.nextPlan.activities.size} activities ")
                    System.out.flush()
                    for (activity in round.nextPlan.activities) {
                        val candidate = result.copy(rounds = result.rounds.toMutableList().also {
                            it[roundIndex] = FuzzTestRound.SaveRestore(
                                (it[roundIndex] as FuzzTestRound.SaveRestore).nextPlan - activity
                            )
                        })
                        if (candidate.exposesSomeBug()) {
                            result = candidate
                            print(".")
                        } else {
                            print("X")
                        }
                        System.out.flush()
                    }
                    val finalPlan = (result.rounds[roundIndex] as FuzzTestRound.SaveRestore).nextPlan
                    println("\nSimplified round $roundIndex, ending with ${finalPlan.activities.size} activities")
                }
            }
            System.out.flush()
        }

        print("Simplifying intiial plan, starting with ${result.initialPlan.activities.size} activities ")
        for (i in result.initialPlan.activities.indices.reversed()) {
            val candidate = result.copy(initialPlan = result.initialPlan.copy(activities =
                result.initialPlan.activities.toMutableList().also { it.removeAt(i) }))

            if (candidate.exposesSomeBug()) {
                result = candidate
                print(".")
            } else {
                print("X")
            }
            System.out.flush()
        }
        println("\nSimplified initial plan, ending with ${result.initialPlan.activities.size} activities")

        println("Simplified transcript to ${result.rounds.size} rounds and ${result.totalEdits()} total edits.")
        return result
    }

    /**
     * Intermediate edits are often unnecessary. Try combining edits into just their net effect.
     */
    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.simplifyByCondensingEdits(): FuzzTestTranscript<M> {
        println("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits, by condensing edits.")
        var result = this
        for (i in rounds.indices.drop(1).reversed()) {
            when (val source = result.rounds[i]) {
                is FuzzTestRound.RunEdit -> {
                    // Skip if the prior round is not a RunEdit round as well
                    if (result.rounds[i - 1] !is FuzzTestRound.RunEdit) continue

                    val condensedEdits = source.edits.condensedEdits().toList()
                    print("Simplifying round $i, starting with ${condensedEdits.size} edits ")
                    System.out.flush()
                    for (editToMove in condensedEdits) {
                        val candidate = result.copy(rounds = result.rounds.toMutableList().also {
                            it[i] = (it[i] as FuzzTestRound.RunEdit) - editToMove
                            it[i - 1] = (it[i - 1] as FuzzTestRound.RunEdit) + editToMove
                        })

                        if (candidate.exposesSomeBug()) {
                            result = candidate
                            print(".")
                        } else {
                            print("X")
                        }
                        System.out.flush()
                    }

                    val finalEdits = (result.rounds[i] as FuzzTestRound.RunEdit).edits
                    println("\nSimplified round $i, ending with ${finalEdits.condensedEdits().toList().size} edits")
                    System.out.flush()
                }
                is FuzzTestRound.SaveRestore -> {
                    // Nothing to do
                }
            }
        }

        (result.rounds.firstOrNull() as? FuzzTestRound.RunEdit)?.let { firstEdits ->
            val condensedEdits = firstEdits.edits.condensedEdits().toList()
            print("Simplifying first round by condensing into initial plan, starting with ${condensedEdits.size} edits ")
            for (editToMove in condensedEdits) {
                val candidate = result.copy(
                    initialPlan = result.initialPlan + editToMove,
                    rounds = result.rounds.toMutableList().also {
                        it[0] = (it[0] as FuzzTestRound.RunEdit) - editToMove
                    }
                )
                if (candidate.exposesSomeBug()) {
                    result = candidate
                    print(".")
                } else {
                    print("X")
                }
                System.out.flush()
            }

            val finalEdits = (result.rounds.first() as FuzzTestRound.RunEdit).edits
            println("\nSimplified first round by condensing into initial plan, ending with ${finalEdits.condensedEdits().toList().size} edits")
        }
        println("Simplified transcript to ${result.rounds.size} rounds and ${result.totalEdits()} total edits.")
        return result
    }

    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.simplifyByRemovingSaveRestore(): FuzzTestTranscript<M> {
        println("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits, by removing save/restore cycles.")
        var result = this

        val saveRestoreIndices = rounds.withIndex()
            .filter { (_, round) -> round is FuzzTestRound.SaveRestore }
            .map { it.index }
        print("Simplifying ${saveRestoreIndices.size} save/restore cycles ")
        System.out.flush()

        for ((priorIndex, thisIndex) in saveRestoreIndices.zipWithNext().reversed()) {
            val candidate = result.copy(rounds = result.rounds.toMutableList().also {
                val round = it[thisIndex] as FuzzTestRound.SaveRestore
                // Turn this save/restore round into an edit round where we just add in these activities
                it[thisIndex] = FuzzTestRound.RunEdit(PlanEdits(additions = round.nextPlan.activities))
                // Then bump up the end time for the prior save/restore round to permit these edits
                val priorRound = it[priorIndex] as FuzzTestRound.SaveRestore
                it[priorIndex] = priorRound.copy(nextPlan = priorRound.nextPlan.copy(endTime = round.nextPlan.endTime))
            })

            if (candidate.exposesSomeBug()) {
                result = candidate
                print(".")
            } else {
                print("X")
            }
            System.out.flush()
        }

        saveRestoreIndices.firstOrNull()?.let { firstSaveIndex ->
            val firstSaveRestore = result.rounds[firstSaveIndex] as FuzzTestRound.SaveRestore
            val candidate = result.copy(
                // Change the first save/restore round to a edit round where we add in these activities
                rounds = result.rounds.toMutableList().also {
                    it[firstSaveIndex] = FuzzTestRound.RunEdit(PlanEdits(additions = firstSaveRestore.nextPlan.activities))
                },
                // Then bump up the end time for the initial plan to permit these edits
                initialPlan = result.initialPlan.copy(endTime = firstSaveRestore.nextPlan.endTime),
            )

            if (candidate.exposesSomeBug()) {
                result = candidate
                print(".")
            } else {
                print("X")
            }
            System.out.flush()
        }

        println("\nSimplified transcript to ${result.rounds.size} rounds and ${result.totalEdits()} total edits.")
        return result
    }

    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.simplifyActivities(): FuzzTestTranscript<M> {
        println("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits, by simplifying activities themselves.")
        var result = this

        fun Plan<M>.remove(activity: GroundedActivity<M>) =
            copy(activities = activities.toMutableList().also { it.remove(activity) })
        fun PlanEdits<M>.remove(activity: GroundedActivity<M>) = PlanEdits(
            additions = additions.toMutableList().also { it.remove(activity) },
            removals = removals.toMutableList().also { it.remove(activity) },
        )
        fun FuzzTestTranscript<M>.remove(activity: GroundedActivity<M>) = FuzzTestTranscript(
            initialPlan = initialPlan.remove(activity),
            rounds = rounds.map {
                when (it) {
                    is FuzzTestRound.RunEdit -> FuzzTestRound.RunEdit(edits = it.edits.remove(activity))
                    is FuzzTestRound.SaveRestore -> FuzzTestRound.SaveRestore(nextPlan = it.nextPlan.remove(activity))
                }
            },
        )

        // This way of doing activity replacement is a little inefficient, but it's simple and effective.
        // Since we tend to optimize activities later, when the transcript is already smaller, performance doesn't matter.
        fun Plan<M>.replace(from: GroundedActivity<M>, to: GroundedActivity<M>) =
            copy(activities = activities.map { if (it == from) to else it })
        fun PlanEdits<M>.replace(from: GroundedActivity<M>, to: GroundedActivity<M>) = PlanEdits(
            additions = additions.map { if (it == from) to else it },
            removals = removals.map { if (it == from) to else it },
        )
        fun FuzzTestTranscript<M>.replace(from: GroundedActivity<M>, to: GroundedActivity<M>) = FuzzTestTranscript(
            initialPlan = initialPlan.replace(from, to),
            rounds = rounds.map {
                when (it) {
                    is FuzzTestRound.RunEdit -> FuzzTestRound.RunEdit(edits = it.edits.replace(from, to))
                    is FuzzTestRound.SaveRestore -> FuzzTestRound.SaveRestore(nextPlan = it.nextPlan.replace(from, to))
                }
            },
        )

        // mutates result instead of returning a value
        tailrec fun GroundedActivity<M>.simplify() {
            var successfulSimplification: GroundedActivity<M>? = null
            // Use exposesSomeBug to run the activity for instrumentation, if needed.
            for (simplification in simplifications(runActivity = { result.replace(this, it).exposesSomeBug() })) {
                val candidate =
                    if (simplification == null) result.remove(this)
                    else result.replace(this, simplification)

                if (candidate.exposesSomeBug()) {
                    print(".")
                    System.out.flush()
                    result = candidate
                    successfulSimplification = simplification
                    break
                } else {
                    print("X")
                    System.out.flush()
                }
            }
            // If the simplification succeeded (and didn't just remove the activity), try to simplify further.
            successfulSimplification?.simplify()
        }

        print("\nSimplifying ${result.initialPlan.activities.size} activities in initial plan ")
        System.out.flush()

        result.initialPlan.activities.forEach { it.simplify() }
        println()
        System.out.flush()

        for (roundIndex in result.rounds.indices) {
            val activitiesInRound = when (val round = result.rounds[roundIndex]) {
                // No need to look at removals - anything we remove we must have added earlier
                is FuzzTestRound.RunEdit -> round.edits.additions
                is FuzzTestRound.SaveRestore -> round.nextPlan.activities
            }
            print("Simplifying ${activitiesInRound.size} activities in round $roundIndex ")
            System.out.flush()
            activitiesInRound.forEach { it.simplify() }
            println()
            System.out.flush()
        }
        println("Simplified transcript to ${result.rounds.size} rounds and ${result.totalEdits()} total edits.")
        System.out.flush()
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <M> GroundedActivity<M>.simplifications(runActivity: (GroundedActivity<M>) -> Unit): Sequence<GroundedActivity<M>?> = sequence {
        if (activity is BlockActivity) {
            val instumentationLoader: Lazy<BlockInstrumentation> = lazy {
                // Build the instrumentation object and run an instrumented activity to fill it.
                BlockInstrumentation().also {
                    context (it) { runActivity(copy(activity = activity.instrument() as Activity<M>)) }
                }
            }

            // Now we can do simplifications with the option to collect instrumentation on the activity if needed.
            // By making it lazy, we avoid the performance cost if we can try simplifications that don't need instrumentation,
            // and we can avoid paying the performance penalty twice if we try multiple simplifications that do need it.
            context (instumentationLoader) {
                yieldAll(activity.simplifications().map { it?.let { copy(activity = it as Activity<M>) } })
            }
        }
    }

    private data class BlockInstrumentation(
        val expressionResults: MutableMap<Expression<*>, MutableList<Any?>> = mutableMapOf(),
    )

    context (instrumentation: BlockInstrumentation)
    private fun BlockActivity.instrument() = copy(statements = statements.map { it.instrument() })
    context (instrumentation: BlockInstrumentation)
    private fun StatementBlock.instrument(): StatementBlock = when (this) {
        is Await -> copy(condition = condition.instrument())
        is IncrementCounter -> copy(index = index.instrument(), amount = amount.instrument())
        is SetCounter -> copy(index = index.instrument(), value = value.instrument())
        is IncreaseSlope -> copy(index = index.instrument(), amount = amount.instrument())
        is SetSlope -> copy(index = index.instrument(), value = value.instrument())
        is SetSwitch -> copy(index = index.instrument(), value = value.instrument())
        is ToggleSwitch -> copy(index = index.instrument())
        is PauseTimer -> copy(index = index.instrument())
        is ResetTimer -> copy(index = index.instrument())
        is RestartTimer -> copy(index = index.instrument())
        is ResumeTimer -> copy(index = index.instrument())
        is ReportBoolean -> copy(value = value.instrument())
        is ReportDouble -> copy(value = value.instrument())
        is ReportDuration -> copy(value = value.instrument())
        is ReportInt -> copy(value = value.instrument())
        is SaveBoolean -> copy(value = value.instrument())
        is SaveDouble -> copy(value = value.instrument())
        is SaveDuration -> copy(value = value.instrument())
        is SaveInt -> copy(value = value.instrument())
        is Spawn -> copy(body = body.map { it.instrument() })
    }
    context (instrumentation: BlockInstrumentation)
    private fun <R> Expression<R>.instrument() = object : Expression<R> {
        context(_: TaskScope)
        override fun evaluate(model: BlockTestModel, locals: BlockLocals): R =
            this@instrument.evaluate(model, locals).also {
                instrumentation.expressionResults.computeIfAbsent(this@instrument) { mutableListOf() }.add(it)
            }
    }


    context (instrumentation: Lazy<BlockInstrumentation>)
    private fun BlockActivity.simplifications(): Sequence<BlockActivity?> = sequence {
        if (statements.isEmpty()) {
            // Try removing this activity entirely
            // We only suggest this when the activity is already empty, because it's so unlikely to work otherwise.
            yield(null)
        } else {
            for (i in statements.indices) {
                // Suggest removing the statement entirely
                yield(copy(statements = statements.toMutableList().also { it.removeAt(i) }))
                // If the statement cannot be removed, suggest simplifying it
                yieldAll(statements[i].simplifications().map { newStatement ->
                    copy(statements = statements.toMutableList().also { it[i] = newStatement })
                })
            }
        }
    }

    context (instrumentation: Lazy<BlockInstrumentation>)
    private fun StatementBlock.simplifications(): Sequence<StatementBlock> = sequence {
        when (this@simplifications) {
            is Spawn -> {
                for (i in body.indices) {
                    // Suggest removing the statement entirely
                    yield(copy(body = body.toMutableList().also { it.removeAt(i) }))
                    // If the statement cannot be removed, suggest simplifying it
                    yieldAll(body[i].simplifications().map { newStatement ->
                        copy(body = body.toMutableList().also { it[i] = newStatement })
                    })
                }
            }
            is IncrementCounter -> {
                yieldAll(index.simplifications().map { copy(index = it) })
                yieldAll(amount.simplifications().map { copy(amount = it) })
            }
            is SetCounter -> {
                yieldAll(index.simplifications().map { copy(index = it) })
                yieldAll(value.simplifications().map { copy(value = it) })
            }
            is IncreaseSlope -> {
                yieldAll(index.simplifications().map { copy(index = it) })
                yieldAll(amount.simplifications().map { copy(amount = it) })
            }
            is SetSlope -> {
                yieldAll(index.simplifications().map { copy(index = it) })
                yieldAll(value.simplifications().map { copy(value = it) })
            }
            is SetSwitch -> {
                yieldAll(index.simplifications().map { copy(index = it) })
                yieldAll(value.simplifications().map { copy(value = it) })
            }
            is ToggleSwitch -> yieldAll(index.simplifications().map { copy(index = it) })
            is PauseTimer -> yieldAll(index.simplifications().map { copy(index = it) })
            is ResetTimer -> yieldAll(index.simplifications().map { copy(index = it) })
            is RestartTimer -> yieldAll(index.simplifications().map { copy(index = it) })
            is ResumeTimer -> yieldAll(index.simplifications().map { copy(index = it) })
            is ReportBoolean -> yieldAll(value.simplifications().map { copy(value = it) })
            is ReportDouble -> yieldAll(value.simplifications().map { copy(value = it) })
            is ReportDuration -> yieldAll(value.simplifications().map { copy(value = it) })
            is ReportInt -> yieldAll(value.simplifications().map { copy(value = it) })
            is SaveBoolean -> yieldAll(value.simplifications().map { copy(value = it) })
            is SaveDouble -> yieldAll(value.simplifications().map { copy(value = it) })
            is SaveDuration -> yieldAll(value.simplifications().map { copy(value = it) })
            is SaveInt -> yieldAll(value.simplifications().map { copy(value = it) })
            is Await -> yieldAll(condition.simplifications().map { copy(condition = it) })
        }
    }

    @Suppress("UNCHECKED_CAST")
    context (instrumentation: Lazy<BlockInstrumentation>)
    private fun <R> Expression<R>.simplifications(): Sequence<Expression<R>> = sequence {
        if (this@simplifications is ConstantBoolean
            || this@simplifications is ConstantInt
            || this@simplifications is ConstantDouble
            || this@simplifications is ConstantDuration
            ) {
            // Already as simple as it gets, do nothing
        } else {
            val evaluations = instrumentation.value.expressionResults[this@simplifications]
            if (evaluations.isNullOrEmpty()) {
                // This expression didn't get run! We can't simplify this at this level.
            } else {
                // If instrumentation suggests this expression is effectively-constant, try substituting a constant
                evaluations.distinct().singleOrNull()?.let {
                    when (it) {
                        is Boolean -> ConstantBoolean(it)
                        is Int -> ConstantInt(it)
                        is Double -> ConstantDouble(it)
                        is Duration -> ConstantDuration(it)
                        else -> null
                    }
                }?.let {
                    yield(it as Expression<R>)
                }
            }

        }
    }

    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.simplifyByRemovingEmptyRounds(): FuzzTestTranscript<M> {
        print("Simplifying a transcript with ${rounds.size} rounds and ${totalEdits()} total edits, by removing empty rounds ")
        System.out.flush()
        var result = this
        for ((i, round) in result.rounds.withIndex().reversed()) {
            when (round) {
                is FuzzTestRound.RunEdit -> if (round.edits.isEmpty()) {
                    val candidate = result.copy(rounds = result.rounds.toMutableList().also { it.removeAt(i) })
                    if (candidate.exposesSomeBug()) {
                        result = candidate
                        print(".")
                    } else {
                        print("X")
                    }
                    System.out.flush()
                }
                is FuzzTestRound.SaveRestore -> {
                    // Nothing to do, continue
                }
            }
        }
        println("\nSimplified transcript to ${result.rounds.size} rounds and ${result.totalEdits()} total edits.")
        System.out.flush()
        return result
    }

    private fun <M> FuzzTestTranscript<M>.totalEdits(): Int = initialPlan.activities.size + rounds.sumOf {
        when (it) {
            is FuzzTestRound.RunEdit -> it.edits.condensedEdits().toList().size
            is FuzzTestRound.SaveRestore -> it.nextPlan.activities.size
        }
    }

    context (constructModel: (InitScope) -> M)
    private fun <M : Any> FuzzTestTranscript<M>.exposesSomeBug(): Boolean {
        try {
            // Run the transcript
            var tester = test(
                constructModel,
                startTime = initialPlan.startTime,
                endTime = initialPlan.endTime,
                activities = initialPlan.activities
            )
            for (round in rounds) {
                when (round) {
                    is FuzzTestRound.RunEdit -> tester.run(round.edits)
                    is FuzzTestRound.SaveRestore -> {
                        val incon = tester.save(round.nextPlan.startTime)
                        tester = test(
                            constructModel,
                            startTime = round.nextPlan.startTime,
                            endTime = round.nextPlan.endTime,
                            activities = round.nextPlan.activities,
                            incon = incon,
                        )
                    }
                }
            }
            // No exception thrown means we didn't expose any bugs
            return false
        } catch (e: Throwable) {
            // This particular exception indicates a bad plan; anything else is exposing a bug
            return !(e is IllegalArgumentException && "activities which were not part of this plan" in (e.message ?: ""))
        }
    }

    /** Returns a set of edits equivalent to [this], but paired additions/removals (like [move]) are grouped together. */
    private fun <M> PlanEdits<M>.condensedEdits(): Sequence<PlanEdits<M>> = sequence {
        val remainingAdditions = additions.toMutableList()
        for (removal in removals) {
            val addition = remainingAdditions.singleOrNull { it.name == removal.name }
                ?.also(remainingAdditions::remove)
            yield((addition?.let { +it } ?: PlanEdits()) - removal)
        }
        yieldAll(remainingAdditions.map { +it })
    }

    private fun <M> FuzzTestTranscript<M>.printAsCode(file: File) {
        file.printWriter().use { out ->
            val model =
                if (initialPlan.activities.first().activity is BlockActivity) BlockTestModel::class.simpleName
                else TestModel::class.simpleName
            val istName = "${IncrementalSimulationTester::class.simpleName}<$model>"

            out.println(
                """
                @Test
                fun `repro directly`() {
                    var tester = test(::$model,
                        startTime = ${initialPlan.startTime.repr()},
                        endTime = ${initialPlan.endTime.repr()},
                        activities = listOf(
            """.trimIndent()
            )
            initialPlan.activities.forEach { out.println("            ${it.repr()},") }
            out.println("        )")
            out.println("    )")
            out.println("    // Split each round into a separate method to avoid \"method too large\" errors")
            for (roundNumber in (1..rounds.size)) {
                out.println("    println(\"Running round $roundNumber...\")")
                out.println("    tester = round$roundNumber(tester)")
            }
            out.println("}")

            for ((roundIndex, round) in rounds.withIndex()) {
                val roundNumber = roundIndex + 1
                out.println("\nprivate fun round$roundNumber(tester: $istName): $istName {")
                when (round) {
                    is FuzzTestRound.RunEdit -> {
                        out.println("    tester.run(")
                        for (edit in round.edits.condensedEdits()) {
                            out.println(
                                if (edit.additions.isEmpty()) {
                                    "        + remove(${edit.removals.single().repr()})"
                                } else if (edit.removals.isEmpty()) {
                                    "        + add(${edit.additions.single().repr()})"
                                } else {
                                    val addition = edit.additions.single()
                                    val removal = edit.removals.single()
                                    when (addition) {
                                        removal.copy(time = addition.time) ->
                                            "        + move(${removal.repr()} to ${addition.time.repr()})"

                                        removal.copy(activity = addition.activity) ->
                                            "        + edit(${removal.repr()} to ${addition.activity})"

                                        else ->
                                            "        + add(${addition.repr()}) + remove(${removal.repr()})"
                                    }
                                }
                            )
                        }
                        out.println("    )")
                        out.println("    return tester")
                    }

                    is FuzzTestRound.SaveRestore -> {
                        out.println(
                            """
                            val inconTime = ${round.nextPlan.startTime.repr()}
                            val incon = tester.save(inconTime)
                            return test(::$model,
                                startTime = inconTime,
                                endTime = ${round.nextPlan.endTime.repr()},
                                incon = incon,
                                activities = listOf(
                        """.trimIndent().prependIndent()
                        )
                        for (activity in round.nextPlan.activities) {
                            out.println("        ${activity.repr()},")
                        }
                        out.println("        )")
                        out.println("    )")
                    }
                }
                out.println("}")
            }
        }
    }

    private fun <M> GroundedActivity<M>.repr(): String =
        "${GroundedActivity::class.simpleName}(${time.repr()}, Name(\"$name\"), $activity)"

    private fun Instant.repr(): String =
        "Instant.parse(\"$this\")"

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
            data class ReportBoolean(val value: Expression<Boolean>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(value.evaluate(model, locals).toString())
                }
            }
            data class ReportInt(val value: Expression<Int>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(value.evaluate(model, locals).toString())
                }
            }
            data class ReportDouble(val value: Expression<Double>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(value.evaluate(model, locals).toString())
                }
            }
            data class ReportDuration(val value: Expression<Duration>) : ReportBlock {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    stdout.report(value.evaluate(model, locals).toString())
                }
            }
        }
        sealed interface SaveValue : StatementBlock {
            data class SaveBoolean(val value: Expression<Boolean>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedBoolean = value.evaluate(model, locals)
                }
            }
            data class SaveInt(val value: Expression<Int>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedInt = value.evaluate(model, locals)
                }
            }
            data class SaveDouble(val value: Expression<Double>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedDouble = value.evaluate(model, locals)
                }
            }
            data class SaveDuration(val value: Expression<Duration>) : SaveValue {
                context(_: TaskScope)
                override suspend fun run(model: BlockTestModel, locals: BlockLocals) {
                    locals.savedDuration = value.evaluate(model, locals)
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

            // Like log above, this lets us inspect the results of an expression.
            // This version is intended primarily for automatic simplification, though.
            fun <R> Expression<R>.capture(block: (R) -> Unit) = object : Expression<R> {
                context(_: TaskScope)
                override fun evaluate(model: BlockTestModel, locals: BlockLocals): R =
                    this@capture.evaluate(model, locals).also(block)
            }
        }
    }
}
