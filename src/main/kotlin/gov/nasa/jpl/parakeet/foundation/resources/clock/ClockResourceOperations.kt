package gov.nasa.jpl.parakeet.foundation.resources.clock

import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.clock.ClockOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.clock.ClockOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.emit
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResource
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.constant
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.lessThan
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.utilities.named
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

typealias ClockResource = Resource<Clock>
typealias MutableClockResource = MutableResource<Clock>

object ClockResourceOperations {
    context (scope: InitScope)
    fun clock(name: String, initialTime: Instant = now(), initialRate: Double = 1.0) =
        resource(name, Clock(initialTime, initialRate))

    /**
     * Stop the clock at its current time.
     */
    context (scope: TaskScope)
    fun MutableClockResource.pause() =
        this.emit({ t: Clock -> t.copy(rate = 0.0) }.named { "Pause $this" })

    /**
     * Resume the clock from its current time.
     */
    context (scope: TaskScope)
    fun MutableClockResource.resume() =
        this.emit({ t: Clock -> t.copy(rate = 1.0) }.named { "Resume $this" })

    /**
     * Set the clock to [time], but don't change the rate it's running at.
     */
    context (scope: TaskScope)
    fun MutableClockResource.set(time: Instant) =
        this.emit({ t: Clock -> t.copy(time = time) }.named { "Set $this to $time" })

    fun DiscreteResource<Instant>.asClock(): ClockResource =
        map(this) { t -> Clock(t.value, 0.0) }.fullyNamed { name }
    fun constant(time: Instant): ClockResource = pure(Clock(time, 0.0))

    operator fun ClockResource.unaryPlus(): ClockResource = this
    operator fun ClockResource.minus(other: ClockResource): TimerResource =
        map(this, other) { t, o -> t - o }.fullyNamed { Name("($this) - ($other)") }
    operator fun ClockResource.minus(other: Instant): TimerResource = this - constant(other)
    operator fun Instant.minus(other: ClockResource): TimerResource = constant(this) - other

    // To avoid conflicting JVM declarations due to type erasure, these methods need to be split into separate objects.
    object VsTimer {
        operator fun ClockResource.plus(other: TimerResource): ClockResource =
            map(this, other) { t, o -> t + o }.fullyNamed { Name("($this) + ($other)") }
        operator fun ClockResource.plus(other: Duration): ClockResource = this + constant(other)
        operator fun Instant.plus(other: TimerResource): ClockResource = constant(this) + other

        operator fun ClockResource.minus(other: TimerResource): ClockResource =
            map(this, other) { t, o -> t - o }.fullyNamed { Name("($this) - ($other)") }
        operator fun ClockResource.minus(other: Duration): ClockResource = this - constant(other)
        operator fun Instant.minus(other: TimerResource): ClockResource = constant(this) - other
    }

    object TimerVs {
        operator fun TimerResource.plus(other: ClockResource): ClockResource =
            map(this, other) { t, o -> t + o }.fullyNamed { Name("($this) + ($other)") }
        operator fun Duration.plus(other: ClockResource): ClockResource = constant(this) + other
        operator fun TimerResource.plus(other: Instant): ClockResource = this + constant(other)
    }

    infix fun ClockResource.lessThan(other: ClockResource): BooleanResource =
        ((this - other) lessThan constant(ZERO)).fullyNamed { Name("($this) < ($other)") }
    infix fun ClockResource.lessThanOrEquals(other: ClockResource): BooleanResource =
        (this - other) lessThanOrEquals constant(ZERO).fullyNamed { Name("($this) <= ($other)") }
    infix fun ClockResource.greaterThan(other: ClockResource): BooleanResource =
        (this - other) greaterThan constant(ZERO).fullyNamed { Name("($this) > ($other)") }
    infix fun ClockResource.greaterThanOrEquals(other: ClockResource): BooleanResource =
        (this - other) greaterThanOrEquals constant(ZERO).fullyNamed { Name("($this) >= ($other)") }

    infix fun ClockResource.lessThan(other: Instant): BooleanResource = this lessThan constant(other)
    infix fun ClockResource.lessThanOrEquals(other: Instant): BooleanResource = this lessThanOrEquals constant(other)
    infix fun ClockResource.greaterThan(other: Instant): BooleanResource = this greaterThan constant(other)
    infix fun ClockResource.greaterThanOrEquals(other: Instant): BooleanResource = this greaterThanOrEquals constant(other)

    infix fun Instant.lessThan(other: ClockResource): BooleanResource = constant(this) lessThan other
    infix fun Instant.lessThanOrEquals(other: ClockResource): BooleanResource = constant(this) lessThanOrEquals other
    infix fun Instant.greaterThan(other: ClockResource): BooleanResource = constant(this) greaterThan other
    infix fun Instant.greaterThanOrEquals(other: ClockResource): BooleanResource = constant(this) greaterThanOrEquals other
}