package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitializer
import gov.nasa.jpl.pyre.spark.BasicSerializers.BOOLEAN_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.DOUBLE_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.FLOAT_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.INT_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.LONG_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.STRING_SERIALIZER
import gov.nasa.jpl.pyre.spark.BasicSerializers.alias
import gov.nasa.jpl.pyre.spark.BasicSerializers.enumSerializer
import gov.nasa.jpl.pyre.spark.TaskScope
import gov.nasa.jpl.pyre.spark.resources.Expiring
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.autoEffects
import gov.nasa.jpl.pyre.spark.resources.resource
import kotlin.math.abs

/*
 * Convenience constructors
 */

fun <V> SimulationInitializer.discreteResource(name: String, value: V, serializer: Serializer<V>) =
    resource(name, Discrete(value), discreteSerializer(serializer))

fun SimulationInitializer.discreteResource(name: String, value: Int) =
    resource(name, Discrete(value), discreteSerializer(INT_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: Long) =
    resource(name, Discrete(value), discreteSerializer(LONG_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: String) =
    resource(name, Discrete(value), discreteSerializer(STRING_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: Boolean) =
    resource(name, Discrete(value), discreteSerializer(BOOLEAN_SERIALIZER))

// Automatic fuzzy equality testing for floating point

fun SimulationInitializer.discreteResource(name: String, value: Double) =
    resource(name, Discrete(value), discreteSerializer(DOUBLE_SERIALIZER), autoEffects({ x, y ->
        x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-14
    }))

fun SimulationInitializer.discreteResource(name: String, value: Float) =
    resource(name, Discrete(value), discreteSerializer(FLOAT_SERIALIZER), autoEffects({ x, y ->
        x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-6
    }))

inline fun <reified E : Enum<E>> SimulationInitializer.discreteResource(name: String, value: E) =
    resource(name, Discrete(value), discreteSerializer(enumSerializer<E>()))

/*
 * TaskScope convenience functions
 */

// Generic read/write operations, specialized to discrete resources

context (TaskScope<*>)
suspend fun <V> MutableDiscreteResource<V>.emit(effect: (V) -> V) = this.emit {
    Expiring(Discrete(effect(it.data.value)), NEVER)
}

context (TaskScope<*>)
suspend fun <V> MutableDiscreteResource<V>.set(value: V) = this.emit { _: V -> value }

/*
 * Derivation convenience functions
 */

fun <T : Comparable<T>> DiscreteResource<T>.compareTo(other: DiscreteResource<T>): Resource<Discrete<Int>> =
    DiscreteResourceMonad.map(this, other) { x, y -> x.compareTo(y) }

infix fun <T : Comparable<T>> DiscreteResource<T>.lessThan(other: DiscreteResource<T>): Resource<Discrete<Boolean>> =
    DiscreteResourceMonad.map(this.compareTo(other)) { it < 0 }
infix fun <T : Comparable<T>> DiscreteResource<T>.lessThanOrEquals(other: DiscreteResource<T>): Resource<Discrete<Boolean>> =
    DiscreteResourceMonad.map(this.compareTo(other)) { it <= 0 }
infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThan(other: DiscreteResource<T>): Resource<Discrete<Boolean>> =
    DiscreteResourceMonad.map(this.compareTo(other)) { it > 0 }
infix fun <T : Comparable<T>> DiscreteResource<T>.greaterThanOrEquals(other: DiscreteResource<T>): Resource<Discrete<Boolean>> =
    DiscreteResourceMonad.map(this.compareTo(other)) { it >= 0 }


// TODO: work out how to resolve the platform signature clashes between these so I can get resource-level operator overloads...

//operator fun Resource<Discrete<Int>>.plus(other: Resource<Discrete<Int>>): Resource<Discrete<Int>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x + y }
//operator fun Resource<Discrete<Int>>.minus(other: Resource<Discrete<Int>>): Resource<Discrete<Int>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x - y }
//operator fun Resource<Discrete<Int>>.times(other: Resource<Discrete<Int>>): Resource<Discrete<Int>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x * y }
//operator fun Resource<Discrete<Int>>.div(other: Resource<Discrete<Int>>): Resource<Discrete<Int>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x / y }
//operator fun Resource<Discrete<Int>>.rem(other: Resource<Discrete<Int>>): Resource<Discrete<Int>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x % y }
//
//operator fun Resource<Discrete<Long>>.plus(other: Resource<Discrete<Long>>): Resource<Discrete<Long>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x + y }
//operator fun Resource<Discrete<Long>>.minus(other: Resource<Discrete<Long>>): Resource<Discrete<Long>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x - y }
//operator fun Resource<Discrete<Long>>.times(other: Resource<Discrete<Long>>): Resource<Discrete<Long>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x * y }
//operator fun Resource<Discrete<Long>>.div(other: Resource<Discrete<Long>>): Resource<Discrete<Long>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x / y }
//operator fun Resource<Discrete<Long>>.rem(other: Resource<Discrete<Long>>): Resource<Discrete<Long>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x % y }
//
//operator fun Resource<Discrete<Double>>.plus(other: Resource<Discrete<Double>>): Resource<Discrete<Double>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x + y }
//operator fun Resource<Discrete<Double>>.minus(other: Resource<Discrete<Double>>): Resource<Discrete<Double>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x - y }
//operator fun Resource<Discrete<Double>>.times(other: Resource<Discrete<Double>>): Resource<Discrete<Double>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x * y }
//operator fun Resource<Discrete<Double>>.div(other: Resource<Discrete<Double>>): Resource<Discrete<Double>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x / y }
//operator fun Resource<Discrete<Double>>.rem(other: Resource<Discrete<Double>>): Resource<Discrete<Double>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x % y }
//
//operator fun Resource<Discrete<Float>>.plus(other: Resource<Discrete<Float>>): Resource<Discrete<Float>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x + y }
//operator fun Resource<Discrete<Float>>.minus(other: Resource<Discrete<Float>>): Resource<Discrete<Float>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x - y }
//operator fun Resource<Discrete<Float>>.times(other: Resource<Discrete<Float>>): Resource<Discrete<Float>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x * y }
//operator fun Resource<Discrete<Float>>.div(other: Resource<Discrete<Float>>): Resource<Discrete<Float>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x / y }
//operator fun Resource<Discrete<Float>>.rem(other: Resource<Discrete<Float>>): Resource<Discrete<Float>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x % y }
//
//operator fun Resource<Discrete<String>>.plus(other: Resource<Discrete<String>>): Resource<Discrete<String>> =
//    DiscreteResourceMonad.map(this, other) { x, y -> x + y }


// TODO: Write more specialized convenience effects, maybe split out into new files for different roles?
//   E.g., increment/decrement, increase/decrease, set, etc.

/*
 * Helper functions primarily for this file
 */

fun <V> discreteSerializer(serializer: Serializer<V>): Serializer<Discrete<V>> =
    serializer.alias(InvertibleFunction.of({ it.value }, ::Discrete))
