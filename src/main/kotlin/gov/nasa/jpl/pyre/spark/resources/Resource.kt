package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.suspend
import gov.nasa.jpl.pyre.spark.TaskScope

fun interface ThinResource<D> {
    context (TaskScope<*>)
    suspend fun getDynamics() : D
}
// TODO: Consider wrapping this in Result (equiv. to ErrorCatching), building ResultMonad, and updating DynamicsMonad
typealias FullDynamics<D> = Expiring<D>
typealias Resource<D> = ThinResource<FullDynamics<D>>

object ThinResourceMonad {
    fun <A> pure(a: A): ThinResource<A> = ThinResource { a }
    fun <A, B> apply(a: ThinResource<A>, f: ThinResource<(A) -> B>): ThinResource<B> =
        ThinResource { f.getDynamics()(a.getDynamics()) }
    fun <A> join(a: ThinResource<ThinResource<A>>): ThinResource<A> = ThinResource { a.getDynamics().getDynamics() }
    // TODO: Generate other methods
    fun <A, B> map(a: ThinResource<A>, f: (A) -> B): ThinResource<B> = apply(a, pure(f))
    fun <A, B> bind(a: ThinResource<A>, f: (A) -> ThinResource<B>): ThinResource<B> = join(map(a, f))
}

val DynamicsMonad = ExpiringMonad

object ResourceMonad {
    fun <A> pure(a: A): Resource<A> = ThinResourceMonad.pure(DynamicsMonad.pure(a))
    fun <A, B> apply(a: Resource<A>, f: Resource<(A) -> B>): Resource<B> =
        ThinResourceMonad.apply(a, ThinResourceMonad.map(f, DynamicsMonad::apply))
    fun <A> distribute(a: FullDynamics<ThinResource<A>>): Resource<A> =
        Resource<A> { DynamicsMonad.map(a, suspend({ it.getDynamics() })) }
    fun <A> join(a: Resource<Resource<A>>): Resource<A> =
        Resource<A> { ThinResourceMonad.map(ThinResourceMonad.join(ThinResourceMonad.map(a, ResourceMonad::distribute)), DynamicsMonad::join).getDynamics() }
    // TODO: Generate other methods
    fun <A, B> map(a: Resource<A>, f: (A) -> B): Resource<B> = apply(a, pure(f))
    fun <A, B> bind(a: Resource<A>, f: (A) -> Resource<B>): Resource<B> = join(map(a, f))
}
