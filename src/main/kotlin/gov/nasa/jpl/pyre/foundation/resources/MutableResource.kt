package gov.nasa.jpl.pyre.foundation.resources

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.kernel.*
import gov.nasa.jpl.pyre.foundation.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.allocate
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A [Resource] which can be mutated with a [ResourceEffect].
 * Usually constructed with [resource] or a wrapper around it.
 *
 * A [MutableResource] may be "faulted" if applying an effect throws an exception.
 * If the resource is faulted, [MutableResource.getDynamics] will through a [FaultedResourceException].
 * Faults can be cleared by using [MutableResource.set] to write a new value to it.
 */
interface MutableResource<D> : Resource<D> {
    context (scope: TaskScope)
    fun emit(effect: ResourceEffect<D>)
}
typealias ResourceEffect<D> = Effect<Result<FullDynamics<D>>>
typealias MergeResourceEffect<D> = (ResourceEffect<D>, ResourceEffect<D>) -> ResourceEffect<D>

class FaultedResourceException(message: String, cause: Throwable) : RuntimeException(message, cause)

/**
 * Emit a general effect, which operates on the dynamics stored in this resource.
 * If this resource is faulted, that fault will propagate forward.
 * If applying this effect throws an exception, this resource will fault.
 */
context (scope: TaskScope)
fun <D> MutableResource<D>.emit(effect: (D) -> D) = this.emit({ r: Result<FullDynamics<D>> ->
    // In general, any effect on a faulted cell preserves that fault.
    // Additionally, if this effect throws an exception, that puts this cell in a faulted state.
    r.mapCatching { Expiring(effect(it.data), NEVER) }
}.named(effect::toString))

/**
 * Set the dynamics (and expiry) stored in this resource.
 * If this resource is faulted, this will clear the fault.
 */
context (scope: TaskScope)
fun <D> MutableResource<D>.set(newDynamics: Expiring<D>) =
    emit({ _: Result<FullDynamics<D>> -> Result.success(newDynamics) }
        .named { "Set $this to $newDynamics" })

/**
 * Set the dynamics stored in this resource.
 * If this resource is faulted, this will clear the fault.
 */
context (scope: TaskScope)
fun <D> MutableResource<D>.set(newDynamics: D) = set(Expiring(newDynamics, NEVER))

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
    val cell = allocate(
        Name(name),
        Result.success(initialDynamics),
        Result::class.withArg(fullDynamicsType),
        { d, t -> d.mapCatching { it.step(t) } },
        mergeConcurrentEffects,
    )

    return object : MutableResource<D> {
        context(scope: TaskScope)
        override fun emit(effect: ResourceEffect<D>) = scope.emit(cell, effect)

        context(scope: ResourceScope)
        override fun getDynamics(): FullDynamics<D> = scope.read(cell).getOrElse {
            throw FaultedResourceException("Fault in resource $this", it)
        }

        override val name: Name = scope.contextName / name
        override fun toString() = name
    }
}

fun <D> commutingEffects(): MergeResourceEffect<D> = { left, right -> left andThen right }

fun <D> noncommutingEffects(): MergeResourceEffect<D> = { left, right ->
    throw IllegalArgumentException("Non-commuting concurrent effects: $left vs. $right - Cell does not support concurrent effects.")
}

fun <D> autoEffects(resultsEqual: (FullDynamics<D>, FullDynamics<D>) -> Boolean = { r, s -> r == s }): MergeResourceEffect<D> =
    { left, right -> {
        // Eagerly throw exceptions if either ordering fails.
        // In cases where many things try to write simultaneously in a non-commuting way, this fails fast.
        Result.runCatching {
            val result1 = left(right(it)).getOrThrow()
            val result2 = right(left(it)).getOrThrow()
            require(resultsEqual(result1, result2)) {
                "Non-commuting concurrent effects: $left vs. $right - autoEffects detected different results: $result1 vs. $result2"
            }
            result1
        }
    }
}

context (scope: SimulationScope)
fun <D> MutableResource<D>.named(nameFn: () -> String) = fullyNamed { scope.contextName / nameFn() }

fun <D> MutableResource<D>.fullyNamed(nameFn: () -> Name) = object : MutableResource<D> by this {
    override val name: Name get() = nameFn()
    override fun toString() = name.simpleName
}
