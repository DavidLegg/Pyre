package gov.nasa.jpl.pyre.general.state_machines

import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// Some users may prefer the name start with "Finite", to reinforce the finite number of states and because it's more familiar.
// This also quiets the warning that the function below should start with a lowercase letter, by indicating it's really
// a constructor for this type.
typealias FiniteStateMachine<State, Stimulus> = StateMachine<State, Stimulus>

// TODO: There's a JVM clash between the two overloads of [FiniteStateMachine]. Solve it.

context (_: InitScope)
inline fun <reified State> FiniteStateMachine(
    name: String,
    initialState: State,
    transitions: Map<State, Set<State>>,
    noinline miscTransitions: (State, State) -> State =
        { state, stimulus -> throw IllegalArgumentException("Cannot transition from $state to $stimulus") },
    entryActions: Map<State, () -> Unit> = mapOf(),
    exitActions: Map<State, () -> Unit> = mapOf(),
    transitionActions: Map<State, Map<State, () -> Unit>> = mapOf(),
) = FiniteStateMachine(
    name,
    initialState,
    transitions.mapValues { (_, allowedStates) -> allowedStates.associateWith { it } },
    miscTransitions,
    entryActions,
    exitActions,
    transitionActions,
    typeOf<State>(),
)

context (_: InitScope)
inline fun <reified State, Stimulus> FiniteStateMachine(
    name: String,
    initialState: State,
    transitions: Map<State, Map<Stimulus, State>>,
    noinline miscTransitions: (State, Stimulus) -> State =
        { state, stimulus -> throw IllegalArgumentException("Stimulus $stimulus is not permitted while in state $state") },
    entryActions: Map<State, () -> Unit> = mapOf(),
    exitActions: Map<State, () -> Unit> = mapOf(),
    transitionActions: Map<State, Map<State, () -> Unit>> = mapOf(),
) = FiniteStateMachine(
    name,
    initialState,
    transitions,
    miscTransitions,
    entryActions,
    exitActions,
    transitionActions,
    typeOf<State>(),
)

context (_: InitScope)
fun <State, Stimulus> FiniteStateMachine(
    name: String,
    initialState: State,
    transitions: Map<State, Map<Stimulus, State>>,
    miscTransitions: (State, Stimulus) -> State =
        { state, stimulus -> throw IllegalArgumentException("Stimulus $stimulus is not permitted while in state $state") },
    entryActions: Map<State, () -> Unit> = mapOf(),
    exitActions: Map<State, () -> Unit> = mapOf(),
    transitionActions: Map<State, Map<State, () -> Unit>> = mapOf(),
    stateType: KType,
) = StateMachine<State, Stimulus>(
    name = name,
    initialState = initialState,
    transitionFunction = { state, stimulus ->
        transitions.get(state)?.get(stimulus) ?: miscTransitions(state, stimulus)
    },
    entryAction = { entryActions[it]?.invoke() },
    exitAction = { exitActions[it]?.invoke() },
    transitionAction = { start, end -> transitionActions.get(start)?.get(end)?.invoke() },
    stateType = stateType,
)
