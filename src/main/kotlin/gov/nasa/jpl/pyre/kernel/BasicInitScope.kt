package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType

/**
 * These are the actions allowed during "initialization", before the simulation starts running.
 * Note that this is the only time we're allowed to allocate cells.
 */
interface BasicInitScope {
    fun <T: Any> allocate(
        name: Name,
        value: T,
        valueType: KType,
        stepBy: (T, Duration) -> T,
        mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ): Cell<T>
    fun <T> spawn(name: Name, step: PureTaskStep<T>)
    fun <T> read(cell: Cell<T>): T = cell.value
    fun <T> report(value: T, type: KType)

    companion object {
        context (scope: BasicInitScope)
        fun <T: Any> allocate(
            name: Name,
            value: T,
            valueType: KType,
            stepBy: (T, Duration) -> T,
            mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
        ): Cell<T> = scope.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)

        context (scope: BasicInitScope)
        fun <T> spawn(name: Name, step: PureTaskStep<T>) = scope.spawn(name, step)

        context (scope: BasicInitScope)
        fun <T> read(cell: Cell<T>): T = scope.read(cell)

        context (scope: BasicInitScope)
        fun <T> report(value: T, type: KType) = scope.report(value, type)
    }
}
