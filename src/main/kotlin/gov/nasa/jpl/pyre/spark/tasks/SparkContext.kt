package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.getValue
import kotlin.time.Instant

suspend fun ResourceScope.now() = simulationEpoch + simulationClock.getValue().toKotlinDuration()

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun TaskScope.delayUntil(time: Duration) = delay(maxOf(time - simulationClock.getValue(), ZERO))

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun TaskScope.delayUntil(time: Instant) = delayUntil((time - simulationEpoch).toPyreDuration())

object SparkContextExtensions {
    context (sparkContext: SparkContext)
    val simulationClock get() = sparkContext.simulationClock

    context (sparkContext: SparkContext)
    val simulationEpoch get() = sparkContext.simulationEpoch

    context (scope: ResourceScope)
    suspend fun now() = scope.now()

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: TaskScope)
    suspend fun delayUntil(time: Duration) = scope.delayUntil(time)

    /**
     * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
     */
    context (scope: TaskScope)
    suspend fun delayUntil(time: Instant) = scope.delayUntil(time)
}
