package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.spark.TaskScope
import gov.nasa.jpl.pyre.spark.condition
import gov.nasa.jpl.pyre.spark.resources.getValue

typealias BooleanResource = DiscreteResource<Boolean>

// Since overload conflicts are common between resource derivation operations due to erasure,
// bundle resource operations into classes like this.
// To use them, one can either static-import them, or do something like
// with (BooleanResourceOperations) {
//   var both = someResource and someOtherResource
// }

object BooleanResourceOperations {
    operator fun BooleanResource.not(): BooleanResource = with (DiscreteResourceMonad) {
        map(this@not) { !it }
    }
    // Do short-circuiting in resource operations for efficiency
    infix fun BooleanResource.and(other: BooleanResource): BooleanResource = with(DiscreteResourceMonad) {
        bind(this@and) { if (!it) pure(false) else other }
    }
    infix fun BooleanResource.or(other: BooleanResource): BooleanResource = with(DiscreteResourceMonad) {
        bind(this@or) { if (it) pure(true) else other }
    }
}