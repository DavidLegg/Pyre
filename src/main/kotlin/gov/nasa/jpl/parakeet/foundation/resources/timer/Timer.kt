package gov.nasa.jpl.parakeet.foundation.resources.timer

import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * A [Duration] which changes linearly with respect to simulation time.
 */
@Serializable
data class Timer(
    val time: Duration,
    val rate: Double
) : Dynamics<Duration, Timer> {
    override fun value(): Duration = time
    override fun step(t: Duration): Timer = Timer(time + t * rate, rate)
}
