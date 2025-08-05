package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Cell
import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.ember.Task.PureStepResult
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.Timer
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

interface SparkInitContext : SparkContext, SimulationInitContext {
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
    fun onStartup(name: String, block: suspend SparkTaskScope.() -> Unit)
}
interface SparkResourceScope : SparkContext, CellsReadableScope
interface SparkTaskScope : SparkResourceScope, TaskScope

context (sparkContext: SparkContext, scope: CellsReadableScope)
fun sparkResourceScope(): SparkResourceScope =
    object : SparkResourceScope, SparkContext by sparkContext, CellsReadableScope by scope {}

context (sparkContext: SparkContext, scope: TaskScope)
fun sparkTaskScope(): SparkTaskScope =
    object : SparkTaskScope, SparkContext by sparkContext, TaskScope by scope {}

suspend fun SparkResourceScope.now() = simulationEpoch + simulationClock.getValue().toKotlinDuration()

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun SparkTaskScope.delayUntil(time: Duration) = delay(time - simulationClock.getValue())

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun SparkTaskScope.delayUntil(time: Instant) = delayUntil((time - simulationEpoch).toPyreDuration())

object SparkContextExtensions {
    context (sparkContext: SparkContext)
    val simulationClock get() = sparkContext.simulationClock

    context (sparkContext: SparkContext)
    val simulationEpoch get() = sparkContext.simulationEpoch

    context (scope: SparkResourceScope)
    suspend fun now() = scope.now()

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: SparkTaskScope)
    suspend fun delayUntil(time: Duration) = scope.delayUntil(time)

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: SparkTaskScope)
    suspend fun delayUntil(time: Instant) = scope.delayUntil(time)
}
