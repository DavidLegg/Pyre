package gov.nasa.jpl.pyre.flame.results.discrete

import gov.nasa.jpl.pyre.flame.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.flame.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.div
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.times
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.unaryMinus
import gov.nasa.jpl.pyre.foundation.resources.discrete.LongResourceOperations.unaryPlus

typealias LongProfile = DiscreteProfile<Long>

object LongProfileOperations {
    operator fun LongProfile.unaryPlus(): LongProfile = compute { +this }
    operator fun LongProfile.unaryMinus(): LongProfile = compute { -this }
    operator fun LongProfile.plus(other: LongProfile): LongProfile =
        compute { this + other.asResource() }
    operator fun LongProfile.minus(other: LongProfile): LongProfile =
        compute { this - other.asResource() }
    operator fun LongProfile.times(other: LongProfile): LongProfile =
        compute { this * other.asResource() }
    operator fun LongProfile.div(other: LongProfile): LongProfile =
        compute { this / other.asResource() }
}