package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType
import kotlin.reflect.typeOf

// TODO: Should we specialize on keys being Name?
// TODO: Implement this class by porting over code from MutableSnapshot, but simplify in the process

/**
 * A map where the value varies between keys.
 *
 * Supports efficient (de)serialization.
 */
sealed interface DependentMap<K> {
    fun <V> get(key: K, valueType: KType): V?

    companion object {
        inline fun <K, reified V> DependentMap<K>.get(key: K): V? = get(key, typeOf<V>())
    }
}

sealed interface MutableDependentMap<K> : DependentMap<K> {
    fun <V> put(key: K, value: V, valueType: KType)

    companion object {
        inline fun <K, reified V> MutableDependentMap<K>.put(key: K, value: V) = put(key, value, typeOf<V>())
    }
}

fun <K> MutableDependentMap(): MutableDependentMap<K> = TODO()
