package gov.nasa.jpl.pyre.ember

import kotlin.reflect.KType

data class Cell<T, E>(
    val name: String,
    val value: T,
    val valueType: KType,
    val stepBy: (T, Duration) -> T,
    val applyEffect: (T, E) -> T,
    val effectTrait: EffectTrait<E>,
) {
    interface EffectTrait<E> {
        fun empty(): E
        fun sequential(first: E, second: E): E
        fun concurrent(left: E, right: E): E
    }
}