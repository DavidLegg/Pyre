package gov.nasa.jpl.parakeet.foundation.resources.clock

import gov.nasa.jpl.parakeet.foundation.resources.Dynamics
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * An [Instant] which changes linearly with respect to simulation time.
 */
@Serializable
data class Clock(
    @Contextual
    val time: Instant,
    val rate: Double,
) : Dynamics<Instant, Clock> {
    override fun value(): Instant = time
    override fun step(t: Duration): Clock = copy(time = time + t * rate)
    override fun toString(): String = if (rate == 0.0) {
        time.toString()
    } else {
        "Clock($time, running at ${rate}x)"
    }
}
