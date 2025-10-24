package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.CellSet
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.Timer
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationClock
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationEpoch
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * A context for all the "global" conveniences offered by Spark during simulation.
 */
interface SparkScope {
    /**
     * Primary simulation clock. Simulation time should be derived from this clock.
     */
    val simulationClock: Resource<Timer>

    /**
     * Absolute epoch time. [simulationClock] is relative to this time.
     */
    val simulationEpoch: Instant

    companion object {
        context (scope: SparkScope)
        val simulationClock get() = scope.simulationClock

        context (scope: SparkScope)
        val simulationEpoch get() = scope.simulationEpoch
    }
}

interface ResourceScope : SparkScope {
    suspend fun <V> read(cell: CellSet.CellHandle<V>): V

    companion object {
        context (scope: ResourceScope)
        suspend fun <V> read(cell: CellSet.CellHandle<V>): V = scope.read(cell)

        context (scope: ResourceScope)
        suspend fun now() = simulationEpoch + simulationClock.getValue().toKotlinDuration()
    }
}

interface ConditionScope : ResourceScope

interface TaskScope : ResourceScope {
    suspend fun <V> emit(cell: CellSet.CellHandle<V>, effect: Effect<V>)
    suspend fun <T> report(value: T, type: KType)
    suspend fun delay(time: Duration)
    suspend fun await(condition: () -> Condition)
    suspend fun <S> spawn(childName: String, child: PureTaskStep<S>)

    companion object {
        context (scope: TaskScope)
        suspend inline fun <reified T> report(value: T) = report(value, typeOf<T>())

        context (scope: TaskScope)
        suspend fun <V> emit(cell: CellSet.CellHandle<V>, effect: Effect<V>) = scope.emit(cell, effect)

        context (scope: TaskScope)
        suspend fun <T> report(value: T, type: KType) = scope.report(value, type)

        context (scope: TaskScope)
        suspend fun delay(time: Duration) = scope.delay(time)

        context (scope: TaskScope)
        suspend fun await(condition: () -> Condition) = scope.await(condition)

        context (scope: TaskScope)
        suspend fun <S> spawn(childName: String, child: PureTaskStep<S>) = scope.spawn(childName, child)

        /**
         * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
         */
        context (scope: TaskScope)
        suspend fun delayUntil(time: Duration) = delay(maxOf(time - simulationClock.getValue(), ZERO))

        /**
         * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
         */
        context (scope: TaskScope)
        suspend fun delayUntil(time: Instant) = delayUntil((time - simulationEpoch).toPyreDuration())
    }
}

interface InitScope : SparkScope, BasicInitScope, ResourceScope {
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
    fun onStartup(name: String, block: suspend context (TaskScope) () -> Unit)

    override suspend fun <V> read(cell: CellSet.CellHandle<V>): V =
        (this as BasicInitScope).read(cell)

    companion object {
        context (scope: InitScope)
        fun onStartup(name: String, block: suspend context (TaskScope) () -> Unit) = scope.onStartup(name, block)

        /**
         * Creates a subcontext named "$this/$contextName".
         * If models incorporate the context name into the names of tasks and resources,
         * this provides an easy way to build hierarchical models without a lot of manual bookkeeping.
         */
        context (scope: InitScope)
        fun subContext(contextName: String) = object : InitScope by scope {
            override fun <T : Any> allocate(cell: Cell<T>): CellSet.CellHandle<T> =
                scope.allocate(cell.copy(name = "$contextName/${cell.name}"))

            override fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>) =
                scope.spawn("$contextName/$name", step)

            override fun onStartup(name: String, block: suspend TaskScope.() -> Unit) =
                scope.onStartup("$contextName/$name", block)

            override fun toString() = "$scope/$contextName"
        }
    }
}
