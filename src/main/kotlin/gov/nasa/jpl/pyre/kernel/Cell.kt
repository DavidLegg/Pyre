package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType

typealias Effect<T> = (T) -> T

data class Cell<T>(
    val name: String,
    val value: T,
    val valueType: KType,
    val stepBy: (T, Duration) -> T,
    val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
)