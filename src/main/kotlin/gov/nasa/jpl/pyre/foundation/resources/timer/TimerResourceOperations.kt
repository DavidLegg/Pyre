package gov.nasa.jpl.pyre.foundation.resources.timer

import gov.nasa.jpl.pyre.foundation.resources.*
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.bind
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.*
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerOperations.plus
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Durations.EPSILON
import gov.nasa.jpl.pyre.kernel.Durations.epsilon
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.utilities.named
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.times

typealias TimerResource = Resource<Timer>
typealias MutableTimerResource = MutableResource<Timer>

object TimerResourceOperations {
    context (scope: InitScope)
    fun timer(name: String, initialTime: Duration = ZERO, initialRate: Double = 1.0) =
        resource(name, Timer(initialTime, initialRate))

    /**
     * Reset this timer to [time], not running.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.reset(time: Duration = ZERO) =
        this.emit({ t: Timer -> Timer(time, 0.0) }.named { "Reset $this" })

    /**
     * Reset this timer to [time], running forward.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.restart(time: Duration = ZERO) =
        this.emit({ t: Timer -> Timer(time, 1.0) }.named { "Restart $this" })

    /**
     * Reset this timer to [time], running backward.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.restartCountdown(time: Duration) =
        this.emit({ t: Timer -> Timer(time, -1.0) }.named { "Restart countdown on $this" })

    /**
     * Pause this timer, but preserve the recorded time.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.pause() =
        this.emit({ t: Timer -> Timer(t.time, 0.0) }.named { "Pause $this" })

    /**
     * Resume this timer, running forward from the current time.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.resume() =
        this.emit({ t: Timer -> Timer(t.time, 1.0) }.named { "Resume $this" })

    /**
     * Resume this timer, running backward from the current time.
     */
    context (scope: TaskScope)
    fun MutableTimerResource.resumeCountdown() =
        this.emit({ t: Timer -> Timer(t.time, -1.0) }.named { "Resume countdown on $this" })

    operator fun TimerResource.plus(other: TimerResource): TimerResource =
        map(this, other) { t, o -> t + o }.fullyNamed { Name("($this) + ($other)") }
    operator fun TimerResource.plus(other: Duration): TimerResource = this + constant(other)
    operator fun Duration.plus(other: TimerResource): TimerResource = constant(this) + other
    operator fun TimerResource.minus(other: TimerResource): TimerResource =
        map(this, other) { t, o -> t - o }.fullyNamed { Name("($this) - ($other)") }
    operator fun TimerResource.minus(other: Duration): TimerResource = this - constant(other)
    operator fun Duration.minus(other: TimerResource): TimerResource = constant(this) - other

    // Writing actual operator overloads for DiscreteResource<Duration> causes platform declaration clash,
    // because the outer type is just Resource. Instead, offer the asTimer() conversion.
    fun DiscreteResource<Duration>.asTimer(): TimerResource =
        map(this) { t -> Timer(t.value, 0.0) }.fullyNamed { name }
    fun constant(time: Duration): TimerResource =
        ResourceMonad.pure(Timer(time, 0.0)).fullyNamed { Name(time.toString()) }

    fun TimerResource.compareTo(other: TimerResource): IntResource {
        return bind(this, other) { t, o ->
            val delta = t - o
            val expiry =
                // If the delta isn't changing, comparison never changes
                if (delta.rate == 0.0) Duration.INFINITE
                // If delta is exactly zero and changing, it changes "immediately"
                // TODO: Do we need to handle an edge case here if rate in [0, 1)? It may take >epsilon for the value to change by epsilon...
                else if (delta.time == ZERO) t.time.epsilon
                // If delta is moving away from zero, it never changes sign
                else if (delta.time > ZERO == delta.rate > 0) Duration.INFINITE
                // Otherwise delta is moving towards zero. Compute the intercept
                else {
                    // Compute the intercept as ceil(|time| / rate).
                    // Note that integer division on positive numbers is equivalent to floor(... / ...)
                    // If rate divides time, the -EPSILON means the division returns (|time| / rate) - EPSILON,
                    // which we correct with a +EPSILON.
                    // Otherwise, the -EPSILON doesn't change the quotient floor(|time| / rate),
                    // so the +EPSILON corrects it up to ceil(|time| / rate).
                    // Expiry(((delta.time.absoluteValue - EPSILON) / abs(delta.rate).toLong()) + EPSILON)

                    // Since the estimated root may be off by at most +/- epsilon (we think),
                    // brute-force the problem of finding the exact crossing time by bracketing the estimated root
                    // with a +/- 2-epsilon search window, taking the earliest time that actually crosses.
                    // TODO: Think through ways to analytically reach this solution with fewer operations
                    val estimatedRoot = (delta.time / delta.rate).absoluteValue
                    var result: Duration? = null
                    for (offset in -2..2) {
                        // TODO: Think through whether we need to handle coarse epsilon here, and if so, how
                        val possibleRoot = estimatedRoot + offset * EPSILON
                        val projectedDeltaAtRoot = delta.time + delta.rate * possibleRoot
                        if ((projectedDeltaAtRoot == ZERO) || projectedDeltaAtRoot > ZERO != delta.time > ZERO) {
                            result = possibleRoot
                            break
                        }
                    }
                    checkNotNull(result) { "Root finding failed on resource $this"}
                }
            ThinResourceMonad.pure(Expiring(Discrete(delta.time.compareTo(ZERO)), expiry))
        }.fullyNamed { Name("($this).compareTo($other)") }
    }

    infix fun TimerResource.lessThan(other: TimerResource): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it < 0 }.fullyNamed { Name("($this) < ($other)") }
    infix fun TimerResource.lessThanOrEquals(other: TimerResource): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it <= 0 }.fullyNamed { Name("($this) <= ($other)") }
    infix fun TimerResource.greaterThan(other: TimerResource): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it > 0 }.fullyNamed { Name("($this) > ($other)") }
    infix fun TimerResource.greaterThanOrEquals(other: TimerResource): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it >= 0 }.fullyNamed { Name("($this) >= ($other)") }

    infix fun TimerResource.lessThan(other: Duration) = this lessThan constant(other)
    infix fun TimerResource.lessThanOrEquals(other: Duration) = this lessThanOrEquals constant(other)
    infix fun TimerResource.greaterThan(other: Duration) = this greaterThan constant(other)
    infix fun TimerResource.greaterThanOrEquals(other: Duration) = this greaterThanOrEquals constant(other)

    infix fun Duration.lessThan(other: TimerResource) = constant(this) lessThan other
    infix fun Duration.lessThanOrEquals(other: TimerResource) = constant(this) lessThanOrEquals other
    infix fun Duration.greaterThan(other: TimerResource) = constant(this) greaterThan other
    infix fun Duration.greaterThanOrEquals(other: TimerResource) = constant(this) greaterThanOrEquals other
}