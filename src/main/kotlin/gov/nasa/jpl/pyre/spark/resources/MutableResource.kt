package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.coals.andThen
import gov.nasa.jpl.pyre.coals.identity
import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.ember.Cell.EffectTrait
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.spark.tasks.CellsReadableScope
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface MutableResource<D> : Resource<D> {
    context (scope: TaskScope)
    suspend fun emit(effect: ResourceEffect<D>)
}
typealias ResourceEffect<D> = (FullDynamics<D>) -> FullDynamics<D>

context (scope: TaskScope)
suspend fun <D> MutableResource<D>.emit(effect: (D) -> D) = this.emit({ it: FullDynamics<D> ->
    Expiring(effect(it.data), NEVER)
} named effect::toString)

context (scope: TaskScope)
suspend fun <D> MutableResource<D>.set(newDynamics: D) = emit({ d: D -> newDynamics } named { "Set $this to $newDynamics" })

inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.resource(
    name: String,
    initialDynamics: D,
    effectTrait: EffectTrait<ResourceEffect<D>> = autoEffects(),
) = resource(name, initialDynamics, typeOf<D>(), effectTrait)

fun <V, D : Dynamics<V, D>> SparkInitContext.resource(
    name: String,
    initialDynamics: D,
    dynamicsType: KType,
    effectTrait: EffectTrait<ResourceEffect<D>> = autoEffects(),
) = resource(name, DynamicsMonad.pure(initialDynamics), FullDynamics::class.withArg(dynamicsType), effectTrait)

fun <V, D : Dynamics<V, D>> SparkInitContext.resource(
    name: String,
    initialDynamics: FullDynamics<D>,
    fullDynamicsType: KType,
    effectTrait: EffectTrait<ResourceEffect<D>> = autoEffects(),
): MutableResource<D> {
    val cell = allocate(Cell(
        name,
        initialDynamics,
        fullDynamicsType,
        { d, t -> d.step(t) },
        { d, effect -> effect(d) },
        effectTrait,
    ))

    return object : MutableResource<D> {
        context(scope: TaskScope)
        override suspend fun emit(effect: (FullDynamics<D>) -> FullDynamics<D>) = scope.emit(cell, effect)

        context(scope: CellsReadableScope)
        override suspend fun getDynamics(): FullDynamics<D> = scope.read(cell)
    } named { name }
}

fun <D> resourceEffectTrait(concurrent: (left: ResourceEffect<D>, right: ResourceEffect<D>) -> ResourceEffect<D>) =
    object : EffectTrait<ResourceEffect<D>> {
        override fun empty(): ResourceEffect<D> = identity<FullDynamics<D>>() named { "(empty effect)" }
        override fun sequential(first: ResourceEffect<D>, second: ResourceEffect<D>) = (first andThen second) named { "($first), ($second)" }
        override fun concurrent(left: ResourceEffect<D>, right: ResourceEffect<D>) = concurrent(left, right) named { "($left) | ($right)" }
    }

fun <D> commutingEffects(): EffectTrait<ResourceEffect<D>> = resourceEffectTrait { left, right -> left andThen right }

fun <D> noncommutingEffects(): EffectTrait<ResourceEffect<D>> = resourceEffectTrait { left, right ->
    throw IllegalArgumentException("Non-commuting concurrent effects: $left vs. $right - Cell does not support concurrent effects.")
}

fun <D> autoEffects(resultsEqual: (FullDynamics<D>, FullDynamics<D>) -> Boolean = { r, s -> r == s}): EffectTrait<ResourceEffect<D>> =
    resourceEffectTrait { left, right -> {
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
