package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType

typealias Effect<T> = (T) -> T

// Cell is class, not data class, because we want to use object-identity equality
interface Cell<T> {
    val name: Name
    val valueType: KType
    val stepBy: (T, Duration) -> T
    val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
}

class CellImpl<T> internal constructor(
    override val name: Name,
    internal var value: T,
    override val valueType: KType,
    override val stepBy: (T, Duration) -> T,
    override val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    /** Internal bookkeeping: the value this cell had before being modified on this branch */
    internal var trunkValue: T? = null,
    /** Internal bookkeeping: the net effect of all branches in this batch */
    internal var trunkNetEffect: NetEffect<T>? = null,
    /** Internal bookkeeping: the net effect of this branch only */
    internal var branchNetEffect: Effect<T>? = null,
) : Cell<T> {
    override fun toString() = "$name = $value"
}

/** Internal bookkeeping class used by the simulator itself. */
internal class NetEffect<T>(
    internal var value: T?,
    internal var effect: Effect<T>,
)
