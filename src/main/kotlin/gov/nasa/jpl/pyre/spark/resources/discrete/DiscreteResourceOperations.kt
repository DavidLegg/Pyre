package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.emit
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object DiscreteResourceOperations {
    context (scope: SparkInitScope)
    fun <V> discreteResource(name: String, value: V, valueType: KType) =
        resource<V, Discrete<V>>(name, Discrete(value), Discrete::class.withArg(valueType))

    context (scope: SparkInitScope)
    inline fun <reified V> discreteResource(name: String, value: V) =
        discreteResource(name, value, typeOf<V>())

    context (scope: SparkInitScope)
    fun <V> registeredDiscreteResource(name: String, value: V, valueType: KType): MutableResource<Discrete<V>> =
        discreteResource(name, value, valueType).also { register(name, it, Discrete::class.withArg(valueType)) }

    context (scope: SparkInitScope)
    inline fun <reified V> registeredDiscreteResource(name: String, value: V) =
        registeredDiscreteResource(name, value, typeOf<V>())

    // Generic read/write operations, specialized to discrete resources

    context (scope: TaskScope)
    suspend fun <V> MutableDiscreteResource<V>.emit(effect: (V) -> V) = this.emit(DiscreteMonad.map(effect) named effect::toString)

    context (scope: TaskScope)
    suspend fun <V> MutableDiscreteResource<V>.set(value: V) = this.emit({ _: V -> value } named { "Set $this to $value" })

    fun <T : Comparable<T>> DiscreteResource<T>.compareTo(other: DiscreteResource<T>): DiscreteResource<Int> =
        map(this, other) { x, y -> x.compareTo(y) } named { "($this).compareTo($other)" }

    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThan(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it < 0 } named { "($this) < ($other)" }
    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThanOrEquals(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it <= 0 } named { "($this) <= ($other)" }
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThan(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it > 0 } named { "($this) > ($other)" }
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThanOrEquals(other: DiscreteResource<T>): BooleanResource =
        map(this.compareTo(other)) { it >= 0 } named { "($this) >= ($other)" }

    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThan(other: T): BooleanResource = this lessThan pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.lessThanOrEquals(other: T): BooleanResource = this lessThanOrEquals pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThan(other: T): BooleanResource = this greaterThan pure(other)
    infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThanOrEquals(other: T): BooleanResource = this greaterThanOrEquals pure(other)

    infix fun <T> DiscreteResource<T>.equals(other: DiscreteResource<T>): BooleanResource =
        map(this, other) { x, y -> x == y } named { "($this) == ($other)" }
    infix fun <T> DiscreteResource<T>.notEquals(other: DiscreteResource<T>): BooleanResource =
        map(this, other) { x, y -> x != y } named { "($this) != ($other)" }
    infix fun <T> DiscreteResource<T>.equals(other: T): BooleanResource = this equals pure(other)
    infix fun <T> DiscreteResource<T>.notEquals(other: T): BooleanResource = this notEquals pure(other)

    fun <T> DiscreteResource<T>.isNull(): BooleanResource =
        map(this) { it == null } named { "($this) == null" }
    fun <T> DiscreteResource<T>.isNotNull(): BooleanResource =
        map(this) { it != null } named { "($this) != null" }
}


