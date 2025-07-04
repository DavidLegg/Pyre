package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete.DiscreteSerializer
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.emit
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

object DiscreteResourceOperations {
    inline fun <reified V> SimulationInitContext.discreteResource(name: String, value: V, serializer: KSerializer<V> = serializer()) =
        resource(name, Discrete(value), DiscreteSerializer(serializer))

    inline fun <reified V> SparkInitContext.register(
        name: String,
        resource: DiscreteResource<V>,
        serializer: KSerializer<V> = serializer(),
    ) {
        register(name, resource, DiscreteSerializer(serializer))
    }

    inline fun <reified V> SparkInitContext.registeredDiscreteResource(name: String, value: V, serializer: KSerializer<V> = serializer()) =
        discreteResource(name, value, serializer).also { register(name, it, serializer) }

    // Generic read/write operations, specialized to discrete resources

    context (TaskScope<*>)
    suspend fun <V> MutableDiscreteResource<V>.emit(effect: (V) -> V) = this.emit(DiscreteMonad.map(effect))

    context (TaskScope<*>)
    suspend fun <V> MutableDiscreteResource<V>.set(value: V) = this.emit { _: V -> value }

    fun <T : Comparable<T>> DiscreteResource<T>.compareTo(other: DiscreteResource<T>): DiscreteResource<Int> =
        map(this, other) { x, y -> x.compareTo(y) }

    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThan(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it < 0 }
    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThanOrEquals(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it <= 0 }
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThan(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it > 0 }
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThanOrEquals(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it >= 0 }

    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThan(other: T): BooleanResource = this lessThan pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThanOrEquals(other: T): BooleanResource = this lessThanOrEquals pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThan(other: T): BooleanResource = this greaterThan pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThanOrEquals(other: T): BooleanResource = this greaterThanOrEquals pure(other)

    infix fun <T> DiscreteResource<T>.equals(other: DiscreteResource<T>): BooleanResource =
        map(this, other) { x, y -> x == y }
    infix fun <T> DiscreteResource<T>.notEquals(other: DiscreteResource<T>): BooleanResource =
        map(this, other) { x, y -> x != y }
    infix fun <T> DiscreteResource<T>.equals(other: T): BooleanResource = this equals pure(other)
    infix fun <T> DiscreteResource<T>.notEquals(other: T): BooleanResource = this notEquals pure(other)
}


