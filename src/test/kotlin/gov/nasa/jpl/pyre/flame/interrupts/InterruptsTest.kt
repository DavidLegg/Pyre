package gov.nasa.jpl.pyre.flame.interrupts

import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.flame.interrupts.Interrupts.withInterrupts
import gov.nasa.jpl.pyre.flame.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.spark.tasks.task
import org.junit.jupiter.api.Test
import kotlin.test.DefaultAsserter.fail
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Instant

class InterruptsTest {
    private class TestModel(context: InitScope) {
        val flag1: MutableBooleanResource
        val flag2: MutableBooleanResource

        init {
            with (context) {
                flag1 = discreteResource("flag1", false)
                flag2 = discreteResource("flag2", false)
            }
        }
    }

    private val start = Instant.parse("2025-01-01T00:00:00Z")
    private fun runUnitTest(testTask: suspend context(TaskScope) (TestModel) -> Unit) {
        runUnitTest(
            start,
            ::TestModel,
            testTask,
        )
    }

    @Test
    fun interruptable_task_without_interrupts_runs_to_completion() {
        var nominalTaskComplete = false
        runUnitTest {
            withInterrupts({
                delay(10 * MINUTE)
                nominalTaskComplete = true
            })
        }
        assert(nominalTaskComplete)
    }

    @Test
    fun interruptable_task_runs_synchronously() {
        runUnitTest {
            assertEquals(start, now())
            withInterrupts({
                delay(10 * MINUTE)
            })
            assertEquals(start + (10 * MINUTE).toKotlinDuration(), now())
        }
    }

    @Test
    fun interruptable_task_with_no_interruptions_runs_to_completion() {
        var nominalTaskComplete = false
        runUnitTest {
            withInterrupts(
                {
                    delay(10 * MINUTE)
                    nominalTaskComplete = true
                },
                it.flag1 to { fail() },
                it.flag2 to { fail() },
            )
        }
        assert(nominalTaskComplete)
    }

    @Test
    fun interruptions_stop_nominal_behavior_and_run_interrupt_handler() {
        var interruptHandled = false
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            withInterrupts(
                {
                    delay(10 * MINUTE)
                    fail()
                },
                it.flag1 to {
                    assertEquals(start + (5 * MINUTE).toKotlinDuration(), now())
                    interruptHandled = true
                },
                it.flag2 to { fail() },
            )
            // Delay longer than any time above, to make sure we don't have some background task running that shouldn't be there.
            // If there is, it'll hit the end and fail by 30 minutes.
            delay(30 * MINUTE)
        }
        assert(interruptHandled)
    }

    @Test
    fun interrupt_handlers_run_synchronously() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            withInterrupts(
                {
                    delay(10 * MINUTE)
                    fail()
                },
                it.flag1 to {
                    delay(2 * MINUTE)
                },
                it.flag2 to { fail() },
            )
            assertEquals(start + (7 * MINUTE).toKotlinDuration(), now())
        }
    }

    @Test
    fun interruptions_from_interrupt_handler_dont_trigger_another_interrupt_handler() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            withInterrupts(
                {
                    delay(10 * MINUTE)
                    fail()
                },
                it.flag1 to {
                    it.flag2.set(true)
                    delay(2 * MINUTE)
                },
                it.flag2 to { fail() },
            )
        }
    }
}