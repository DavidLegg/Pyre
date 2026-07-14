package gov.nasa.jpl.parakeet.general.state_machines

import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.allow
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.prohibit
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.transitionGroups
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.transitionMap
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.transitionTable
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctions.transitions
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctionsTest.State.*
import gov.nasa.jpl.parakeet.general.state_machines.TransitionFunctionsTest.Stimulus.*
import gov.nasa.jpl.parakeet.general.testing.UnitTesting.runUnitTest
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

    @Test
    fun `allow throws an error by default for prohibited transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    allow(transitions(A to A, A to C, B to A))
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            // Transitioning to B is not allowed
            assertTransitionError { m.accept(B) }
            assertEquals(A, m.getValue())
            // Remaining in A is allowed
            m.accept(A)
            assertEquals(A, m.getValue())
            // Transitioning to C is allowed
            m.accept(C)
            assertEquals(C, m.getValue())
            // All transitions from C are prohibited
            assertTransitionError { m.accept(A) }
            assertTransitionError { m.accept(B) }
            assertTransitionError { m.accept(C) }
        }
    }

    @Test
    fun `allow can be given an alternative default transition function`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    allow(transitions(A to A, A to C, B to A), { _, _ -> B })
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            // Transitioning to B is not allowed, but falls through to default rule
            m.accept(B)
            assertEquals(B, m.getValue())
            m.accept(A)
            assertEquals(A, m.getValue())
            m.accept(C)
            assertEquals(C, m.getValue())
            // Transitioning to A is not allowed, but falls through to default rule
            m.accept(A)
            assertEquals(B, m.getValue())
        }
    }

    @Test
    fun `prohibit throws an error by default for prohibited transitions`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    prohibit(transitions(A to B, B to B, B to C))
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            // Transitioning to B is not allowed
            assertTransitionError { m.accept(B) }
            assertEquals(A, m.getValue())
            // Remaining in A is allowed
            m.accept(A)
            assertEquals(A, m.getValue())
            // Transitioning to C is allowed
            m.accept(C)
            assertEquals(C, m.getValue())
            // All transitions from C are allowed
            m.accept(C)
            assertEquals(C, m.getValue())

            m.accept(A)
            assertEquals(A, m.getValue())
            m.accept(C)
            assertEquals(C, m.getValue())

            m.accept(B)
            assertEquals(B, m.getValue())
            // Staying in B is not allowed
            assertTransitionError { m.accept(B) }
            assertEquals(B, m.getValue())
            // Transitioning to C is not allowed
            assertTransitionError { m.accept(C) }
            assertEquals(B, m.getValue())
            // Transitioning to A is allowed
            m.accept(A)
            assertEquals(A, m.getValue())
        }
    }

    @Test
    fun `prohibit can be given an alternative default transition function`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    A,
                    prohibit(transitions(A to B, C to A), { _, _ -> B })
                )
            }
        ) { m ->
            assertEquals(A, m.getValue())
            // Transitioning to B is not allowed, but falls through to default rule
            m.accept(B)
            assertEquals(B, m.getValue())
            m.accept(A)
            assertEquals(A, m.getValue())
            m.accept(C)
            assertEquals(C, m.getValue())
            // Transitioning to A is not allowed, but falls through to default rule
            m.accept(A)
            assertEquals(B, m.getValue())
        }
    }

    @Test
    fun `transitionGroups gives a cartesian product of transitions`() {
        assertEquals(
            transitions(
                A to A,
                A to C,
                B to A,
                B to C
            ),
            transitionGroups(
                listOf(A, B) to listOf(A, C)
            )
        )
        // Empty maps are permitted
        assertEquals(transitions<State>(), transitionGroups<State>())
        // Overlap is permitted
        assertEquals(
            transitions(
                1 to 2,
                1 to 3,
                1 to 4,
                1 to 1,
                1 to 5,
                3 to 1,
                3 to 3,
                3 to 5
            ),
            transitionGroups(
                listOf(1) to listOf(2, 3, 4),
                listOf(1, 3) to listOf(1, 3, 5)
            )
        )
    }

    @Test
    fun `transition functions can be chained`() {
        runUnitTest(
            start,
            {
                StateMachine(
                    "M",
                    1,
                    // Prohibit all transitions (1, 2, or 3) -> (4 or 5) except 1 -> 5, allow anything else
                    allow(transitions(1 to 5),
                        prohibit(transitionGroups(listOf(1, 2, 3) to listOf(4, 5))))
                )
            }
        ) { m ->
            assertEquals(1, m.getValue())
            m.accept(5)
            assertEquals(5, m.getValue())
            m.accept(4)
            assertEquals(4, m.getValue())
            m.accept(3)
            assertEquals(3, m.getValue())
            assertTransitionError { m.accept(4) }
            assertEquals(3, m.getValue())
            m.accept(1)
            assertEquals(1, m.getValue())
            m.accept(3)
            assertEquals(3, m.getValue())
            m.accept(1)
            assertEquals(1, m.getValue())
            assertTransitionError { m.accept(4) }
            assertEquals(1, m.getValue())
        }
    }

    context (_: TaskScope)
    private suspend inline fun assertTransitionError(block: suspend context (TaskScope) () -> Unit) {
        try {
            block()
            fail("Expected an IllegalArgumentException to be thrown, but no exception was thrown")
        } catch (_: IllegalArgumentException) {
            // Pass test
        } catch (e: AssertionError) {
            throw e
        } catch (e: Throwable) {
            fail("Expected an IllegalArgumentException to be thrown, but ${e.javaClass.simpleName} was thrown", e)
        }
    }
}