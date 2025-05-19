package gov.nasa.jpl.pyre.state

import org.example.gov.nasa.jpl.pyre.core.Duration
import org.example.gov.nasa.jpl.pyre.io.Serializer

data class Cell<T, E>(
    val name: String,
    val value: T,
    val serializer: Serializer<T>,
    val stepper: (T, Duration) -> T,
    val applyEffect: (T, E) -> T,
    val effectTrait: EffectTrait<E>,
) {
    interface EffectTrait<E> {
        fun empty(): E
        fun sequential(first: E, second: E): E
        fun concurrent(left: E, right: E): E
    }
}