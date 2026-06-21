package gov.nasa.jpl.pyre.general.state_machines

import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.state_machines.TransitionFunctions.transitionMap
import gov.nasa.jpl.pyre.general.state_machines.TransitionFunctions.transitionTable
import gov.nasa.jpl.pyre.general.state_machines.TransitionFunctionsTest.State.*
import gov.nasa.jpl.pyre.general.state_machines.TransitionFunctionsTest.Stimulus.*
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import kotlin.test.Test
import kotlin.time.Instant

class TransitionFunctionsTest {
    private val start = Instant.parse("2020-01-01T00:00:00Z")

    private enum class State { A, B, C, D }
    private enum class Stimulus { X, Y, Z }

    @Test
    fun `transitionMap enumerates allowed transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    transitionMap(mapOf(
                        A to mapOf(X to D, Y to A, Z to B),
                        B to mapOf(X to A, Y to B, Z to C),
                        C to mapOf(X to B, Y to C, Z to D),
                        D to mapOf(X to C, Y to D, Z to A),
                    )),
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            m.accept(Z)
            assertEquals(B, m.getValue())
            m.accept(Y)
            assertEquals(B, m.getValue())
            m.accept(X)
            assertEquals(A, m.getValue())
            m.accept(Z)
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            m.accept(Y)
            assertEquals(C, m.getValue())
        }
    }

    @Test
    fun `transitionMap throws an error by default for non-enumerated transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    B,
                    transitionMap(mapOf(
                        A to mapOf(X to D, Y to A, Z to B),
                        B to mapOf(X to A, Y to B, Z to C),
                        C to mapOf(X to B, Z to D),
                    )),
                )
            }
        ) { m ->
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            // Since Y is not listed as a stimulus for state C, attempting to accept it produces a transition error.
            assertTransitionError { m.accept(Y) }
            // If we catch the exception, the machine should remain in this state.
            assertEquals(C, m.getValue())
            // Other stimuli can then be given to transition again.
            m.accept(Z)
            assertEquals(D, m.getValue())
            // Since D is not listed as a starting state, any stimulus produces a transition error
            assertTransitionError { m.accept(X) }
            assertTransitionError { m.accept(Y) }
            assertTransitionError { m.accept(Z) }
        }
    }

    @Test
    fun `transitionMap can be given an alternative default transition function`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    B,
                    transitionMap(mapOf(
                        A to mapOf(X to D, Y to A, Z to B),
                        B to mapOf(X to A, Y to B, Z to C),
                        C to mapOf(X to B, Z to D),
                    )) { _, _ -> A },
                )
            }
        ) { m ->
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            // Since Y is not listed as a stimulus for state C, it falls through to the default function
            // For this test, that puts us in state A
            m.accept(Y)
            assertEquals(A, m.getValue())
            m.accept(X)
            assertEquals(D, m.getValue())
            // Since D is not listed as a starting state, any stimulus falls through to the default function.
            m.accept(X)
            assertEquals(A, m.getValue())

            m.accept(X)
            assertEquals(D, m.getValue())
            m.accept(Y)
            assertEquals(A, m.getValue())

            m.accept(X)
            assertEquals(D, m.getValue())
            m.accept(Z)
            assertEquals(A, m.getValue())
        }
    }

    @Test
    fun `transitionTable enumerates allowed transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    transitionTable<State, Stimulus>(X, Y, Z)
                        .row(A, D, A, B)
                        .row(B, A, B, C)
                        .row(C, B, C, D)
                        .row(D, C, D, A)
                        .build(),
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            m.accept(Z)
            assertEquals(B, m.getValue())
            m.accept(Y)
            assertEquals(B, m.getValue())
            m.accept(X)
            assertEquals(A, m.getValue())
            m.accept(Z)
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            m.accept(Y)
            assertEquals(C, m.getValue())
        }
    }

    @Test
    fun `transitionTable throws an error by default for non-enumerated transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    B,
                    transitionTable<State, Stimulus>(Y, Z)
                        .row(A, A, B)
                        .row(B, B, C)
                        .row(C, null, D)
                        .build(),
                )
            }
        ) { m ->
            assertEquals(B, m.getValue())
            // Since X is not listed as a stimulus (full stop), attempting to accept it produces a transition error.
            assertTransitionError { m.accept(X) }
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            // Since Y is not listed as a stimulus for state C, attempting to accept it produces a transition error.
            assertTransitionError { m.accept(Y) }
            // If we catch the exception, the machine should remain in this state.
            assertEquals(C, m.getValue())
            // Other stimuli can then be given to transition again.
            m.accept(Z)
            assertEquals(D, m.getValue())
            // Since D is not listed as a starting state, any stimulus produces a transition error
            assertTransitionError { m.accept(X) }
            assertTransitionError { m.accept(Y) }
            assertTransitionError { m.accept(Z) }
        }
    }

    @Test
    fun `transitionTable can be given an alternative default transition function`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    B,
                    transitionTable<State, Stimulus>(X, Y, Z)
                        .row(A, D, A, B)
                        .row(B, A, B, C)
                        .row(C, B, null, D)
                        .build { _, _ -> A },
                )
            }
        ) { m ->
            assertEquals(B, m.getValue())
            m.accept(Z)
            assertEquals(C, m.getValue())
            // Since Y is not listed as a stimulus for state C, it falls through to the default function
            // For this test, that puts us in state A
            m.accept(Y)
            assertEquals(A, m.getValue())
            m.accept(X)
            assertEquals(D, m.getValue())
            // Since D is not listed as a starting state, any stimulus falls through to the default function.
            m.accept(X)
            assertEquals(A, m.getValue())

            m.accept(X)
            assertEquals(D, m.getValue())
            m.accept(Y)
            assertEquals(A, m.getValue())

            m.accept(X)
            assertEquals(D, m.getValue())
            m.accept(Z)
            assertEquals(A, m.getValue())
        }
    }

    // TODO: Test allowedTransitions
    // TODO: Test prohibitedTransitions

    context (_: TaskScope)
    private suspend inline fun assertTransitionError(block: suspend context (TaskScope) () -> Unit) {
        try {
            block()
            fail("Expected an IllegalArgumentException to be thrown, but no exception was thrown")
        } catch (_: IllegalArgumentException) {
            // Pass test
        } catch (e: Throwable) {
            fail("Expected an IllegalArgumentException to be thrown, but ${e.javaClass.simpleName} was thrown")
        }
    }
}