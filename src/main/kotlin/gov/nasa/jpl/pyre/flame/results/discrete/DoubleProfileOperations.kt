package gov.nasa.jpl.pyre.flame.results.discrete

import gov.nasa.jpl.pyre.flame.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.flame.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.div
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.minus
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.plus
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.times
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.unaryMinus
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.unaryPlus

typealias DoubleProfile = DiscreteProfile<Double>

object DoubleProfileOperations {
    operator fun DoubleProfile.unaryPlus(): DoubleProfile = compute { +this }
    operator fun DoubleProfile.unaryMinus(): DoubleProfile = compute { -this }
    operator fun DoubleProfile.plus(other: DoubleProfile): DoubleProfile =
        compute { this + other.asResource() }
    operator fun DoubleProfile.minus(other: DoubleProfile): DoubleProfile =
        compute { this - other.asResource() }
    operator fun DoubleProfile.times(other: DoubleProfile): DoubleProfile =
        compute { this * other.asResource() }
    operator fun DoubleProfile.div(other: DoubleProfile): DoubleProfile =
        compute { this / other.asResource() }
}