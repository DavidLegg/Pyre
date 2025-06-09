package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.alias
import gov.nasa.jpl.pyre.spark.BasicSerializers.enumSerializer
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.resources.Expiring
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.emit
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.register

/*
 * Convenience constructors
 */

fun <V> SimulationInitContext.discreteResource(name: String, value: V, serializer: Serializer<V>) =
    resource(name, Discrete(value), discreteSerializer(serializer))

// Automatic fuzzy equality testing for floating point

inline fun <reified E : Enum<E>> SimulationInitContext.discreteResource(name: String, value: E) =
    resource(name, Discrete(value), discreteSerializer(enumSerializer<E>()))

/*
 * TaskScope convenience functions
 */

// Generic read/write operations, specialized to discrete resources

context (TaskScope<*>)
suspend fun <V> MutableDiscreteResource<V>.emit(effect: (V) -> V) = this.emit(DiscreteMonad.map(effect))

context (TaskScope<*>)
suspend fun <V> MutableDiscreteResource<V>.set(value: V) = this.emit { _: V -> value }

/*
 * Derivation convenience functions
 */

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

/*
 * Registration convenience functions
 */

fun <V> SparkInitContext.register(name: String, resource: DiscreteResource<V>, serializer: Serializer<V>) {
    register(name, resource, discreteSerializer(serializer))
}

/*
 * Helper functions primarily for this file
 */

fun <V> discreteSerializer(serializer: Serializer<V>): Serializer<Discrete<V>> =
    serializer.alias(InvertibleFunction.of({ it.value }, ::Discrete))
