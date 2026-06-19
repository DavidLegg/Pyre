package gov.nasa.jpl.pyre.general.state_machines

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface StateMachine<State, Stimulus> : DiscreteResource<State> {
    context (_: TaskScope)
    fun accept(stimulus: Stimulus)
}

/**
 * Create a generic [StateMachine].
 *
 * Such a state machine may have infinite states.
 * For more convenient construction of finite state machines, see [FiniteStateMachine].
 *
 * @param transitionFunction Indicates which state to transition to, given current state and a stimulus.
 * @param entryAction Action to perform when entering a state.
 * @param exitAction Action to perform when exiting a state.
 * @param transitionAction Action to perform when transitioning between states.
 */
context (_: InitScope)
inline fun <reified State, Stimulus> StateMachine(
    name: String,
    initialState: State,
    noinline transitionFunction: (State, Stimulus) -> State,
    noinline entryAction: (State) -> Unit = {},
    noinline exitAction: (State) -> Unit = {},
    noinline transitionAction: (State, State) -> Unit = { _, _ -> },
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
    entryAction: (State) -> Unit = {},
    exitAction: (State) -> Unit = {},
    transitionAction: (State, State) -> Unit = { _, _ -> },
    stateType: KType,
): StateMachine<State, Stimulus> {
    val state: MutableDiscreteResource<State> = discreteResource(name, initialState, stateType)

    return object : StateMachine<State, Stimulus>, DiscreteResource<State> by state {
        context (_: TaskScope)
        override fun accept(stimulus: Stimulus) {
            val startState = state.getValue()
            val endState = transitionFunction(startState, stimulus)
            if (endState != startState) {
                exitAction(startState)
                state.set(endState)
                transitionAction(startState, endState)
                entryAction(endState)
            }
            // Else, no transition occurred, nothing to do.
        }

        override val name: Name = state.name
        override fun toString(): String = state.name.toString()
    }
}
