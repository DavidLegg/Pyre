package gov.nasa.jpl.pyre.foundation.resources.timer

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.EPSILON
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.abs
import gov.nasa.jpl.pyre.kernel.div
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.foundation.resources.Expiring
import gov.nasa.jpl.pyre.foundation.resources.Expiry
import gov.nasa.jpl.pyre.foundation.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.bind
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.ThinResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.emit
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.math.abs

object TimerResourceOperations {
    context (scope: InitScope)
    fun timer(name: String, initialTime: Duration = ZERO, initialRate: Int = 1) =
        resource(name, Timer(initialTime, initialRate))

    /**
     * Reset this timer to time, not running.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.reset(time: Duration = ZERO) =
        this.emit({ t: Timer -> Timer(time, 0) } named { "Reset $this" })

    /**
     * Reset this timer to time, running forward.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.restart(time: Duration = ZERO) =
        this.emit({ t: Timer -> Timer(time, 1) } named { "Restart $this" })

    /**
     * Reset this timer to time, running backward.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.restartCountdown(time: Duration) =
        this.emit({ t: Timer -> Timer(time, -1) } named { "Restart countdown on $this" })

    /**
     * Pause this timer, but preserve the recorded time.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.pause() =
        this.emit({ t: Timer -> Timer(t.time, 0) } named { "Pause $this" })

    /**
     * Resume this timer, running forward from the current time.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.resume() =
        this.emit({ t: Timer -> Timer(t.time, 1) } named { "Resume $this" })

    /**
     * Resume this timer, running backward from the current time.
     */
    context (scope: TaskScope)
    suspend fun MutableResource<Timer>.resumeCountdown() =
        this.emit({ t: Timer -> Timer(t.time, -1) } named { "Resume countdown on $this" })

    operator fun Resource<Timer>.plus(other: Resource<Timer>): Resource<Timer> =
        map(this, other, Timer::plus) named { "($this) + ($other)" }
    operator fun Resource<Timer>.plus(other: Duration): Resource<Timer> = this + constant(other)
    operator fun Duration.plus(other: Resource<Timer>): Resource<Timer> = constant(this) + other
    operator fun Resource<Timer>.minus(other: Resource<Timer>): Resource<Timer> =
        map(this, other, Timer::minus) named { "($this) - ($other)" }
    operator fun Resource<Timer>.minus(other: Duration): Resource<Timer> = this - constant(other)
    operator fun Duration.minus(other: Resource<Timer>): Resource<Timer> = constant(this) - other

    // Writing actual operator overloads for DiscreteResource<Duration> causes platform declaration clash,
    // because the outer type is just Resource. Instead, offer the asTimer() conversion.
    fun DiscreteResource<Duration>.asTimer(): Resource<Timer> = map(this) { t -> Timer(t.value, 0) } named this::toString
    fun constant(time: Duration): Resource<Timer> = ResourceMonad.pure(Timer(time, 0)) named time::toString

    fun Resource<Timer>.compareTo(other: Resource<Timer>): IntResource {
        return bind(this - other) { delta ->
            val expiry =
                // If the delta isn't changing, comparison never changes
                if (delta.rate == 0) NEVER
                // If delta is exactly zero and changing, it changes "immediately"
                else if (delta.time == ZERO) Expiry(EPSILON)
                // If delta is moving away from zero, it never changes sign
                else if (delta.time > ZERO == delta.rate > 0) NEVER
                // Otherwise delta is moving towards zero. Compute the intercept
                else {
                    // Compute the intercept as ceil(|time| / rate).
                    // Note that integer division on positive numbers is equivalent to floor(... / ...)
                    // If rate divides time, the -EPSILON means the division returns (|time| / rate) - EPSILON,
                    // which we correct with a +EPSILON.
                    // Otherwise, the -EPSILON doesn't change the quotient floor(|time| / rate),
                    // so the +EPSILON corrects it up to ceil(|time| / rate).
                    Expiry(((abs(delta.time) - EPSILON) / abs(delta.rate).toLong()) + EPSILON)
                }
            ThinResourceMonad.pure(Expiring(Discrete(delta.time.ticks.compareTo(0)), expiry))
        } named { "($this).compareTo($other)" }
    }
    fun Resource<Timer>.compareTo(other: Duration): IntResource = compareTo(constant(other))

    infix fun Resource<Timer>.lessThan(other: Resource<Timer>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it < 0 } named { "($this) < ($other)"}
    infix fun Resource<Timer>.lessThanOrEquals(other: Resource<Timer>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it <= 0 } named { "($this) <= ($other)"}
    infix fun Resource<Timer>.greaterThan(other: Resource<Timer>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it > 0 } named { "($this) > ($other)"}
    infix fun Resource<Timer>.greaterThanOrEquals(other: Resource<Timer>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it >= 0 } named { "($this) >= ($other)"}

    infix fun Resource<Timer>.lessThan(other: Duration) = this lessThan constant(other)
    infix fun Resource<Timer>.lessThanOrEquals(other: Duration) = this lessThanOrEquals constant(other)
    infix fun Resource<Timer>.greaterThan(other: Duration) = this greaterThan constant(other)
    infix fun Resource<Timer>.greaterThanOrEquals(other: Duration) = this greaterThanOrEquals constant(other)

    infix fun Duration.lessThan(other: Resource<Timer>) = constant(this) lessThan other
    infix fun Duration.lessThanOrEquals(other: Resource<Timer>) = constant(this) lessThanOrEquals other
    infix fun Duration.greaterThan(other: Resource<Timer>) = constant(this) greaterThan other
    infix fun Duration.greaterThanOrEquals(other: Resource<Timer>) = constant(this) greaterThanOrEquals other
}