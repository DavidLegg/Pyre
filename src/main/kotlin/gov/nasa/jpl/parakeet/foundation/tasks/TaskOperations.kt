package gov.nasa.jpl.parakeet.foundation.tasks

import gov.nasa.jpl.parakeet.foundation.resources.clock.ClockResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.parakeet.foundation.tasks.Reactions.await
import gov.nasa.jpl.parakeet.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.parakeet.foundation.tasks.SimulationScope.Companion.simulationClock
import kotlin.time.Duration
import kotlin.time.Instant

object TaskOperations {
    context (scope: TaskScope)
    suspend fun delayUntil(absoluteTime: Instant) = await(simulationClock greaterThanOrEquals absoluteTime)

    context (scope: TaskScope)
    suspend fun delay(time: Duration) = delayUntil(now() + time)
}