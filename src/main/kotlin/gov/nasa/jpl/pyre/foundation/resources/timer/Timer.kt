package gov.nasa.jpl.pyre.foundation.resources.timer

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.*
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import kotlinx.serialization.Serializable

@Serializable
data class Timer(val time: Duration, val rate: Int) : Dynamics<Duration, Timer> {
    override fun value(): Duration = time
    override fun step(t: Duration): Timer = Timer(time + t * rate, rate)
}

operator fun Timer.plus(other: Timer): Timer = Timer(this.time + other.time, this.rate + other.rate)
operator fun Timer.plus(other: Duration): Timer = Timer(this.time + other, this.rate)
operator fun Duration.plus(other: Timer): Timer = Timer(this + other.time, other.rate)
operator fun Timer.unaryPlus(): Timer = this
operator fun Timer.minus(other: Timer): Timer = Timer(this.time - other.time, this.rate - other.rate)
operator fun Timer.minus(other: Duration): Timer = Timer(this.time - other, this.rate)
operator fun Duration.minus(other: Timer): Timer = Timer(this - other.time, other.rate)
operator fun Timer.unaryMinus(): Timer = Timer(-time, -rate)
