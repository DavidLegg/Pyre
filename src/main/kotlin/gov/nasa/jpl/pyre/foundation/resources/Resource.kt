package gov.nasa.jpl.pyre.foundation.resources

import gov.nasa.jpl.pyre.utilities.curry
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div

fun interface ThinResource<D> {
    context (scope: ResourceScope)
    fun getDynamics() : D
    val name: Name get() = Name("<anonymous>")
}
typealias FullDynamics<D> = Result<Expiring<D>>
typealias Resource<D> = ThinResource<FullDynamics<D>>

/*
 * Helpers
 */

context (scope: ResourceScope)
fun <V, D : Dynamics<V, D>> Resource<D>.getValue(): V = this.getDynamics().getOrThrow().data.value()

context (scope: SimulationScope)
fun <D> Resource<D>.named(nameFn: () -> String) = fullyNamed { scope.contextName / nameFn() }

fun <D> Resource<D>.fullyNamed(nameFn: () -> Name) = object : Resource<D> by this {
    override val name: Name get() = nameFn()
    override fun toString() = name.simpleName
}

/*
 * Monads
 */

@Suppress("NOTHING_TO_INLINE")
object ThinResourceMonad {
    inline fun<A> pure(a: A): ThinResource<A> = object : ThinResource<A> {
        context(scope: ResourceScope)
        override fun getDynamics(): A = a
        override val name: Name = Name(a.toString())
        override fun toString(): String = name.toString()
    }
    inline fun<A, B> apply(a: ThinResource<A>, fn: ThinResource<(A) -> B>): ThinResource<B> =
        ThinResource { fn.getDynamics()(a.getDynamics()) }
    inline fun<A> join(a: ThinResource<ThinResource<A>>): ThinResource<A> = ThinResource { a.getDynamics().getDynamics() }
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun<A, B> map(a: ThinResource<A>, crossinline fn: (A) -> B): ThinResource<B> =
        ThinResource { fn(a.getDynamics()) }
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun<A, B> apply(fn: ThinResource<(A) -> B>): (ThinResource<A>) -> ThinResource<B> = { apply(it, fn) }
    inline fun<A, B> map(crossinline fn: (A) -> B): (ThinResource<A>) -> ThinResource<B> = { map(it, fn) }
    inline fun<A, B> bind(a: ThinResource<A>, crossinline fn: (A) -> ThinResource<B>): ThinResource<B> = join(map(a, fn))
    inline fun<A, B> bind(crossinline fn: (A) -> ThinResource<B>): (ThinResource<A>) -> ThinResource<B> = { bind(it, fn) }
    // Auxiliary map
    inline fun<A, B, C> map(crossinline fn: (A, B) -> C): (ThinResource<A>, ThinResource<B>) -> ThinResource<C> = { a, b -> map(a, b, fn) }
    inline fun<A, B, C> map(a: ThinResource<A>, b: ThinResource<B>, crossinline fn: (A, B) -> C): ThinResource<C> = map(a, b, curry(fn))
    inline fun<A, B, C> map(a: ThinResource<A>, b: ThinResource<B>, crossinline fn: (A) -> (B) -> C): ThinResource<C> = apply(b, map(a, fn))
    inline fun<A, B, C, D> map(crossinline fn: (A, B, C) -> D): (ThinResource<A>, ThinResource<B>, ThinResource<C>) -> ThinResource<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun<A, B, C, D> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, crossinline fn: (A, B, C) -> D): ThinResource<D> = map(a, b, c, curry(fn))
    inline fun<A, B, C, D> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, crossinline fn: (A) -> (B) -> (C) -> D): ThinResource<D> = apply(c, map(a, b, fn))
    inline fun<A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>) -> ThinResource<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, crossinline fn: (A, B, C, D) -> E): ThinResource<E> = map(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): ThinResource<E> = apply(d, map(a, b, c, fn))
    inline fun<A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>) -> ThinResource<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, crossinline fn: (A, B, C, D, E) -> F): ThinResource<F> = map(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): ThinResource<F> = apply(e, map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>) -> ThinResource<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, crossinline fn: (A, B, C, D, E, F) -> G): ThinResource<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): ThinResource<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>) -> ThinResource<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): ThinResource<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): ThinResource<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>) -> ThinResource<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): ThinResource<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): ThinResource<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>) -> ThinResource<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): ThinResource<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): ThinResource<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>, ThinResource<J>) -> ThinResource<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): ThinResource<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): ThinResource<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    inline fun<A, B, C> bind(crossinline fn: (A, B) -> ThinResource<C>): (ThinResource<A>, ThinResource<B>) -> ThinResource<C> = { a, b -> bind(a, b, fn) }
    inline fun<A, B, C> bind(a: ThinResource<A>, b: ThinResource<B>, crossinline fn: (A, B) -> ThinResource<C>): ThinResource<C> = bind(a, b, curry(fn))
    inline fun<A, B, C> bind(a: ThinResource<A>, b: ThinResource<B>, crossinline fn: (A) -> (B) -> ThinResource<C>): ThinResource<C> = join(map(a, b, fn))
    inline fun<A, B, C, D> bind(crossinline fn: (A, B, C) -> ThinResource<D>): (ThinResource<A>, ThinResource<B>, ThinResource<C>) -> ThinResource<D> = { a, b, c -> bind(a, b, c, fn) }
    inline fun<A, B, C, D> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, crossinline fn: (A, B, C) -> ThinResource<D>): ThinResource<D> = bind(a, b, c, curry(fn))
    inline fun<A, B, C, D> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, crossinline fn: (A) -> (B) -> (C) -> ThinResource<D>): ThinResource<D> = join(map(a, b, c, fn))
    inline fun<A, B, C, D, E> bind(crossinline fn: (A, B, C, D) -> ThinResource<E>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>) -> ThinResource<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, crossinline fn: (A, B, C, D) -> ThinResource<E>): ThinResource<E> = bind(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> ThinResource<E>): ThinResource<E> = join(map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F> bind(crossinline fn: (A, B, C, D, E) -> ThinResource<F>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>) -> ThinResource<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, crossinline fn: (A, B, C, D, E) -> ThinResource<F>): ThinResource<F> = bind(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> ThinResource<F>): ThinResource<F> = join(map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G> bind(crossinline fn: (A, B, C, D, E, F) -> ThinResource<G>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>) -> ThinResource<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, crossinline fn: (A, B, C, D, E, F) -> ThinResource<G>): ThinResource<G> = bind(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> ThinResource<G>): ThinResource<G> = join(map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H> bind(crossinline fn: (A, B, C, D, E, F, G) -> ThinResource<H>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>) -> ThinResource<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, crossinline fn: (A, B, C, D, E, F, G) -> ThinResource<H>): ThinResource<H> = bind(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> ThinResource<H>): ThinResource<H> = join(map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> ThinResource<I>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>) -> ThinResource<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> ThinResource<I>): ThinResource<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> ThinResource<I>): ThinResource<I> = join(map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> ThinResource<J>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>) -> ThinResource<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> ThinResource<J>): ThinResource<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> ThinResource<J>): ThinResource<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> ThinResource<K>): (ThinResource<A>, ThinResource<B>, ThinResource<C>, ThinResource<D>, ThinResource<E>, ThinResource<F>, ThinResource<G>, ThinResource<H>, ThinResource<I>, ThinResource<J>) -> ThinResource<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> ThinResource<K>): ThinResource<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: ThinResource<A>, b: ThinResource<B>, c: ThinResource<C>, d: ThinResource<D>, e: ThinResource<E>, f: ThinResource<F>, g: ThinResource<G>, h: ThinResource<H>, i: ThinResource<I>, j: ThinResource<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> ThinResource<K>): ThinResource<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}

@Suppress("NOTHING_TO_INLINE")
object ResultMonad {
    inline fun<A> pure(a: A): Result<A> = Result.success(a)
    inline fun<A, B> apply(a: Result<A>, fn: Result<(A) -> B>): Result<B> =
        a.mapCatching { fn.getOrThrow()(it) }
    inline fun<A> join(a: Result<Result<A>>): Result<A> = a.mapCatching { it.getOrThrow() }
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun<A, B> map(a: Result<A>, crossinline fn: (A) -> B): Result<B> = a.mapCatching(fn)
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun<A, B> apply(fn: Result<(A) -> B>): (Result<A>) -> Result<B> = { apply(it, fn) }
    inline fun<A, B> map(crossinline fn: (A) -> B): (Result<A>) -> Result<B> = { map(it, fn) }
    inline fun<A, B> bind(a: Result<A>, crossinline fn: (A) -> Result<B>): Result<B> = join(map(a, fn))
    inline fun<A, B> bind(crossinline fn: (A) -> Result<B>): (Result<A>) -> Result<B> = { bind(it, fn) }
    // Auxiliary map
    inline fun<A, B, C> map(crossinline fn: (A, B) -> C): (Result<A>, Result<B>) -> Result<C> = { a, b -> map(a, b, fn) }
    inline fun<A, B, C> map(a: Result<A>, b: Result<B>, crossinline fn: (A, B) -> C): Result<C> = map(a, b, curry(fn))
    inline fun<A, B, C> map(a: Result<A>, b: Result<B>, crossinline fn: (A) -> (B) -> C): Result<C> = apply(b, map(a, fn))
    inline fun<A, B, C, D> map(crossinline fn: (A, B, C) -> D): (Result<A>, Result<B>, Result<C>) -> Result<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun<A, B, C, D> map(a: Result<A>, b: Result<B>, c: Result<C>, crossinline fn: (A, B, C) -> D): Result<D> = map(a, b, c, curry(fn))
    inline fun<A, B, C, D> map(a: Result<A>, b: Result<B>, c: Result<C>, crossinline fn: (A) -> (B) -> (C) -> D): Result<D> = apply(c, map(a, b, fn))
    inline fun<A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (Result<A>, Result<B>, Result<C>, Result<D>) -> Result<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, crossinline fn: (A, B, C, D) -> E): Result<E> = map(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): Result<E> = apply(d, map(a, b, c, fn))
    inline fun<A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>) -> Result<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, crossinline fn: (A, B, C, D, E) -> F): Result<F> = map(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Result<F> = apply(e, map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>) -> Result<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, crossinline fn: (A, B, C, D, E, F) -> G): Result<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Result<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>) -> Result<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): Result<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Result<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>) -> Result<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): Result<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Result<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>, Result<I>) -> Result<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): Result<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Result<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>, Result<I>, Result<J>) -> Result<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, j: Result<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): Result<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, j: Result<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Result<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    inline fun<A, B, C> bind(crossinline fn: (A, B) -> Result<C>): (Result<A>, Result<B>) -> Result<C> = { a, b -> bind(a, b, fn) }
    inline fun<A, B, C> bind(a: Result<A>, b: Result<B>, crossinline fn: (A, B) -> Result<C>): Result<C> = bind(a, b, curry(fn))
    inline fun<A, B, C> bind(a: Result<A>, b: Result<B>, crossinline fn: (A) -> (B) -> Result<C>): Result<C> = join(map(a, b, fn))
    inline fun<A, B, C, D> bind(crossinline fn: (A, B, C) -> Result<D>): (Result<A>, Result<B>, Result<C>) -> Result<D> = { a, b, c -> bind(a, b, c, fn) }
    inline fun<A, B, C, D> bind(a: Result<A>, b: Result<B>, c: Result<C>, crossinline fn: (A, B, C) -> Result<D>): Result<D> = bind(a, b, c, curry(fn))
    inline fun<A, B, C, D> bind(a: Result<A>, b: Result<B>, c: Result<C>, crossinline fn: (A) -> (B) -> (C) -> Result<D>): Result<D> = join(map(a, b, c, fn))
    inline fun<A, B, C, D, E> bind(crossinline fn: (A, B, C, D) -> Result<E>): (Result<A>, Result<B>, Result<C>, Result<D>) -> Result<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, crossinline fn: (A, B, C, D) -> Result<E>): Result<E> = bind(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> Result<E>): Result<E> = join(map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F> bind(crossinline fn: (A, B, C, D, E) -> Result<F>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>) -> Result<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, crossinline fn: (A, B, C, D, E) -> Result<F>): Result<F> = bind(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> Result<F>): Result<F> = join(map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G> bind(crossinline fn: (A, B, C, D, E, F) -> Result<G>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>) -> Result<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, crossinline fn: (A, B, C, D, E, F) -> Result<G>): Result<G> = bind(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Result<G>): Result<G> = join(map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H> bind(crossinline fn: (A, B, C, D, E, F, G) -> Result<H>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>) -> Result<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, crossinline fn: (A, B, C, D, E, F, G) -> Result<H>): Result<H> = bind(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Result<H>): Result<H> = join(map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> Result<I>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>) -> Result<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> Result<I>): Result<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Result<I>): Result<I> = join(map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> Result<J>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>, Result<I>) -> Result<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> Result<J>): Result<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Result<J>): Result<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Result<K>): (Result<A>, Result<B>, Result<C>, Result<D>, Result<E>, Result<F>, Result<G>, Result<H>, Result<I>, Result<J>) -> Result<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, j: Result<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Result<K>): Result<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: Result<A>, b: Result<B>, c: Result<C>, d: Result<D>, e: Result<E>, f: Result<F>, g: Result<G>, h: Result<H>, i: Result<I>, j: Result<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Result<K>): Result<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}

@Suppress("NOTHING_TO_INLINE")
object DynamicsMonad {
    inline fun<A> pure(a: A): FullDynamics<A> = ResultMonad.pure(ExpiringMonad.pure(a))
    inline fun<A, B> apply(a: FullDynamics<A>, fn: FullDynamics<(A) -> B>): FullDynamics<B> =
        ResultMonad.apply(a, ResultMonad.map(fn, ExpiringMonad::apply))
    inline fun<A> distribute(a: Expiring<Result<A>>): FullDynamics<A> =
        a.data.map { Expiring(it, a.expiry) }
    inline fun<A> join(a: FullDynamics<FullDynamics<A>>): FullDynamics<A> =
        ResultMonad.map(ResultMonad.join(ResultMonad.map(a, DynamicsMonad::distribute)), ExpiringMonad::join)
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun<A, B> map(a: FullDynamics<A>, crossinline fn: (A) -> B): FullDynamics<B> = ResultMonad.map(a, ExpiringMonad.map(fn))
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun<A, B> apply(fn: FullDynamics<(A) -> B>): (FullDynamics<A>) -> FullDynamics<B> = { apply(it, fn) }
    inline fun<A, B> map(crossinline fn: (A) -> B): (FullDynamics<A>) -> FullDynamics<B> = { map(it, fn) }
    inline fun<A, B> bind(a: FullDynamics<A>, crossinline fn: (A) -> FullDynamics<B>): FullDynamics<B> = join(map(a, fn))
    inline fun<A, B> bind(crossinline fn: (A) -> FullDynamics<B>): (FullDynamics<A>) -> FullDynamics<B> = { bind(it, fn) }
    // Auxiliary map
    inline fun<A, B, C> map(crossinline fn: (A, B) -> C): (FullDynamics<A>, FullDynamics<B>) -> FullDynamics<C> = { a, b -> map(a, b, fn) }
    inline fun<A, B, C> map(a: FullDynamics<A>, b: FullDynamics<B>, crossinline fn: (A, B) -> C): FullDynamics<C> = map(a, b, curry(fn))
    inline fun<A, B, C> map(a: FullDynamics<A>, b: FullDynamics<B>, crossinline fn: (A) -> (B) -> C): FullDynamics<C> = apply(b, map(a, fn))
    inline fun<A, B, C, D> map(crossinline fn: (A, B, C) -> D): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>) -> FullDynamics<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun<A, B, C, D> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, crossinline fn: (A, B, C) -> D): FullDynamics<D> = map(a, b, c, curry(fn))
    inline fun<A, B, C, D> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, crossinline fn: (A) -> (B) -> (C) -> D): FullDynamics<D> = apply(c, map(a, b, fn))
    inline fun<A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>) -> FullDynamics<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, crossinline fn: (A, B, C, D) -> E): FullDynamics<E> = map(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): FullDynamics<E> = apply(d, map(a, b, c, fn))
    inline fun<A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>) -> FullDynamics<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, crossinline fn: (A, B, C, D, E) -> F): FullDynamics<F> = map(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): FullDynamics<F> = apply(e, map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>) -> FullDynamics<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, crossinline fn: (A, B, C, D, E, F) -> G): FullDynamics<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): FullDynamics<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>) -> FullDynamics<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): FullDynamics<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): FullDynamics<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>) -> FullDynamics<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): FullDynamics<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): FullDynamics<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>, FullDynamics<I>) -> FullDynamics<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): FullDynamics<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): FullDynamics<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>, FullDynamics<I>, FullDynamics<J>) -> FullDynamics<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, j: FullDynamics<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): FullDynamics<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, j: FullDynamics<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): FullDynamics<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    inline fun<A, B, C> bind(crossinline fn: (A, B) -> FullDynamics<C>): (FullDynamics<A>, FullDynamics<B>) -> FullDynamics<C> = { a, b -> bind(a, b, fn) }
    inline fun<A, B, C> bind(a: FullDynamics<A>, b: FullDynamics<B>, crossinline fn: (A, B) -> FullDynamics<C>): FullDynamics<C> = bind(a, b, curry(fn))
    inline fun<A, B, C> bind(a: FullDynamics<A>, b: FullDynamics<B>, crossinline fn: (A) -> (B) -> FullDynamics<C>): FullDynamics<C> = join(map(a, b, fn))
    inline fun<A, B, C, D> bind(crossinline fn: (A, B, C) -> FullDynamics<D>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>) -> FullDynamics<D> = { a, b, c -> bind(a, b, c, fn) }
    inline fun<A, B, C, D> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, crossinline fn: (A, B, C) -> FullDynamics<D>): FullDynamics<D> = bind(a, b, c, curry(fn))
    inline fun<A, B, C, D> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, crossinline fn: (A) -> (B) -> (C) -> FullDynamics<D>): FullDynamics<D> = join(map(a, b, c, fn))
    inline fun<A, B, C, D, E> bind(crossinline fn: (A, B, C, D) -> FullDynamics<E>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>) -> FullDynamics<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, crossinline fn: (A, B, C, D) -> FullDynamics<E>): FullDynamics<E> = bind(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> FullDynamics<E>): FullDynamics<E> = join(map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F> bind(crossinline fn: (A, B, C, D, E) -> FullDynamics<F>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>) -> FullDynamics<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, crossinline fn: (A, B, C, D, E) -> FullDynamics<F>): FullDynamics<F> = bind(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> FullDynamics<F>): FullDynamics<F> = join(map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G> bind(crossinline fn: (A, B, C, D, E, F) -> FullDynamics<G>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>) -> FullDynamics<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, crossinline fn: (A, B, C, D, E, F) -> FullDynamics<G>): FullDynamics<G> = bind(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> FullDynamics<G>): FullDynamics<G> = join(map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H> bind(crossinline fn: (A, B, C, D, E, F, G) -> FullDynamics<H>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>) -> FullDynamics<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, crossinline fn: (A, B, C, D, E, F, G) -> FullDynamics<H>): FullDynamics<H> = bind(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> FullDynamics<H>): FullDynamics<H> = join(map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> FullDynamics<I>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>) -> FullDynamics<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> FullDynamics<I>): FullDynamics<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> FullDynamics<I>): FullDynamics<I> = join(map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> FullDynamics<J>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>, FullDynamics<I>) -> FullDynamics<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> FullDynamics<J>): FullDynamics<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> FullDynamics<J>): FullDynamics<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> FullDynamics<K>): (FullDynamics<A>, FullDynamics<B>, FullDynamics<C>, FullDynamics<D>, FullDynamics<E>, FullDynamics<F>, FullDynamics<G>, FullDynamics<H>, FullDynamics<I>, FullDynamics<J>) -> FullDynamics<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, j: FullDynamics<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> FullDynamics<K>): FullDynamics<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: FullDynamics<A>, b: FullDynamics<B>, c: FullDynamics<C>, d: FullDynamics<D>, e: FullDynamics<E>, f: FullDynamics<F>, g: FullDynamics<G>, h: FullDynamics<H>, i: FullDynamics<I>, j: FullDynamics<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> FullDynamics<K>): FullDynamics<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}


@Suppress("NOTHING_TO_INLINE")
object ResourceMonad {
    inline fun<A> pure(a: A): Resource<A> = ThinResourceMonad.pure(DynamicsMonad.pure(a))
    inline fun<A, B> apply(a: Resource<A>, fn: Resource<(A) -> B>): Resource<B> =
        ThinResourceMonad.apply(a, ThinResourceMonad.map(fn, DynamicsMonad::apply))
    inline fun<A> distribute(a: FullDynamics<ThinResource<A>>): Resource<A> =
        Resource { DynamicsMonad.map(a) { it.getDynamics() } }
    inline fun<A> join(a: Resource<Resource<A>>): Resource<A> =
        Resource { ThinResourceMonad.map(ThinResourceMonad.join(ThinResourceMonad.map(a, ResourceMonad::distribute)), DynamicsMonad::join).getDynamics() }
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun<A, B> map(a: Resource<A>, crossinline fn: (A) -> B): Resource<B> = ThinResourceMonad.map(a, DynamicsMonad.map(fn))
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun<A, B> apply(fn: Resource<(A) -> B>): (Resource<A>) -> Resource<B> = { apply(it, fn) }
    inline fun<A, B> map(crossinline fn: (A) -> B): (Resource<A>) -> Resource<B> = { map(it, fn) }
    inline fun<A, B> bind(a: Resource<A>, crossinline fn: (A) -> Resource<B>): Resource<B> = join(map(a, fn))
    inline fun<A, B> bind(crossinline fn: (A) -> Resource<B>): (Resource<A>) -> Resource<B> = { bind(it, fn) }
    // Auxiliary map
    inline fun<A, B, C> map(crossinline fn: (A, B) -> C): (Resource<A>, Resource<B>) -> Resource<C> = { a, b -> map(a, b, fn) }
    inline fun<A, B, C> map(a: Resource<A>, b: Resource<B>, crossinline fn: (A, B) -> C): Resource<C> = map(a, b, curry(fn))
    inline fun<A, B, C> map(a: Resource<A>, b: Resource<B>, crossinline fn: (A) -> (B) -> C): Resource<C> = apply(b, map(a, fn))
    inline fun<A, B, C, D> map(crossinline fn: (A, B, C) -> D): (Resource<A>, Resource<B>, Resource<C>) -> Resource<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun<A, B, C, D> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, crossinline fn: (A, B, C) -> D): Resource<D> = map(a, b, c, curry(fn))
    inline fun<A, B, C, D> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, crossinline fn: (A) -> (B) -> (C) -> D): Resource<D> = apply(c, map(a, b, fn))
    inline fun<A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (Resource<A>, Resource<B>, Resource<C>, Resource<D>) -> Resource<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, crossinline fn: (A, B, C, D) -> E): Resource<E> = map(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): Resource<E> = apply(d, map(a, b, c, fn))
    inline fun<A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>) -> Resource<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, crossinline fn: (A, B, C, D, E) -> F): Resource<F> = map(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Resource<F> = apply(e, map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>) -> Resource<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, crossinline fn: (A, B, C, D, E, F) -> G): Resource<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Resource<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>) -> Resource<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): Resource<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Resource<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>) -> Resource<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): Resource<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Resource<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>) -> Resource<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): Resource<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Resource<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>, Resource<J>) -> Resource<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): Resource<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> map(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Resource<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    inline fun<A, B, C> bind(crossinline fn: (A, B) -> Resource<C>): (Resource<A>, Resource<B>) -> Resource<C> = { a, b -> bind(a, b, fn) }
    inline fun<A, B, C> bind(a: Resource<A>, b: Resource<B>, crossinline fn: (A, B) -> Resource<C>): Resource<C> = bind(a, b, curry(fn))
    inline fun<A, B, C> bind(a: Resource<A>, b: Resource<B>, crossinline fn: (A) -> (B) -> Resource<C>): Resource<C> = join(map(a, b, fn))
    inline fun<A, B, C, D> bind(crossinline fn: (A, B, C) -> Resource<D>): (Resource<A>, Resource<B>, Resource<C>) -> Resource<D> = { a, b, c -> bind(a, b, c, fn) }
    inline fun<A, B, C, D> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, crossinline fn: (A, B, C) -> Resource<D>): Resource<D> = bind(a, b, c, curry(fn))
    inline fun<A, B, C, D> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, crossinline fn: (A) -> (B) -> (C) -> Resource<D>): Resource<D> = join(map(a, b, c, fn))
    inline fun<A, B, C, D, E> bind(crossinline fn: (A, B, C, D) -> Resource<E>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>) -> Resource<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    inline fun<A, B, C, D, E> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, crossinline fn: (A, B, C, D) -> Resource<E>): Resource<E> = bind(a, b, c, d, curry(fn))
    inline fun<A, B, C, D, E> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> Resource<E>): Resource<E> = join(map(a, b, c, d, fn))
    inline fun<A, B, C, D, E, F> bind(crossinline fn: (A, B, C, D, E) -> Resource<F>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>) -> Resource<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    inline fun<A, B, C, D, E, F> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, crossinline fn: (A, B, C, D, E) -> Resource<F>): Resource<F> = bind(a, b, c, d, e, curry(fn))
    inline fun<A, B, C, D, E, F> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> Resource<F>): Resource<F> = join(map(a, b, c, d, e, fn))
    inline fun<A, B, C, D, E, F, G> bind(crossinline fn: (A, B, C, D, E, F) -> Resource<G>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>) -> Resource<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    inline fun<A, B, C, D, E, F, G> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, crossinline fn: (A, B, C, D, E, F) -> Resource<G>): Resource<G> = bind(a, b, c, d, e, f, curry(fn))
    inline fun<A, B, C, D, E, F, G> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Resource<G>): Resource<G> = join(map(a, b, c, d, e, f, fn))
    inline fun<A, B, C, D, E, F, G, H> bind(crossinline fn: (A, B, C, D, E, F, G) -> Resource<H>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>) -> Resource<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    inline fun<A, B, C, D, E, F, G, H> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, crossinline fn: (A, B, C, D, E, F, G) -> Resource<H>): Resource<H> = bind(a, b, c, d, e, f, g, curry(fn))
    inline fun<A, B, C, D, E, F, G, H> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Resource<H>): Resource<H> = join(map(a, b, c, d, e, f, g, fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> Resource<I>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>) -> Resource<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> Resource<I>): Resource<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Resource<I>): Resource<I> = join(map(a, b, c, d, e, f, g, h, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> Resource<J>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>) -> Resource<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> Resource<J>): Resource<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Resource<J>): Resource<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Resource<K>): (Resource<A>, Resource<B>, Resource<C>, Resource<D>, Resource<E>, Resource<F>, Resource<G>, Resource<H>, Resource<I>, Resource<J>) -> Resource<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Resource<K>): Resource<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun<A, B, C, D, E, F, G, H, I, J, K> bind(a: Resource<A>, b: Resource<B>, c: Resource<C>, d: Resource<D>, e: Resource<E>, f: Resource<F>, g: Resource<G>, h: Resource<H>, i: Resource<I>, j: Resource<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Resource<K>): Resource<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}
