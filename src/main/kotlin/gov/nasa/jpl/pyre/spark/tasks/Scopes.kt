package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.InitScope
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.timer.Timer
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * A context for all the "global" conveniences offered by Spark during simulation.
 */
interface SparkContext {
    /**
     * Primary simulation clock. Simulation time should be derived from this clock.
     */
    val simulationClock: Resource<Timer>

    /**
     * Absolute epoch time. [simulationClock] is relative to this time.
     */
    val simulationEpoch: Instant
}


interface ResourceScope : SparkContext {
    suspend fun <V, E> read(cell: CellSet.CellHandle<V, E>): V
}

interface ConditionScope : ResourceScope

interface TaskScope : ResourceScope {
    suspend fun <V, E> emit(cell: CellSet.CellHandle<V, E>, effect: E)
    suspend fun <T> report(value: T, type: KType)
    suspend fun delay(time: Duration)
    suspend fun await(condition: () -> Condition)
    suspend fun <S> spawn(childName: String, child: PureTaskStep<S>)

    companion object {
        suspend inline fun <reified T> TaskScope.report(value: T) = report(value, typeOf<T>())
    }
}

interface SparkInitScope : SparkContext, InitScope {
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
    fun onStartup(name: String, block: suspend TaskScope.() -> Unit)
}
