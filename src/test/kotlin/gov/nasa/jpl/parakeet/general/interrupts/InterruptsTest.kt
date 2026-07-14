package gov.nasa.jpl.parakeet.general.interrupts

import gov.nasa.jpl.pyre.general.interrupts.Interrupts.abortIf
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.task
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
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
                delay(10.minutes)
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
                delay(10.minutes)
            }
            assertEquals(start + 10.minutes, now())
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
                delay(10.minutes)
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
                delay(5.minutes)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    assertEquals(start + 5.minutes, now())
                    interruptHandled = true
                },
                it.flag2 to { fail() }
            ) {
                delay(10.minutes)
                fail()
            }
            // Delay longer than any time above, to make sure we don't have some background task running that shouldn't be there.
            // If there is, it'll hit the end and fail by 30 minutes.
            delay(30.minutes)
        }
        assert(interruptHandled)
    }

    @Test
    fun abort_handlers_run_synchronously() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5.minutes)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    delay(2.minutes)
                },
                it.flag2 to { fail() }
            ) {
                delay(10.minutes)
                fail()
            }
            assertEquals(start + 7.minutes, now())
        }
    }

    @Test
    fun aborts_from_abort_handler_dont_trigger_another_abort_handler() {
        runUnitTest {
            spawn("Cause Interruption", task {
                delay(5.minutes)
                it.flag1.set(true)
            })
            abortIf(
                it.flag1 to {
                    it.flag2.set(true)
                    delay(2.minutes)
                },
                it.flag2 to { fail() }
            ) {
                delay(10.minutes)
                fail()
            }
        }
    }
}