package gov.nasa.jpl.pyre.flame.results.profiles.discrete

import gov.nasa.jpl.pyre.flame.results.profiles.discrete.DiscreteProfile.DiscreteProfileMonad.map
import gov.nasa.jpl.pyre.flame.results.profiles.discrete.DiscreteProfileOperations.constant
import kotlin.collections.intersect
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.union
import kotlin.time.Instant

typealias SetProfile<T> = DiscreteProfile<Set<T>>

object SetProfileOperations {
    fun <T> empty(): SetProfile<T> = constant(emptySet())

    fun <T> singleton(value: T) = constant(setOf(value))

    fun <T> singleton(value: T, start: Instant, end: Instant) = DiscreteProfile(
        emptySet(),
        start to setOf(value),
        end to emptySet())

    infix fun <T> SetProfile<T>.union(other: SetProfile<T>): SetProfile<T> =
        map(this, other) { s, t -> s union t }

    infix fun <T> SetProfile<T>.intersect(other: SetProfile<T>): SetProfile<T> =
        map(this, other) { s, t -> s intersect t }

    operator fun <T> SetProfile<T>.plus(other: SetProfile<T>): SetProfile<T> =
        map(this, other) { s, t -> s + t }

    operator fun <T> SetProfile<T>.minus(other: SetProfile<T>): SetProfile<T> =
        map(this, other) { s, t -> s - t }
}