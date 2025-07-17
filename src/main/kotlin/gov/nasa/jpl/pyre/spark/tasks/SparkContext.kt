package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.ember.minus
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

interface SparkInitContext : SparkContext, SimulationInitContext
interface SparkTaskScope<T> : SparkContext, TaskScope<T>

context (sparkContext: SparkContext, scope: TaskScope<T>)
fun <T> sparkTaskScope(): SparkTaskScope<T> =
    object : SparkTaskScope<T>, SparkContext by sparkContext, TaskScope<T> by scope {}

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun SparkTaskScope<*>.delayUntil(time: Duration) = delay(time - simulationClock.getValue())

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun SparkTaskScope<*>.delayUntil(time: Instant) = delayUntil((time - simulationEpoch).toPyreDuration())

object SparkContextExtensions {
    context (sparkContext: SparkContext)
    val simulationClock get() = sparkContext.simulationClock

    context (sparkContext: SparkContext)
    val simulationEpoch get() = sparkContext.simulationClock

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: SparkTaskScope<Unit>)
    suspend fun delayUntil(time: Duration) = scope.delayUntil(time)

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: SparkTaskScope<Unit>)
    suspend fun delayUntil(time: Instant) = scope.delayUntil(time)
}
