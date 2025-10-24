package gov.nasa.jpl.pyre.general.results.discrete

import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.general.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.div
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.times
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.unaryMinus
import gov.nasa.jpl.pyre.foundation.resources.discrete.FloatResourceOperations.unaryPlus

typealias FloatProfile = DiscreteProfile<Float>

object FloatProfileOperations {
    operator fun FloatProfile.unaryPlus(): FloatProfile = compute { +this }
    operator fun FloatProfile.unaryMinus(): FloatProfile = compute { -this }
    operator fun FloatProfile.plus(other: FloatProfile): FloatProfile =
        compute { this + other.asResource() }
    operator fun FloatProfile.minus(other: FloatProfile): FloatProfile =
        compute { this - other.asResource() }
    operator fun FloatProfile.times(other: FloatProfile): FloatProfile =
        compute { this * other.asResource() }
    operator fun FloatProfile.div(other: FloatProfile): FloatProfile =
        compute { this / other.asResource() }
}