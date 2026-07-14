package gov.nasa.jpl.parakeet.general.state_machines

import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

typealias Action = suspend context (TaskScope) () -> Unit

object Actions {
    /**
     * Construct an entry- or exit-action function by enumerating actions for specific states.
     */
    fun <State> stateActions(vararg entries: Pair<State, Action>): (State) -> Action? =
        entries.toMap()::get

    /**
     * Construct a transition-action function by enumerating actions for specific transitions.
     */
    fun <State> transitionActions(vararg entries: Pair<Pair<State, State>, Action>): (State, State) -> Action? =
        entries.toMap().let { map ->
            { startState, endState -> map[startState to endState] }
        }
}
