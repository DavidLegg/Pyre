package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
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
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.autoEffects
import gov.nasa.jpl.pyre.spark.resources.resource
import kotlin.math.abs

/*
 * Convenience constructors
 */

fun <V> SimulationInitializer.discreteResource(name: String, value: V, serializer: Serializer<V>): MutableResource<Discrete<V>> =
    resource(name, Discrete(value), discreteSerializer(serializer))

fun SimulationInitializer.discreteResource(name: String, value: Int): MutableResource<Discrete<Int>> =
    resource(name, Discrete(value), discreteSerializer(INT_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: Long): MutableResource<Discrete<Long>> =
    resource(name, Discrete(value), discreteSerializer(LONG_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: String): MutableResource<Discrete<String>> =
    resource(name, Discrete(value), discreteSerializer(STRING_SERIALIZER))

fun SimulationInitializer.discreteResource(name: String, value: Boolean): MutableResource<Discrete<Boolean>> =
    resource(name, Discrete(value), discreteSerializer(BOOLEAN_SERIALIZER))

// Automatic fuzzy equality testing for floating point

fun SimulationInitializer.discreteResource(name: String, value: Double): MutableResource<Discrete<Double>> =
    resource(name, Discrete(value), discreteSerializer(DOUBLE_SERIALIZER), autoEffects({ x, y ->
        x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-14
    }))

fun SimulationInitializer.discreteResource(name: String, value: Float): MutableResource<Discrete<Float>> =
    resource(name, Discrete(value), discreteSerializer(FLOAT_SERIALIZER), autoEffects({ x, y ->
        x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-6
    }))

inline fun <reified E : Enum<E>> SimulationInitializer.discreteResource(name: String, value: E): MutableResource<Discrete<E>> =
    resource(name, Discrete(value), discreteSerializer(enumSerializer<E>()))

/*
 * TaskScope convenience functions
 */

// Generic read/write operations, specialized to discrete resources

context (TaskScope<*>)
suspend fun <V> Resource<Discrete<V>>.getValue(): V = this.getDynamics().data.value

context (TaskScope<*>)
suspend fun <V> MutableResource<Discrete<V>>.emit(effect: (V) -> V) = this.emit {
    Expiring(Discrete(effect(it.data.value)), NEVER)
}

context (TaskScope<*>)
suspend fun <V> MutableResource<Discrete<V>>.set(value: V) = this.emit { _: V -> value }


// TODO: Write more specialized convenience effects, maybe split out into new files for different roles?
//   E.g., increment/decrement, increase/decrease, set, etc.

/*
 * Helper functions primarily for this file
 */

fun <V> discreteSerializer(serializer: Serializer<V>): Serializer<Discrete<V>> =
    serializer.alias(InvertibleFunction.of({ it.value }, ::Discrete))
