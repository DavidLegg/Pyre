package gov.nasa.jpl.pyre.foundation.resources

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.kernel.*
import gov.nasa.jpl.pyre.foundation.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.allocate
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface MutableResource<D> : Resource<D> {
    context (scope: TaskScope)
    suspend fun emit(effect: ResourceEffect<D>)
}
typealias ResourceEffect<D> = Effect<FullDynamics<D>>
typealias MergeResourceEffect<D> = (ResourceEffect<D>, ResourceEffect<D>) -> ResourceEffect<D>

context (scope: TaskScope)
suspend fun <D> MutableResource<D>.emit(effect: (D) -> D) = this.emit({ it: FullDynamics<D> ->
    Expiring(effect(it.data), NEVER)
} named effect::toString)

context (scope: TaskScope)
suspend fun <D> MutableResource<D>.set(newDynamics: D) = emit({ d: D -> newDynamics } named { "Set $this to $newDynamics" })

context (scope: InitScope)
inline fun <V, reified D : Dynamics<V, D>> resource(
    name: String,
    initialDynamics: D,
    noinline mergeConcurrentEffects: MergeResourceEffect<D> = autoEffects(),
) = resource(name, initialDynamics, typeOf<D>(), mergeConcurrentEffects)

context (scope: InitScope)
fun <V, D : Dynamics<V, D>> resource(
    name: String,
    initialDynamics: D,
    dynamicsType: KType,
    mergeConcurrentEffects: MergeResourceEffect<D> = autoEffects(),
) = resource(name, DynamicsMonad.pure(initialDynamics), FullDynamics::class.withArg(dynamicsType), mergeConcurrentEffects)

context (scope: InitScope)
fun <V, D : Dynamics<V, D>> resource(
    name: String,
    initialDynamics: FullDynamics<D>,
    fullDynamicsType: KType,
    mergeConcurrentEffects: MergeResourceEffect<D> = autoEffects(),
): MutableResource<D> {
    val cell = allocate(Cell(
        Name(name),
        initialDynamics,
        fullDynamicsType,
        { d, t -> d.step(t) },
        mergeConcurrentEffects,
    ))

    return object : MutableResource<D> {
        context(scope: TaskScope)
        override suspend fun emit(effect: (FullDynamics<D>) -> FullDynamics<D>) = scope.emit(cell, effect)

        context(scope: ResourceScope)
        override fun getDynamics(): FullDynamics<D> = scope.read(cell)
    } named { name }
}

fun <D> commutingEffects(): MergeResourceEffect<D> = { left, right -> left andThen right }

fun <D> noncommutingEffects(): MergeResourceEffect<D> = { left, right ->
    throw IllegalArgumentException("Non-commuting concurrent effects: $left vs. $right - Cell does not support concurrent effects.")
}

fun <D> autoEffects(resultsEqual: (FullDynamics<D>, FullDynamics<D>) -> Boolean = { r, s -> r == s }): MergeResourceEffect<D> =
    { left, right -> {
        val result1 = left(right(it))
        val result2 = right(left(it))
        require(resultsEqual(result1, result2)) {
            "Non-commuting concurrent effects: $left vs. $right - autoEffects detected different results: $result1 vs. $result2"
        }
        result1
    }
}

/**
 * Apply a lazily-computed name to a mutable resource
 */
infix fun <D> MutableResource<D>.named(nameFn: () -> String) = object : MutableResource<D> by this {
    override fun toString(): String = nameFn()
}
