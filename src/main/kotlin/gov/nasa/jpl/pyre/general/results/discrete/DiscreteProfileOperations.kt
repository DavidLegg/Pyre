package gov.nasa.jpl.pyre.general.results.discrete

import gov.nasa.jpl.pyre.general.results.Profile
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.general.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThanOrEquals

typealias DiscreteProfile<V> = Profile<Discrete<V>>

object DiscreteProfileOperations {
    // Comparisons - convenience methods to pass through to the resource method
    infix fun <T : Comparable<T>> DiscreteProfile<T>.greaterThan(other: DiscreteProfile<T>): BooleanProfile =
        compute { this greaterThan other.asResource() }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.greaterThanOrEquals(other: DiscreteProfile<T>): BooleanProfile =
        compute { this greaterThanOrEquals other.asResource() }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.lessThan(other: DiscreteProfile<T>): BooleanProfile =
        compute { this lessThan other.asResource() }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.lessThanOrEquals(other: DiscreteProfile<T>): BooleanProfile =
        compute { this lessThanOrEquals other.asResource() }

    infix fun <T : Comparable<T>> DiscreteProfile<T>.greaterThan(other: T): BooleanProfile =
        compute { this greaterThan other }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.greaterThanOrEquals(other: T): BooleanProfile =
        compute { this greaterThanOrEquals other }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.lessThan(other: T): BooleanProfile =
        compute { this lessThan other }
    infix fun <T : Comparable<T>> DiscreteProfile<T>.lessThanOrEquals(other: T): BooleanProfile =
        compute { this lessThanOrEquals other }

    infix fun <T : Comparable<T>> T.greaterThan(other: DiscreteProfile<T>): BooleanProfile =
        other.compute { this@greaterThan greaterThan this }
    infix fun <T : Comparable<T>> T.greaterThanOrEquals(other: DiscreteProfile<T>): BooleanProfile =
        other.compute { this@greaterThanOrEquals greaterThanOrEquals this }
    infix fun <T : Comparable<T>> T.lessThan(other: DiscreteProfile<T>): BooleanProfile =
        other.compute { this@lessThan lessThan this }
    infix fun <T : Comparable<T>> T.lessThanOrEquals(other: DiscreteProfile<T>): BooleanProfile =
        other.compute { this@lessThanOrEquals lessThanOrEquals this }
}