package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationEpoch
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import kotlin.time.Instant

object TaskOperations {
    context (scope: TaskScope)
    suspend fun delayUntil(absoluteTime: Duration) = await(simulationClock greaterThanOrEquals absoluteTime)

    context (scope: TaskScope)
    suspend fun delayUntil(absoluteTime: Instant) = delayUntil((absoluteTime - simulationEpoch).toPyreDuration())

    context (scope: TaskScope)
    suspend fun delay(time: Duration) = delayUntil(simulationClock.getValue() + time)
}