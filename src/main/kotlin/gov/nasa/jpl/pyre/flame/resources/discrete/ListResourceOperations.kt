package gov.nasa.jpl.pyre.flame.resources.discrete

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

typealias ListResource<E> = DiscreteResource<List<E>>
typealias MutableListResource<E> = MutableDiscreteResource<List<E>>

object ListResourceOperations {
    context (scope: SparkTaskScope)
    suspend fun <E> MutableListResource<E>.push(element: E) =
        emit({ it: List<E> -> it + element } named { "Push $element onto $this" })

    context (scope: SparkTaskScope)
    suspend operator fun <E> MutableListResource<E>.plusAssign(element: E) = this.push(element)

    context (scope: SparkTaskScope)
    suspend fun <E> MutableListResource<E>.pop(): E {
        val poppedElement = requireNotNull(getValue().firstOrNull()) { "$this must be non-empty to pop" }
        emit({ it: List<E> ->
            // Double-check as we do the removal that we're removing the element we plan to return.
            // Otherwise, concurrent pops could be non-deterministic or even incorrect!
            require(it.isNotEmpty()) { "Concurrent effects on list resource conflict with pop effect" }
            require(it.first() == poppedElement) { "Concurrent effects on list resource conflict with pop" }
            it.subList(1, it.size)
        } named { "Pop $poppedElement off of $this" })
        return poppedElement
    }

    fun <E> ListResource<E>.isEmpty(): BooleanResource = map(this) { it.isEmpty() } named { "($this) is empty" }
    fun <E> ListResource<E>.isNotEmpty(): BooleanResource = map(this) { it.isNotEmpty() } named { "($this) is not empty" }
}