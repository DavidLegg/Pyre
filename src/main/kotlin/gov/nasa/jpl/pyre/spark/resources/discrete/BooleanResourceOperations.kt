package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.bind
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias BooleanResource = DiscreteResource<Boolean>
typealias MutableBooleanResource = MutableDiscreteResource<Boolean>

object BooleanResourceOperations {
    operator fun BooleanResource.not(): BooleanResource =
        map(this@not) { !it } named { "not (${this@not})" }
    // Do short-circuiting in resource operations for efficiency
    infix fun BooleanResource.and(other: BooleanResource): BooleanResource =
        bind(this@and) { if (it) other else pure(false) } named { "${this@and} and $other" }
    infix fun BooleanResource.or(other: BooleanResource): BooleanResource =
        bind(this@or) { if (it) pure(true) else other } named { "${this@or} or $other" }

    // When working with constants, short-circuit during initialization instead
    infix fun Boolean.and(other: BooleanResource): BooleanResource = if (this) other else pure(false)
    infix fun Boolean.or(other: BooleanResource): BooleanResource = if (this) pure(true) else other
    infix fun BooleanResource.and(other: Boolean): BooleanResource = other and this
    infix fun BooleanResource.or(other: Boolean): BooleanResource = other or this

    fun <D> BooleanResource.choose(ifCase: Resource<D>, elseCase: Resource<D>): Resource<D> =
        ResourceMonad.bind(this) { if (it.value()) ifCase else elseCase } named { "if ($this) then ($ifCase) else ($elseCase)" }

    context(scope: TaskScope)
    suspend fun MutableBooleanResource.toggle() = this.emit({ b: Boolean -> !b } named { "Toggle $this" })
}