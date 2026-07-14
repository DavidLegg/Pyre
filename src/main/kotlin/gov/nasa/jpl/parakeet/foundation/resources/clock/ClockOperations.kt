package gov.nasa.jpl.parakeet.foundation.resources.clock

import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import kotlin.time.Duration
import kotlin.time.Instant

object ClockOperations {
    operator fun Clock.plus(other: Timer): Clock = Clock(time + other.time, rate + other.rate)
    operator fun Timer.plus(other: Clock): Clock = other + this
    operator fun Clock.plus(other: Duration): Clock = Clock(time + other, rate)
    operator fun Duration.plus(other: Clock): Clock = other + this
    operator fun Clock.unaryPlus(): Clock = this
    operator fun Clock.minus(other: Clock): Timer = Timer(time - other.time, rate - other.rate)
    operator fun Clock.minus(other: Timer): Clock = Clock(time - other.time, rate - other.rate)
    operator fun Clock.minus(other: Duration): Clock = Clock(time - other, rate)
    operator fun Clock.minus(other: Instant): Timer = Timer(time - other, rate)
    operator fun Instant.minus(other: Clock): Timer = Timer(this - other.time, -other.rate)
}