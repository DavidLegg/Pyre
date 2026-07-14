package gov.nasa.jpl.parakeet.general.state_machines

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface StateMachine<State, Stimulus> : DiscreteResource<State> {
    context (_: TaskScope)
    suspend fun accept(stimulus: Stimulus)
}

/**
 * A [StateMachine] in which the stimulus is directly the new state to adopt.
 */
typealias DirectStateMachine<State> = StateMachine<State, State>

/**
 * Create a generic [StateMachine].
 *
 * Such a state machine may have finitely or infinitely many states.
 * For finite state machines, we have utilities to support constructing these arguments:
 * - [TransitionFunctions] for [transitionFunction]
 * - [Actions] for [entryAction], [exitAction], and [transitionAction]
 *
 * @param transitionFunction Indicates which state to transition to, given current state and a stimulus.
 * @param entryAction Action to perform when entering a state, or null if no action is needed.
 * @param exitAction Action to perform when exiting a state, or null if no action is needed.
 * @param transitionAction Action to perform when transitioning between states, or null if no action is needed.
 */
context (_: InitScope)
inline fun <reified State, Stimulus> StateMachine(
    name: String,
    initialState: State,
    noinline transitionFunction: TransitionFunction<State, Stimulus>,
    noinline entryAction: (State) -> Action? = { null },
    noinline exitAction: (State) -> Action? = { null },
    noinline transitionAction: (State, State) -> Action? = { _, _ -> null },
) = StateMachine(
    name,
    initialState,
    transitionFunction,
    entryAction,
    exitAction,
    transitionAction,
    typeOf<State>()
)

context (_: InitScope)
fun <State, Stimulus> StateMachine(
    name: String,
    initialState: State,
    transitionFunction: (State, Stimulus) -> State,
    entryAction: (State) -> (suspend context (TaskScope) () -> Unit)? = { null },
    exitAction: (State) -> (suspend context (TaskScope) () -> Unit)? = { null },
    transitionAction: (State, State) -> (suspend context (TaskScope) () -> Unit)? = { _, _ -> null },
    stateType: KType,
): StateMachine<State, Stimulus> {
    val state: MutableDiscreteResource<State> = discreteResource(name, initialState, stateType)

    return object : StateMachine<State, Stimulus>, DiscreteResource<State> by state {
        context (_: TaskScope)
        override suspend fun accept(stimulus: Stimulus) {
            val startState = state.getValue()
            val endState = transitionFunction(startState, stimulus)
            if (endState != startState) {
                // Entry, exit, and transition actions return the task to run, rather than just running the task directly.
                // In the common case that no action is needed, we can avoid spawning a task.
                exitAction(startState)?.let {
                    spawn(Name(name) / "Exit $startState", task(it))
                }
                state.set(endState)
                transitionAction(startState, endState)?.let {
                    spawn(Name(name) / "Transition $startState -> $endState", task(it))
                }
                entryAction(endState)?.let {
                    spawn(Name(name) / "Enter $endState", task(it))
                }
            }
            // Else, no transition occurred, nothing to do.
        }

        override val name: Name = state.name
        override fun toString(): String = state.name.toString()
    }
}
