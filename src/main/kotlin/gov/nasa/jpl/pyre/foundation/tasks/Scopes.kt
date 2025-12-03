package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationEpoch
import gov.nasa.jpl.pyre.kernel.CellSet.Cell
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * A context for all the "global" conveniences offered by foundation during simulation.
 */
interface SimulationScope {
    /**
     * A hierarchical [Name] that can be used to indicate "where" in the model or simulation we are.
     *
     * This is primarily used to name resources and tasks, for reporting results and debugging purposes.
     */
    val contextName: Name?

    /**
     * Primary simulation clock. Simulation time should be derived from this clock.
     */
    val simulationClock: Resource<Timer>

    /**
     * Absolute epoch time. [simulationClock] is relative to this time.
     */
    val simulationEpoch: Instant

    companion object {
        context (scope: SimulationScope)
        val simulationClock get() = scope.simulationClock

        context (scope: SimulationScope)
        val simulationEpoch get() = scope.simulationEpoch

        context (scope: SimulationScope)
        fun subSimulationScope(contextName: Name) = object : SimulationScope by scope {
            override val contextName = scope.contextName / contextName
        }
    }
}

interface ResourceScope : SimulationScope {
    fun <V> read(cell: Cell<V>): V

    companion object {
        context (scope: ResourceScope)
        fun <V> read(cell: Cell<V>): V = scope.read(cell)

        context (scope: ResourceScope)
        fun now() = simulationEpoch + simulationClock.getValue().toKotlinDuration()
    }
}

interface ConditionScope : ResourceScope

interface TaskScope : ResourceScope {
    fun <V> emit(cell: Cell<V>, effect: Effect<V>)
    fun <T> report(value: T, type: KType)
    suspend fun await(condition: Condition)
    suspend fun <S> spawn(childName: Name, child: suspend context (TaskScope) () -> TaskScopeResult<S>)

    companion object {
        context (scope: TaskScope)
        inline fun <reified T> report(value: T) = report(value, typeOf<T>())

        context (scope: TaskScope)
        fun <V> emit(cell: Cell<V>, effect: Effect<V>) = scope.emit(cell, effect)

        context (scope: TaskScope)
        fun <T> report(value: T, type: KType) = scope.report(value, type)

        context (scope: TaskScope)
        suspend fun await(condition: Condition) = scope.await(condition)

        context (scope: TaskScope)
        suspend fun <S> spawn(childName: Name, child: suspend context (TaskScope) () -> TaskScopeResult<S>) = scope.spawn(childName, child)

        context (scope: TaskScope)
        suspend fun <S> spawn(childName: String, child: suspend context (TaskScope) () -> TaskScopeResult<S>) = spawn(Name(childName), child)
    }
}

// Note: We specifically don't implement BasicInitScope here.
//   We want to supplant its methods with higher-level methods that change its signature somehow.
// TODO: Allocate/read/emit on Resource, instead of Cell, to force all cell allocation to hide behind a resource.
interface InitScope : SimulationScope, ResourceScope {
    /**
     * Run block whenever the simulation starts.
     *
     * WARNING! This creates an impure task.
     * If you do not have a compelling reason to use this method, prefer the pure method [spawn] instead.
     *
     * This intentionally violates the general rule that saving and restoring a simulation should not affect the results.
     * To maintain overall simulation "sanity", this method should reduce to a no-op for a pure save/restore cycle.
     *
     * Example use cases for this method include reporting the initial resource values or updating initial model states
     * for consistency if other "cleaner" approaches don't suffice.
     * In these cases, a pure save/restore cycle results in (at most) a few redundant resource value reports.
     * These tasks are not required for "pure" simulations, but judicious use lets us tolerate real-world impurities.
     * For example, we may manually adjust a fincon between runs, and want the model to update to a consistent state,
     * as well as report the state of all resources, which may have changed due to that manual fincon adjustment.
     */
    fun onStartup(name: Name, block: suspend context (TaskScope) () -> Unit)

    fun <T: Any> allocate(
        name: Name,
        value: T,
        valueType: KType,
        stepBy: (T, Duration) -> T,
        mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ): Cell<T>

    /**
     * Spawn a regular task, which will run when the simulation starts
     */
    fun <T> spawn(name: Name, block: suspend context (TaskScope) () -> TaskScopeResult<T>)

    companion object {
        context (scope: InitScope)
        fun onStartup(name: Name, block: suspend context (TaskScope) () -> Unit) = scope.onStartup(name, block)

        context (scope: InitScope)
        fun onStartup(name: String, block: suspend context (TaskScope) () -> Unit) = onStartup(Name(name), block)

        context (scope: InitScope)
        fun <T: Any> allocate(
            name: Name,
            value: T,
            valueType: KType,
            stepBy: (T, Duration) -> T,
            mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
        ): Cell<T> = scope.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)

        context (scope: InitScope)
        fun <T> spawn(name: Name, block: suspend context (TaskScope) () -> TaskScopeResult<T>) = scope.spawn(name, block)

        context (scope: InitScope)
        fun <T> spawn(name: String, block: suspend context (TaskScope) () -> TaskScopeResult<T>) = spawn(Name(name), block)

        /**
         * Adds [contextName] to the naming context, adding it as a level in the namespace of all resources and tasks
         * allocated through the returned [InitScope].
         */
        context (scope: InitScope)
        fun subContext(contextName: String) = object : InitScope by scope {
            override val contextName get() = scope.contextName / contextName

            override fun onStartup(name: Name, block: suspend context(TaskScope) () -> Unit) =
                scope.onStartup(Name(contextName) / name, block)

            override fun <T : Any> allocate(
                name: Name,
                value: T,
                valueType: KType,
                stepBy: (T, Duration) -> T,
                mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
            ): Cell<T> = scope.allocate(Name(contextName) / name, value, valueType, stepBy, mergeConcurrentEffects)

            override fun <T> spawn(name: Name, block: suspend context (TaskScope) () -> TaskScopeResult<T>) =
                scope.spawn(Name(contextName) / name, block)
        }

        /**
         * Runs [block] in a sub-scope of [InitScope], adding [contextName] to all resources' and tasks' namespaces.
         */
        @OptIn(ExperimentalContracts::class)
        context (scope: InitScope)
        inline fun <R> subContext(contextName: String, block: context (InitScope) () -> R): R {
            kotlin.contracts.contract {
                callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
            }
            return block(subContext(contextName))
        }
    }
}
