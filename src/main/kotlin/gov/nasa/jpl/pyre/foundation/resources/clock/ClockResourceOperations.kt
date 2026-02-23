package gov.nasa.jpl.pyre.foundation.resources.clock

import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.clock.ClockOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.clock.ClockOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.emit
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.compareTo
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.constant
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.utilities.named
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

object ClockResourceOperations {
    context (scope: InitScope)
    fun clock(name: String, initialTime: Instant = now(), initialRate: Double = 1.0) =
        resource(name, Clock(initialTime, initialRate))

    /**
     * Stop the clock at its current time.
     */
    context (scope: TaskScope)
    fun MutableResource<Clock>.pause() =
        this.emit({ t: Clock -> t.copy(rate = 0.0) }.named { "Pause $this" })

    /**
     * Resume the clock from its current time.
     */
    context (scope: TaskScope)
    fun MutableResource<Clock>.resume() =
        this.emit({ t: Clock -> t.copy(rate = 1.0) }.named { "Resume $this" })

    /**
     * Set the clock to [time], but don't change the rate it's running at.
     */
    context (scope: TaskScope)
    fun MutableResource<Clock>.set(time: Instant) =
        this.emit({ t: Clock -> t.copy(time = time) }.named { "Set $this to $time" })

    fun DiscreteResource<Instant>.asClock(): Resource<Clock> =
        map(this) { t -> Clock(t.value, 0.0) }.fullyNamed { name }
    fun constant(time: Instant): Resource<Clock> = pure(Clock(time, 0.0))

    operator fun Resource<Clock>.unaryPlus(): Resource<Clock> = this
    operator fun Resource<Clock>.minus(other: Resource<Clock>): Resource<Timer> =
        map(this, other) { t, o -> t - o }.fullyNamed { Name("($this) - ($other)") }
    operator fun Resource<Clock>.minus(other: Instant): Resource<Timer> = this - constant(other)
    operator fun Instant.minus(other: Resource<Clock>): Resource<Timer> = constant(this) - other

    // To avoid conflicting JVM declarations due to type erasure, these methods need to be split into separate objects.
    object VsTimer {
        operator fun Resource<Clock>.plus(other: Resource<Timer>): Resource<Clock> =
            map(this, other) { t, o -> t + o }.fullyNamed { Name("($this) + ($other)") }
        operator fun Resource<Clock>.plus(other: Duration): Resource<Clock> = this + constant(other)
        operator fun Instant.plus(other: Resource<Timer>): Resource<Clock> = constant(this) + other

        operator fun Resource<Clock>.minus(other: Resource<Timer>): Resource<Clock> =
            map(this, other) { t, o -> t - o }.fullyNamed { Name("($this) - ($other)") }
        operator fun Resource<Clock>.minus(other: Duration): Resource<Clock> = this - constant(other)
        operator fun Instant.minus(other: Resource<Timer>): Resource<Clock> = constant(this) - other
    }

    object TimerVs {
        operator fun Resource<Timer>.plus(other: Resource<Clock>): Resource<Clock> =
            map(this, other) { t, o -> t + o }.fullyNamed { Name("($this) + ($other)") }
        operator fun Duration.plus(other: Resource<Clock>): Resource<Clock> = constant(this) + other
        operator fun Resource<Timer>.plus(other: Instant): Resource<Clock> = this + constant(other)
    }

    fun Resource<Clock>.compareTo(other: Resource<Clock>): IntResource =
        (this - other).compareTo(constant(ZERO))

    infix fun Resource<Clock>.lessThan(other: Resource<Clock>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it < 0 }.fullyNamed { Name("($this) < ($other)") }
    infix fun Resource<Clock>.lessThanOrEquals(other: Resource<Clock>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it <= 0 }.fullyNamed { Name("($this) <= ($other)") }
    infix fun Resource<Clock>.greaterThan(other: Resource<Clock>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it > 0 }.fullyNamed { Name("($this) > ($other)") }
    infix fun Resource<Clock>.greaterThanOrEquals(other: Resource<Clock>): BooleanResource =
        DiscreteResourceMonad.map(this.compareTo(other)) { it >= 0 }.fullyNamed { Name("($this) >= ($other)") }

    infix fun Resource<Clock>.lessThan(other: Instant): BooleanResource = this lessThan constant(other)
    infix fun Resource<Clock>.lessThanOrEquals(other: Instant): BooleanResource = this lessThanOrEquals constant(other)
    infix fun Resource<Clock>.greaterThan(other: Instant): BooleanResource = this greaterThan constant(other)
    infix fun Resource<Clock>.greaterThanOrEquals(other: Instant): BooleanResource = this greaterThanOrEquals constant(other)

    infix fun Instant.lessThan(other: Resource<Clock>): BooleanResource = constant(this) lessThan other
    infix fun Instant.lessThanOrEquals(other: Resource<Clock>): BooleanResource = constant(this) lessThanOrEquals other
    infix fun Instant.greaterThan(other: Resource<Clock>): BooleanResource = constant(this) greaterThan other
    infix fun Instant.greaterThanOrEquals(other: Resource<Clock>): BooleanResource = constant(this) greaterThanOrEquals other
}