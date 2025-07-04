package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.andThen
import gov.nasa.jpl.pyre.coals.identity
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.ember.Cell.EffectTrait
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.spark.tasks.CellsReadableScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface MutableResource<D> : Resource<D> {
    context (TaskScope<*>)
    suspend fun emit(effect: ResourceEffect<D>)
}
typealias ResourceEffect<D> = (FullDynamics<D>) -> FullDynamics<D>

context (TaskScope<*>)
suspend fun <D> MutableResource<D>.emit(effect: (D) -> D) = emit {
    Expiring(effect(it.data), NEVER)
}

context (TaskScope<*>)
suspend fun <D> MutableResource<D>.set(newDynamics: D) = emit { d: D -> newDynamics }

inline fun <V, reified D : Dynamics<V, D>> SimulationInitContext.resource(
    name: String,
    initialDynamics: D,
    serializer: KSerializer<D> = serializer<D>(),
    effectTrait: EffectTrait<ResourceEffect<D>> = autoEffects(),
) = resource(name, DynamicsMonad.pure(initialDynamics), FullDynamics.serializer(serializer), effectTrait)

fun <V, D : Dynamics<V, D>> SimulationInitContext.resource(
    name: String,
    initialDynamics: FullDynamics<D>,
    serializer: KSerializer<FullDynamics<D>>,
    effectTrait: EffectTrait<ResourceEffect<D>> = autoEffects(),
): MutableResource<D> {
    val cell = allocate(Cell(
        name,
        initialDynamics,
        serializer,
        { d, t -> d.step(t) },
        { d, effect -> effect(d) },
        effectTrait,
    ))

    return object : MutableResource<D> {
        context(TaskScope<*>)
        override suspend fun emit(effect: (FullDynamics<D>) -> FullDynamics<D>) = emit(cell, effect)

        context(CellsReadableScope)
        override suspend fun getDynamics(): FullDynamics<D> = read(cell)
    }
}

fun <D> resourceEffectTrait(concurrent: (left: ResourceEffect<D>, right: ResourceEffect<D>) -> ResourceEffect<D>) =
    object : EffectTrait<ResourceEffect<D>> {
        override fun empty(): ResourceEffect<D> = identity()
        override fun sequential(first: ResourceEffect<D>, second: ResourceEffect<D>) = first andThen second
        override fun concurrent(left: ResourceEffect<D>, right: ResourceEffect<D>) = concurrent(left, right)
    }

fun <D> commutingEffects(): EffectTrait<ResourceEffect<D>> = resourceEffectTrait { left, right -> left andThen right }

fun <D> noncommutingEffects(): EffectTrait<ResourceEffect<D>> = resourceEffectTrait { left, right ->
    throw IllegalArgumentException("Non-commuting concurrent effects!")
}

fun <D> autoEffects(resultsEqual: (FullDynamics<D>, FullDynamics<D>) -> Boolean = { r, s -> r == s}): EffectTrait<ResourceEffect<D>> =
    resourceEffectTrait { left, right -> {
        val result1 = left(right(it))
        val result2 = right(left(it))
        require(resultsEqual(result1, result2)) { "Non-commuting concurrent effects! $result1 != $result2" }
        result1
    }
}
