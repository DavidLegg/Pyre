package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationEpoch
import gov.nasa.jpl.pyre.kernel.Cell
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

    /**
     * Standard channel for reporting [ActivityEvent]s for all activities.
     */
    val activities: Channel<ActivityEvent>

    /**
     * Standard channel for general-purpose "logging" style output
     */
    val stdout: Channel<String>

    /**
     * Standard channel for general-purpose "logging" style output that signals some kind of warning or error.
     */
    val stderr: Channel<String>

    companion object {
        context (scope: SimulationScope)
        val simulationClock get() = scope.simulationClock

        context (scope: SimulationScope)
        val simulationEpoch get() = scope.simulationEpoch

        context (scope: SimulationScope)
        val activities get() = scope.activities

        context (scope: SimulationScope)
        val stdout get() = scope.stdout

        context (scope: SimulationScope)
        val stderr get() = scope.stderr

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
        fun now() = simulationEpoch + simulationClock.getValue().toKotlinDuration()
    }
}

interface ReportScope : SimulationScope {
    fun <T> report(channel: Channel<T>, value: T)

    companion object {
        context (scope: ReportScope)
        fun <T> Channel<T>.report(value: T) = scope.report(this, value)
    }
}

interface ConditionScope : ResourceScope

interface TaskScope : ResourceScope, ReportScope {
    fun <V> emit(cell: Cell<V>, effect: Effect<V>)
    suspend fun await(condition: Condition)
    suspend fun <S> spawn(childName: Name, child: suspend context (TaskScope) () -> TaskScopeResult<S>)

    companion object {
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
interface InitScope : SimulationScope, ResourceScope, ReportScope {
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

    fun <T> channel(name: Name, metadata: Map<String, String>, valueType: KType): Channel<T>

    companion object {
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

        context (scope: InitScope)
        inline fun <reified T> channel(name: Name, vararg metadata: Pair<String, String>) =
            scope.channel<T>(name, metadata.toMap(), typeOf<T>())
    }
}
