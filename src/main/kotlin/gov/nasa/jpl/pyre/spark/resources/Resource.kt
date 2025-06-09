package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.curry
import gov.nasa.jpl.pyre.coals.suspend
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteMonad
import gov.nasa.jpl.pyre.spark.tasks.CellsReadableScope

fun interface ThinResource<D> {
    // TODO: Factor out a "ResourceReadScope", which we use for ConditionScope, and from which TaskScope derives.
    //  Use that scope here, so we can derive conditions from resources
    context (CellsReadableScope)
    suspend fun getDynamics() : D
}
// TODO: Consider wrapping this in Result (equiv. to ErrorCatching), building ResultMonad, and updating DynamicsMonad
typealias FullDynamics<D> = Expiring<D>
typealias Resource<D> = ThinResource<FullDynamics<D>>

/*
 * Helpers
 */

context (CellsReadableScope)
suspend fun <V, D : Dynamics<V, D>> Resource<D>.getValue(): V = this.getDynamics().data.value()

/*
 * Monads
 */

object ThinResourceMonad {
    fun <A> pure(a: A): ThinResource<A> = ThinResource { a }
    fun <A, B> apply(a: ThinResource<A>, f: ThinResource<(A) -> B>): ThinResource<B> =
        ThinResource { f.getDynamics()(a.getDynamics()) }
    fun <A> join(a: ThinResource<ThinResource<A>>): ThinResource<A> = ThinResource { a.getDynamics().getDynamics() }
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    fun <A, B> apply(f: ThinResource<(A) -> B>): (ThinResource<A>) -> ThinResource<B> = { apply(it, f) }
    fun <A, B> map(a: ThinResource<A>, f: (A) -> B): ThinResource<B> = apply(a, pure(f))
    fun <A, B> map(f: (A) -> B): (ThinResource<A>) -> ThinResource<B> = { map(it, f) }
    fun <A, B> bind(a: ThinResource<A>, f: (A) -> ThinResource<B>): ThinResource<B> = join(map(a, f))
    fun <A, B> bind(f: (A) -> ThinResource<B>): (ThinResource<A>) -> ThinResource<B> = { bind(it, f) }
    // Auxiliary map
    fun <A, B, C> map(fn: (A, B) -> C): (ThinResource<A>, ThinResource<B>) -> ThinResource<C> = { a, b -> map(a, b, fn) }
    fun <A, B, C> map(a: ThinResource<A>, b: ThinResource<B>, fn: (A, B) -> C): ThinResource<C> = map(a, b, curry(fn))
    fun <A, B, C> map(a: ThinResource<A>, b: ThinResource<B>, fn: (A) -> (B) -> C): ThinResource<C> = apply(b, map(a, fn))
    fun <A, B, C, D> map(fn: (A, B, C) -> D): (ThinResource<A>, ThinResource<B>, ThinResource<C>) -> ThinResource<D> = { a, b, c -> map(a, b, c, fn) }
    fun <A, B, C, D> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, fn: (A, B, C) -> D): ThinResource<D> = map(a, b, c, curry(fn))
    fun <A, B, C, D> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, fn: (A) -> (B) -> (C) -> D): ThinResource<D> = apply(c, map(a, b, fn))
    fun <A, B, C, D, E> map(fn: (A, B, C, D) -> E): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>) -> ThinResource<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    fun <A, B, C, D, E> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, fn: (A, B, C, D) -> E): ThinResource<E> = map(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, fn: (A) -> (B) -> (C) -> (D) -> E): ThinResource<E> = apply(d, map(a, b, c, fn))
    fun <A, B, C, D, E, F> map(fn: (A, B, C, D, E) -> F): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>) -> ThinResource<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, fn: (A, B, C, D, E) -> F): ThinResource<F> = map(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): ThinResource<F> = apply(e, map(a, b, c, d, fn))
    fun <A, B, C, D, E, F, G> map(fn: (A, B, C, D, E, F) -> G): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>) -> ThinResource<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, fn: (A, B, C, D, E, F) -> G): ThinResource<G> = map(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): ThinResource<G> = apply(f, map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G, H> map(fn: (A, B, C, D, E, F, G) -> H): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>) -> ThinResource<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, fn: (A, B, C, D, E, F, G) -> H): ThinResource<H> = map(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): ThinResource<H> = apply(g, map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H, I> map(fn: (A, B, C, D, E, F, G, H) -> I): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>) -> ThinResource<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, fn: (A, B, C, D, E, F, G, H) -> I): ThinResource<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): ThinResource<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(fn: (A, B, C, D, E, F, G, H, I) -> J): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>) -> ThinResource<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, fn: (A, B, C, D, E, F, G, H, I) -> J): ThinResource<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): ThinResource<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(fn: (A, B, C, D, E, F, G, H, I, J) -> K): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>, ThinResource<J>) -> ThinResource<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> K): ThinResource<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): ThinResource<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    fun <A, B, C> bind(fn: (A, B) -> ThinResource<C>): (ThinResource<A>, ThinResource<B>) -> ThinResource<C> = { a, b -> bind(a, b, fn) }
    fun <A, B, C> bind(a: ThinResource<A>, b: ThinResource<B>, fn: (A, B) -> ThinResource<C>): ThinResource<C> = bind(a, b, curry(fn))
    fun <A, B, C> bind(a: ThinResource<A>, b: ThinResource<B>, fn: (A) -> (B) -> ThinResource<C>): ThinResource<C> = join(map(a, b, fn))
    fun <A, B, C, D> bind(fn: (A, B, C) -> ThinResource<D>): (ThinResource<A>, ThinResource<B>, ThinResource<C>) -> ThinResource<D> = { a, b, c -> bind(a, b, c, fn) }
    fun <A, B, C, D> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, fn: (A, B, C) -> ThinResource<D>): ThinResource<D> = bind(a, b, c, curry(fn))
    fun <A, B, C, D> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, fn: (A) -> (B) -> (C) -> ThinResource<D>): ThinResource<D> = join(map(a, b, c, fn))
    fun <A, B, C, D, E> bind(fn: (A, B, C, D) -> ThinResource<E>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>) -> ThinResource<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    fun <A, B, C, D, E> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, fn: (A, B, C, D) -> ThinResource<E>): ThinResource<E> = bind(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, fn: (A) -> (B) -> (C) -> (D) -> ThinResource<E>): ThinResource<E> = join(map(a, b, c, d, fn))
    fun <A, B, C, D, E, F> bind(fn: (A, B, C, D, E) -> ThinResource<F>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>) -> ThinResource<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, fn: (A, B, C, D, E) -> ThinResource<F>): ThinResource<F> = bind(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> ThinResource<F>): ThinResource<F> = join(map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G> bind(fn: (A, B, C, D, E, F) -> ThinResource<G>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>) -> ThinResource<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, fn: (A, B, C, D, E, F) -> ThinResource<G>): ThinResource<G> = bind(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> ThinResource<G>): ThinResource<G> = join(map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H> bind(fn: (A, B, C, D, E, F, G) -> ThinResource<H>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>) -> ThinResource<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, fn: (A, B, C, D, E, F, G) -> ThinResource<H>): ThinResource<H> = bind(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> ThinResource<H>): ThinResource<H> = join(map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I> bind(fn: (A, B, C, D, E, F, G, H) -> ThinResource<I>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>) -> ThinResource<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, fn: (A, B, C, D, E, F, G, H) -> ThinResource<I>): ThinResource<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> ThinResource<I>): ThinResource<I> = join(map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(fn: (A, B, C, D, E, F, G, H, I) -> ThinResource<J>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>) -> ThinResource<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, fn: (A, B, C, D, E, F, G, H, I) -> ThinResource<J>): ThinResource<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> ThinResource<J>): ThinResource<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(fn: (A, B, C, D, E, F, G, H, I, J) -> ThinResource<K>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>, ThinResource<J>) -> ThinResource<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> ThinResource<K>): ThinResource<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> ThinResource<K>): ThinResource<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}

val DynamicsMonad = ExpiringMonad

object ResourceMonad {
    fun <A> pure(a: A): Resource<A> = ThinResourceMonad.pure(DynamicsMonad.pure(a))
    fun <A, B> apply(a: Resource<A>, f: Resource<(A) -> B>): Resource<B> =
        ThinResourceMonad.apply(a, ThinResourceMonad.map(f, DynamicsMonad::apply))
    fun <A> distribute(a: FullDynamics<ThinResource<A>>): Resource<A> =
        Resource { Expiring(a.data.getDynamics(), a.expiry) }
    fun <A> join(a: Resource<Resource<A>>): Resource<A> =
        Resource { ThinResourceMonad.map(ThinResourceMonad.join(ThinResourceMonad.map(a, ResourceMonad::distribute)), DynamicsMonad::join).getDynamics() }
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    fun <A, B> apply(f: Resource<(A) -> B>): (Resource<A>) -> Resource<B> = { ResourceMonad.apply(it, f) }
    fun <A, B> map(a: Resource<A>, f: (A) -> B): Resource<B> = ResourceMonad.apply(a, pure(f))
    fun <A, B> map(f: (A) -> B): (Resource<A>) -> Resource<B> = { map(it, f) }
    fun <A, B> bind(a: Resource<A>, f: (A) -> Resource<B>): Resource<B> = join(map(a, f))
    fun <A, B> bind(f: (A) -> Resource<B>): (Resource<A>) -> Resource<B> = { bind(it, f) }
    // Auxiliary map
    fun <A, B, C> map(fn: (A, B) -> C): (Resource<A>, Resource<B>) -> Resource<C> = { a, b -> map(a, b, fn) }
    fun <A, B, C> map(a: Resource<A>, b: Resource<B>, fn: (A, B) -> C): Resource<C> = map(a, b, curry(fn))
    fun <A, B, C> map(a: Resource<A>, b: Resource<B>, fn: (A) -> (B) -> C): Resource<C> = apply(b, map(a, fn))
    fun <A, B, C, D> map(fn: (A, B, C) -> D): (Resource<A>, Resource<B>, Resource<C>) -> Resource<D> = { a, b, c -> map(a, b, c, fn) }
    fun <A, B, C, D> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, fn: (A, B, C) -> D): Resource<D> = map(a, b, c, curry(fn))
    fun <A, B, C, D> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, fn: (A) -> (B) -> (C) -> D): Resource<D> = apply(c, map(a, b, fn))
    fun <A, B, C, D, E> map(fn: (A, B, C, D) -> E): (Resource<A>, Resource<B>, Resource<C>, Resource<D>) -> Resource<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    fun <A, B, C, D, E> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, fn: (A, B, C, D) -> E): Resource<E> = map(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, fn: (A) -> (B) -> (C) -> (D) -> E): Resource<E> = apply(d, map(a, b, c, fn))
    fun <A, B, C, D, E, F> map(fn: (A, B, C, D, E) -> F): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>) -> Resource<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, fn: (A, B, C, D, E) -> F): Resource<F> = map(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Resource<F> = apply(e, map(a, b, c, d, fn))
    fun <A, B, C, D, E, F, G> map(fn: (A, B, C, D, E, F) -> G): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>) -> Resource<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, fn: (A, B, C, D, E, F) -> G): Resource<G> = map(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Resource<G> = apply(f, map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G, H> map(fn: (A, B, C, D, E, F, G) -> H): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>) -> Resource<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, fn: (A, B, C, D, E, F, G) -> H): Resource<H> = map(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Resource<H> = apply(g, map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H, I> map(fn: (A, B, C, D, E, F, G, H) -> I): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>) -> Resource<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, fn: (A, B, C, D, E, F, G, H) -> I): Resource<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Resource<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(fn: (A, B, C, D, E, F, G, H, I) -> J): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>) -> Resource<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, fn: (A, B, C, D, E, F, G, H, I) -> J): Resource<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Resource<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>, Resource<J>) -> Resource<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> K): Resource<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Resource<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    fun <A, B, C> bind(fn: (A, B) -> Resource<C>): (Resource<A>, Resource<B>) -> Resource<C> = { a, b -> bind(a, b, fn) }
    fun <A, B, C> bind(a: Resource<A>, b: Resource<B>, fn: (A, B) -> Resource<C>): Resource<C> = bind(a, b, curry(fn))
    fun <A, B, C> bind(a: Resource<A>, b: Resource<B>, fn: (A) -> (B) -> Resource<C>): Resource<C> = join(map(a, b, fn))
    fun <A, B, C, D> bind(fn: (A, B, C) -> Resource<D>): (Resource<A>, Resource<B>, Resource<C>) -> Resource<D> = { a, b, c -> bind(a, b, c, fn) }
    fun <A, B, C, D> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, fn: (A, B, C) -> Resource<D>): Resource<D> = bind(a, b, c, curry(fn))
    fun <A, B, C, D> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, fn: (A) -> (B) -> (C) -> Resource<D>): Resource<D> = join(map(a, b, c, fn))
    fun <A, B, C, D, E> bind(fn: (A, B, C, D) -> Resource<E>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>) -> Resource<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    fun <A, B, C, D, E> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, fn: (A, B, C, D) -> Resource<E>): Resource<E> = bind(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, fn: (A) -> (B) -> (C) -> (D) -> Resource<E>): Resource<E> = join(map(a, b, c, d, fn))
    fun <A, B, C, D, E, F> bind(fn: (A, B, C, D, E) -> Resource<F>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>) -> Resource<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, fn: (A, B, C, D, E) -> Resource<F>): Resource<F> = bind(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> Resource<F>): Resource<F> = join(map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G> bind(fn: (A, B, C, D, E, F) -> Resource<G>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>) -> Resource<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, fn: (A, B, C, D, E, F) -> Resource<G>): Resource<G> = bind(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Resource<G>): Resource<G> = join(map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H> bind(fn: (A, B, C, D, E, F, G) -> Resource<H>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>) -> Resource<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, fn: (A, B, C, D, E, F, G) -> Resource<H>): Resource<H> = bind(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Resource<H>): Resource<H> = join(map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I> bind(fn: (A, B, C, D, E, F, G, H) -> Resource<I>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>) -> Resource<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, fn: (A, B, C, D, E, F, G, H) -> Resource<I>): Resource<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Resource<I>): Resource<I> = join(map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(fn: (A, B, C, D, E, F, G, H, I) -> Resource<J>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>) -> Resource<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, fn: (A, B, C, D, E, F, G, H, I) -> Resource<J>): Resource<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Resource<J>): Resource<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(fn: (A, B, C, D, E, F, G, H, I, J) -> Resource<K>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>, Resource<J>) -> Resource<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> Resource<K>): Resource<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Resource<K>): Resource<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}
