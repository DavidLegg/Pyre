package gov.nasa.jpl.pyre.foundation.resources.timer

import kotlin.time.Duration

object TimerOperations {
    operator fun Timer.plus(other: Timer): Timer = Timer(this.time + other.time, this.rate + other.rate)
    operator fun Timer.plus(other: Duration): Timer = Timer(this.time + other, this.rate)
    operator fun Duration.plus(other: Timer): Timer = Timer(this + other.time, other.rate)
    operator fun Timer.unaryPlus(): Timer = this

    operator fun Timer.minus(other: Timer): Timer = Timer(this.time - other.time, this.rate - other.rate)
    operator fun Timer.minus(other: Duration): Timer = Timer(this.time - other, this.rate)
    operator fun Duration.minus(other: Timer): Timer = Timer(this - other.time, other.rate)
    operator fun Timer.unaryMinus(): Timer = Timer(-time, -rate)

    operator fun Timer.times(other: Double): Timer = Timer(time * other, rate * other)
    operator fun Double.times(other: Timer): Timer = other * this

    operator fun Timer.div(other: Double): Timer = Timer(time / other, rate / other)
}