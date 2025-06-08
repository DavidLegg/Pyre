package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.BOOLEAN_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

typealias BooleanResource = DiscreteResource<Boolean>
typealias MutableBooleanResource = MutableDiscreteResource<Boolean>

object BooleanResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: Boolean) =
        discreteResource(name, value, BOOLEAN_SERIALIZER)

    fun SparkInitContext.register(name: String, resource: DiscreteResource<Boolean>) {
        register(name, resource, BOOLEAN_SERIALIZER)
    }

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

    context(SparkTaskScope<*>)
    suspend fun MutableBooleanResource.toggle() = this.emit { b: Boolean -> !b }
}