package gov.nasa.jpl.pyre.general.state_machines

typealias TransitionFunction<State, Stimulus> = (State, Stimulus) -> State
typealias DirectTransitionFunction<State> = TransitionFunction<State, State>

/**
 * Helper functions for filling in the transition function of [StateMachine],
 * especially for finite state machines.
 */
object TransitionFunctions {
    /**
     * Construct a transition function from a map enumerating transitions.
     * If a (state, stimulus) pair isn't listed in [map], use [defaultTransition] instead.
     */
    fun <State, Stimulus> transitionMap(
        map: Map<State, Map<Stimulus, State>>,
        defaultTransition: TransitionFunction<State, Stimulus> = throwTransitionError()
    ): TransitionFunction<State, Stimulus> =
        { state, stimulus -> map.get(state)?.get(stimulus) ?: defaultTransition(state, stimulus) }

    /**
     * Construct a transition function by enumerating transitions in a tabular format.
     * Similar to [transitionMap], but may be more concise for dense transition tables.
     */
    fun <State, Stimulus> transitionTable(vararg columns: Stimulus) = object : TransitionTableBuilder<State, Stimulus> {
        private val transitionMap = mutableMapOf<State, Map<Stimulus, State>>()

        override fun row(startState: State, vararg endStates: State?): TransitionTableBuilder<State, Stimulus> {
            require(startState !in transitionMap) { "Duplicate start state $startState" }
            require(endStates.size == columns.size) { "Must provide ${columns.size} end states to align with columns" }
            transitionMap[startState] = (columns zip endStates)
                .mapNotNull { (c, e) -> if (e == null) null else (c to e) }
                .toMap()
            return this
        }

        override fun build(defaultTransition: TransitionFunction<State, Stimulus>) =
            transitionMap(transitionMap, defaultTransition)
    }

    interface TransitionTableBuilder<State, Stimulus> {
        fun row(startState: State, vararg endStates: State?): TransitionTableBuilder<State, Stimulus>
        fun build(defaultTransition: TransitionFunction<State, Stimulus> = throwTransitionError()): (State, Stimulus) -> State
    }

    /**
     * When building a [DirectStateMachine], this specifies the transition function by giving the allowed transitions.
     *
     * If a transition not allowed by [map] is attempted, [onProhibitedTransition] is invoked.
     */
    fun <State> allow(
        map: Map<State, Set<State>>,
        onProhibitedTransition: DirectTransitionFunction<State> = throwTransitionError(),
    ): DirectTransitionFunction<State> = { state, newState ->
        if (map[state]?.contains(newState) ?: false) newState else onProhibitedTransition(state, newState)
    }

    /**
     * When building a [DirectStateMachine], this specifies the transition function by giving the prohibited transitions.
     * Any other transition is implicitly allowed.
     *
     * If a transition prohibited by [map] is attempted, [onProhibitedTransition] is invoked.
     */
    fun <State> prohibit(
        map: Map<State, Set<State>>,
        onProhibitedTransition: DirectTransitionFunction<State> = throwTransitionError(),
    ): DirectTransitionFunction<State> = { state, newState ->
        if (map[state]?.contains(newState) ?: false) onProhibitedTransition(state, newState) else newState
    }

    /**
     * Construct a transition map suitable for [allow] or [prohibit]
     * by listing individual transitions, rather than grouping by start state.
     */
    fun <State> transitions(vararg transitions: Pair<State, State>): Map<State, Set<State>> =
        transitions.groupBy { it.first }.map { (k, v) -> k to v.map { it.second }.toSet() }.toMap()

    /**
     * Construct a transition map suitable for [allow] or [prohibit]
     * by listing groups of transitions.
     * The group `(X to Y)` indicates the cartesian product `X x Y`, all transitions `x -> y` for every `x in X` and `y in Y`.
     */
    fun <State> transitionGroups(vararg transitionGroups: Pair<out Collection<State>, out Collection<State>>): Map<State, Set<State>> =
        transitions(*transitionGroups.flatMap { (xs, ys) -> xs * ys }.toTypedArray())

    private operator fun <T, S> Collection<T>.times(other: Collection<S>): List<Pair<T, S>> =
        flatMap { t -> other.map { s -> t to s } }

    fun <State, Stimulus> throwTransitionError(): TransitionFunction<State, Stimulus> =
        { state, stimulus -> throw IllegalArgumentException("Cannot accept $stimulus while state machine is in state $state") }

    /**
     * A trivial transition function for [DirectStateMachine] which accepts every transition.
     */
    fun <State> acceptTransition(): DirectTransitionFunction<State> = { _, newState -> newState }
}
