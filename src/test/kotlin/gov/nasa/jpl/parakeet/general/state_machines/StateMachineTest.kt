package gov.nasa.jpl.parakeet.general.state_machines

import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.stdout
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.general.results.ResourceResults
import gov.nasa.jpl.pyre.general.state_machines.TransitionFunctions.acceptTransition
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.kernel.Name
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StateMachineTest {
    private val start = Instant.parse("2020-01-01T00:00:00Z")

    @Test
    fun `a state machine does not transition without stimuli`() {
        runUnitTest(
            start,
            {
                StateMachine("M", 1, acceptTransition())
            }
        ) { m ->
            // The machine starts in the given initial state
            assertEquals("M", m.name.simpleName)
            assertEquals(1, m.getValue())
            // After waiting a while, it remains in that state
            delay(1.hours)
            assertEquals(1, m.getValue())
        }
    }

    @Test
    fun `a state machine transitions when given a stimulus`() {
        runUnitTest(
            start,
            {
                StateMachine("M", 1, acceptTransition())
            }
        ) { m ->
            assertEquals(1, m.getValue())
            m.accept(2)
            assertEquals(2, m.getValue())
            m.accept(3)
            assertEquals(3, m.getValue())
            delay(1.hours)
            assertEquals(3, m.getValue())
            m.accept(4)
            assertEquals(4, m.getValue())
        }
    }

    @Test
    fun `a state machine uses the transition function to translate stimuli into states`() {
        runUnitTest(
            start,
            {
                StateMachine("M", 1, {
                    state: Int, stimulus: Boolean ->
                    if (stimulus) state + 1 else state - 1
                })
            }
        ) { m ->
            assertEquals(1, m.getValue())
            m.accept(true)
            assertEquals(2, m.getValue())
            m.accept(true)
            assertEquals(3, m.getValue())
            m.accept(false)
            assertEquals(2, m.getValue())
            delay(1.hours)
            assertEquals(2, m.getValue())
            m.accept(false)
            assertEquals(1, m.getValue())
        }
    }

    @Test
    fun `a state machine runs entry actions when transitioning to that state from another state`() {
        val results = runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    1,
                    acceptTransition(),
                    entryAction = { n -> { stdout.report("Entering state $n") } },
                )
            }
        ) { m ->
            // Both of these should run the entry action.
            // Even though they happen back-to-back within a task (so state 2 isn't observed from other tasks)
            // the transition still happens as part of this task.
            m.accept(2)
            m.accept(3)
            // Commanding a transition to the current state doesn't count as "entering" that state, though.
            // This should not run the entry action.
            m.accept(3)
            delay(1.hours)
            // Both of these should run the entry action though, even though we've visited these states before
            m.accept(1)
            m.accept(3)
            // Since the entry action may occur a tick or two after the transition itself, we need to wait.
            delay(1.seconds)
        }
        // To verify we ran the entry actions we expected to run, we can look at the results:
        @Suppress("UNCHECKED_CAST")
        val stdout = results.resources.getValue(Name("stdout")) as ResourceResults<String>
        with (stdout.data.map { it.time to it.data }.iterator()) {
            assertEquals(start to "Entering state 2", next())
            assertEquals(start to "Entering state 3", next())
            assertEquals(start + 1.hours to "Entering state 1", next())
            assertEquals(start + 1.hours to "Entering state 3", next())
            assertFalse(hasNext())
        }
    }

    @Test
    fun `a state machine runs exit actions when transitioning from that state to another state`() {
        val results = runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    1,
                    acceptTransition(),
                    exitAction = { n -> { stdout.report("Exiting state $n") } },
                )
            }
        ) { m ->
            // Both of these should run the exit action.
            // Even though they happen back-to-back within a task (so state 2 isn't observed from other tasks)
            // the transition still happens as part of this task.
            m.accept(2)
            m.accept(3)
            // Commanding a transition to the current state doesn't count as "exiting" that state, though.
            // This should not run the exit action.
            m.accept(3)
            delay(1.hours)
            // Both of these should run the exit action though, even though we've visited these states before
            m.accept(1)
            m.accept(3)
            // Since the exit action may occur a tick or two after the transition itself, we need to wait.
            delay(1.seconds)
        }
        // To verify we ran the entry actions we expected to run, we can look at the results:
        @Suppress("UNCHECKED_CAST")
        val stdout = results.resources.getValue(Name("stdout")) as ResourceResults<String>
        with (stdout.data.map { it.time to it.data }.iterator()) {
            assertEquals(start to "Exiting state 1", next())
            assertEquals(start to "Exiting state 2", next())
            assertEquals(start + 1.hours to "Exiting state 3", next())
            assertEquals(start + 1.hours to "Exiting state 1", next())
            assertFalse(hasNext())
        }
    }

    @Test
    fun `a state machine runs transition actions when transitioning from one state to another state`() {
        val results = runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    1,
                    acceptTransition(),
                    transitionAction = { from, to -> { stdout.report("Transitioning from $from to $to") } },
                )
            }
        ) { m ->
            // Both of these should run the exit action.
            // Even though they happen back-to-back within a task (so state 2 isn't observed from other tasks)
            // the transition still happens as part of this task.
            m.accept(2)
            m.accept(3)
            // Commanding a transition to the current state doesn't count as "exiting" that state, though.
            // This should not run the exit action.
            m.accept(3)
            delay(1.hours)
            // Both of these should run the exit action though, even though we've visited these states before
            m.accept(1)
            m.accept(3)
            // Since the exit action may occur a tick or two after the transition itself, we need to wait.
            delay(1.seconds)
        }
        // To verify we ran the entry actions we expected to run, we can look at the results:
        @Suppress("UNCHECKED_CAST")
        val stdout = results.resources.getValue(Name("stdout")) as ResourceResults<String>
        with (stdout.data.map { it.time to it.data }.iterator()) {
            assertEquals(start to "Transitioning from 1 to 2", next())
            assertEquals(start to "Transitioning from 2 to 3", next())
            assertEquals(start + 1.hours to "Transitioning from 3 to 1", next())
            assertEquals(start + 1.hours to "Transitioning from 1 to 3", next())
            assertFalse(hasNext())
        }
    }
}