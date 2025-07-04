package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.bind
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

typealias BooleanResource = DiscreteResource<Boolean>
typealias MutableBooleanResource = MutableDiscreteResource<Boolean>

object BooleanResourceOperations {
    operator fun BooleanResource.not(): BooleanResource =
        map(this@not) { !it }
    // Do short-circuiting in resource operations for efficiency
    infix fun BooleanResource.and(other: BooleanResource): BooleanResource =
        bind(this@and) { if (it) other else pure(false) }
    infix fun BooleanResource.or(other: BooleanResource): BooleanResource =
        bind(this@or) { if (it) pure(true) else other }

    // When working with constants, short-circuit during initialization instead
    infix fun Boolean.and(other: BooleanResource): BooleanResource = if (this) other else pure(false)
    infix fun Boolean.or(other: BooleanResource): BooleanResource = if (this) pure(true) else other
    infix fun BooleanResource.and(other: Boolean): BooleanResource = other and this
    infix fun BooleanResource.or(other: Boolean): BooleanResource = other or this

    context(scope: SparkTaskScope<*>)
    suspend fun MutableBooleanResource.toggle() = this.emit { b: Boolean -> !b }
}