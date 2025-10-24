package gov.nasa.jpl.pyre.flame.interrupts

import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.flame.interrupts.Interrupts.abortIf
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
    fun abort_if_without_abort_conditions_runs_to_completion() {
        var nominalTaskComplete = false
        runUnitTest {
            abortIf {
                delay(10 * MINUTE)
                nominalTaskComplete = true
            }
        }
        assert(nominalTaskComplete)
    }

    @Test
    fun abort_if_runs_synchronously() {
        runUnitTest {
            assertEquals(start, now())
            abortIf {
                delay(10 * MINUTE)
            }
            assertEquals(start + (10 * MINUTE).toKotlinDuration(), now())
        }
    }

    @Test
    fun abort_if_with_no_abort_runs_to_completion() {
        var nominalTaskComplete = false
        runUnitTest {
            abortIf(
                it.flag1 to { fail() },
                it.flag2 to { fail() }
            ) {
                delay(10 * MINUTE)
                nominalTaskComplete = true
            }
        }
        assert(nominalTaskComplete)
    }

    @Test
    fun aborts_stop_nominal_behavior_and_run_abort_handler() {
        var interruptHandled = false
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    assertEquals(start + (5 * MINUTE).toKotlinDuration(), now())
                    interruptHandled = true
                },
                it.flag2 to { fail() }
            ) {
                delay(10 * MINUTE)
                fail()
            }
            // Delay longer than any time above, to make sure we don't have some background task running that shouldn't be there.
            // If there is, it'll hit the end and fail by 30 minutes.
            delay(30 * MINUTE)
        }
        assert(interruptHandled)
    }

    @Test
    fun abort_handlers_run_synchronously() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    delay(2 * MINUTE)
                },
                it.flag2 to { fail() }
            ) {
                delay(10 * MINUTE)
                fail()
            }
            assertEquals(start + (7 * MINUTE).toKotlinDuration(), now())
        }
    }

    @Test
    fun aborts_from_abort_handler_dont_trigger_another_abort_handler() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5 * MINUTE)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    it.flag2.set(true)
                    delay(2 * MINUTE)
                },
                it.flag2 to { fail() }
            ) {
                delay(10 * MINUTE)
                fail()
            }
        }
    }
}