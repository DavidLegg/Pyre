package gov.nasa.jpl.pyre.flame.resources.unstructured

import gov.nasa.jpl.pyre.utilities.curry
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.DynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.named

interface Unstructured<A> : Dynamics<A, Unstructured<A>> {
    companion object {
        /**
         * Return an [Unstructured] dynamics, given a function returning the value
         * for any time relative to now. fn(0) is the value right now.
         */
        fun <A> of(fn: (Duration) -> A): Unstructured<A> = object : Unstructured<A> {
            override fun value() = fn(ZERO)
            override fun step(t: Duration) = of { fn(t + it) }
        }
    }
}

typealias UnstructuredResource<A> = Resource<Unstructured<A>>
typealias MutableUnstructuredResource<A> = MutableResource<Unstructured<A>>

@Suppress("NOTHING_TO_INLINE")
object UnstructuredMonad {
    inline fun <A> pure(a: A): Unstructured<A> = Unstructured.of { a }
    inline fun <A, B> apply(a: Unstructured<A>, fn: Unstructured<(A) -> B>): Unstructured<B> = Unstructured.of { fn.step(it).value()(a.step(it).value()) }
    inline fun <A> join(a: Unstructured<Unstructured<A>>): Unstructured<A> = Unstructured.of { a.step(it).value().value() }
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun <A, B> map(a: Unstructured<A>, crossinline fn: (A) -> B): Unstructured<B> = Unstructured.of { fn(a.step(it).value()) }
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun <A, B> apply(fn: Unstructured<(A) -> B>): (Unstructured<A>) -> Unstructured<B> = { apply(it, fn) }
    inline fun <A, B> map(crossinline fn: (A) -> B): (Unstructured<A>) -> Unstructured<B> = { map(it, fn) }
    inline fun <A, B> bind(a: Unstructured<A>, crossinline fn: (A) -> Unstructured<B>): Unstructured<B> =
        join(map(a, fn))
    inline fun <A, B> bind(crossinline fn: (A) -> Unstructured<B>): (Unstructured<A>) -> Unstructured<B> = { bind(it, fn) }
    // Auxiliary map
    inline fun <A, B, C> map(crossinline fn: (A, B) -> C): (Unstructured<A>, Unstructured<B>) -> Unstructured<C> = { a, b -> map(a, b, fn) }
    inline fun <A, B, C> map(a: Unstructured<A>, b: Unstructured<B>, crossinline fn: (A, B) -> C): Unstructured<C> = map(a, b, curry(fn))
    inline fun <A, B, C> map(a: Unstructured<A>, b: Unstructured<B>, crossinline fn: (A) -> (B) -> C): Unstructured<C> = apply(b, map(a, fn))
    inline fun <A, B, C, D> map(crossinline fn: (A, B, C) -> D): (Unstructured<A>, Unstructured<B>, Unstructured<C>) -> Unstructured<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun <A, B, C, D> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, crossinline fn: (A, B, C) -> D): Unstructured<D> = map(a, b, c, curry(fn))
    inline fun <A, B, C, D> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, crossinline fn: (A) -> (B) -> (C) -> D): Unstructured<D> = apply(c, map(a, b, fn))
    inline fun <A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>) -> Unstructured<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun <A, B, C, D, E> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, crossinline fn: (A, B, C, D) -> E): Unstructured<E> = map(a, b, c, d, curry(fn))
    inline fun <A, B, C, D, E> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): Unstructured<E> = apply(d, map(a, b, c, fn))
    inline fun <A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>) -> Unstructured<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun <A, B, C, D, E, F> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, crossinline fn: (A, B, C, D, E) -> F): Unstructured<F> = map(a, b, c, d, e, curry(fn))
    inline fun <A, B, C, D, E, F> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Unstructured<F> = apply(e, map(a, b, c, d, fn))
    inline fun <A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>) -> Unstructured<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun <A, B, C, D, E, F, G> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, crossinline fn: (A, B, C, D, E, F) -> G): Unstructured<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun <A, B, C, D, E, F, G> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Unstructured<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun <A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>) -> Unstructured<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun <A, B, C, D, E, F, G, H> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): Unstructured<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun <A, B, C, D, E, F, G, H> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Unstructured<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>) -> Unstructured<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun <A, B, C, D, E, F, G, H, I> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): Unstructured<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Unstructured<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>, Unstructured<I>) -> Unstructured<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): Unstructured<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Unstructured<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>, Unstructured<I>, Unstructured<J>) -> Unstructured<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, j: Unstructured<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): Unstructured<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, j: Unstructured<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Unstructured<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    inline fun <A, B, C> bind(crossinline fn: (A, B) -> Unstructured<C>): (Unstructured<A>, Unstructured<B>) -> Unstructured<C> = { a, b -> bind(a, b, fn) }
    inline fun <A, B, C> bind(a: Unstructured<A>, b: Unstructured<B>, crossinline fn: (A, B) -> Unstructured<C>): Unstructured<C> = bind(a, b, curry(fn))
    inline fun <A, B, C> bind(a: Unstructured<A>, b: Unstructured<B>, crossinline fn: (A) -> (B) -> Unstructured<C>): Unstructured<C> = join(map(a, b, fn))
    inline fun <A, B, C, D> bind(crossinline fn: (A, B, C) -> Unstructured<D>): (Unstructured<A>, Unstructured<B>, Unstructured<C>) -> Unstructured<D> = { a, b, c -> bind(a, b, c, fn) }
    inline fun <A, B, C, D> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, crossinline fn: (A, B, C) -> Unstructured<D>): Unstructured<D> = bind(a, b, c, curry(fn))
    inline fun <A, B, C, D> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, crossinline fn: (A) -> (B) -> (C) -> Unstructured<D>): Unstructured<D> = join(map(a, b, c, fn))
    inline fun <A, B, C, D, E> bind(crossinline fn: (A, B, C, D) -> Unstructured<E>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>) -> Unstructured<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    inline fun <A, B, C, D, E> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, crossinline fn: (A, B, C, D) -> Unstructured<E>): Unstructured<E> = bind(a, b, c, d, curry(fn))
    inline fun <A, B, C, D, E> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> Unstructured<E>): Unstructured<E> = join(map(a, b, c, d, fn))
    inline fun <A, B, C, D, E, F> bind(crossinline fn: (A, B, C, D, E) -> Unstructured<F>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>) -> Unstructured<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    inline fun <A, B, C, D, E, F> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, crossinline fn: (A, B, C, D, E) -> Unstructured<F>): Unstructured<F> = bind(a, b, c, d, e, curry(fn))
    inline fun <A, B, C, D, E, F> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> Unstructured<F>): Unstructured<F> = join(map(a, b, c, d, e, fn))
    inline fun <A, B, C, D, E, F, G> bind(crossinline fn: (A, B, C, D, E, F) -> Unstructured<G>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>) -> Unstructured<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    inline fun <A, B, C, D, E, F, G> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, crossinline fn: (A, B, C, D, E, F) -> Unstructured<G>): Unstructured<G> = bind(a, b, c, d, e, f, curry(fn))
    inline fun <A, B, C, D, E, F, G> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Unstructured<G>): Unstructured<G> = join(map(a, b, c, d, e, f, fn))
    inline fun <A, B, C, D, E, F, G, H> bind(crossinline fn: (A, B, C, D, E, F, G) -> Unstructured<H>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>) -> Unstructured<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    inline fun <A, B, C, D, E, F, G, H> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, crossinline fn: (A, B, C, D, E, F, G) -> Unstructured<H>): Unstructured<H> = bind(a, b, c, d, e, f, g, curry(fn))
    inline fun <A, B, C, D, E, F, G, H> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Unstructured<H>): Unstructured<H> = join(map(a, b, c, d, e, f, g, fn))
    inline fun <A, B, C, D, E, F, G, H, I> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> Unstructured<I>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>) -> Unstructured<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    inline fun <A, B, C, D, E, F, G, H, I> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> Unstructured<I>): Unstructured<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Unstructured<I>): Unstructured<I> = join(map(a, b, c, d, e, f, g, h, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> Unstructured<J>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>, Unstructured<I>) -> Unstructured<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> Unstructured<J>): Unstructured<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Unstructured<J>): Unstructured<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Unstructured<K>): (Unstructured<A>, Unstructured<B>, Unstructured<C>, Unstructured<D>, Unstructured<E>, Unstructured<F>, Unstructured<G>, Unstructured<H>, Unstructured<I>, Unstructured<J>) -> Unstructured<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, j: Unstructured<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Unstructured<K>): Unstructured<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Unstructured<A>, b: Unstructured<B>, c: Unstructured<C>, d: Unstructured<D>, e: Unstructured<E>, f: Unstructured<F>, g: Unstructured<G>, h: Unstructured<H>, i: Unstructured<I>, j: Unstructured<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Unstructured<K>): Unstructured<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}

@Suppress("NOTHING_TO_INLINE")
object UnstructuredDynamicsApplicative {
    inline fun <A> pure(a: A): FullDynamics<Unstructured<A>> = DynamicsMonad.pure(UnstructuredMonad.pure(a))
    inline fun <A, B> apply(a: FullDynamics<Unstructured<A>>, fn: FullDynamics<Unstructured<(A) -> B>>): FullDynamics<Unstructured<B>> =
        DynamicsMonad.apply(a, DynamicsMonad.map(fn, UnstructuredMonad::apply))
    // "distribute" (and consequently "join") can't be written for this type
    // An Unstructured<FullDynamics<A>> has an expiry on the inner term, which can't reasonably be preserved when you distribute.
    // There's no good way to make a continuously-varying expiry make sense... Perhaps you could just ignore it?
    // Probably better to let the end user do that explicitly, though.
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun <A, B> map(a: FullDynamics<Unstructured<A>>, crossinline fn: (A) -> B): FullDynamics<Unstructured<B>> =
        DynamicsMonad.map(a, UnstructuredMonad.map(fn))
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun <A, B> apply(fn: FullDynamics<Unstructured<(A) -> B>>): (FullDynamics<Unstructured<A>>) -> FullDynamics<Unstructured<B>> = { apply(it, fn) }
    inline fun <A, B> map(crossinline fn: (A) -> B): (FullDynamics<Unstructured<A>>) -> FullDynamics<Unstructured<B>> = { map(it, fn) }
    // Auxiliary map
    inline fun <A, B, C> map(crossinline fn: (A, B) -> C): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>) -> FullDynamics<Unstructured<C>> = { a, b -> map(a, b, fn) }
    inline fun <A, B, C> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, crossinline fn: (A, B) -> C): FullDynamics<Unstructured<C>> = map(a, b, curry(fn))
    inline fun <A, B, C> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, crossinline fn: (A) -> (B) -> C): FullDynamics<Unstructured<C>> = apply(b, map(a, fn))
    inline fun <A, B, C, D> map(crossinline fn: (A, B, C) -> D): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>) -> FullDynamics<Unstructured<D>> = { a, b, c -> map(a, b, c, fn) }
    inline fun <A, B, C, D> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, crossinline fn: (A, B, C) -> D): FullDynamics<Unstructured<D>> = map(a, b, c, curry(fn))
    inline fun <A, B, C, D> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, crossinline fn: (A) -> (B) -> (C) -> D): FullDynamics<Unstructured<D>> = apply(c, map(a, b, fn))
    inline fun <A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>) -> FullDynamics<Unstructured<E>> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun <A, B, C, D, E> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, crossinline fn: (A, B, C, D) -> E): FullDynamics<Unstructured<E>> = map(a, b, c, d, curry(fn))
    inline fun <A, B, C, D, E> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): FullDynamics<Unstructured<E>> = apply(d, map(a, b, c, fn))
    inline fun <A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>) -> FullDynamics<Unstructured<F>> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun <A, B, C, D, E, F> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, crossinline fn: (A, B, C, D, E) -> F): FullDynamics<Unstructured<F>> = map(a, b, c, d, e, curry(fn))
    inline fun <A, B, C, D, E, F> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): FullDynamics<Unstructured<F>> = apply(e, map(a, b, c, d, fn))
    inline fun <A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>, FullDynamics<Unstructured<F>>) -> FullDynamics<Unstructured<G>> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun <A, B, C, D, E, F, G> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, crossinline fn: (A, B, C, D, E, F) -> G): FullDynamics<Unstructured<G>> = map(a, b, c, d, e, f, curry(fn))
    inline fun <A, B, C, D, E, F, G> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): FullDynamics<Unstructured<G>> = apply(f, map(a, b, c, d, e, fn))
    inline fun <A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>, FullDynamics<Unstructured<F>>, FullDynamics<Unstructured<G>>) -> FullDynamics<Unstructured<H>> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun <A, B, C, D, E, F, G, H> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, crossinline fn: (A, B, C, D, E, F, G) -> H): FullDynamics<Unstructured<H>> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun <A, B, C, D, E, F, G, H> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): FullDynamics<Unstructured<H>> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>, FullDynamics<Unstructured<F>>, FullDynamics<Unstructured<G>>, FullDynamics<Unstructured<H>>) -> FullDynamics<Unstructured<I>> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun <A, B, C, D, E, F, G, H, I> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): FullDynamics<Unstructured<I>> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): FullDynamics<Unstructured<I>> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>, FullDynamics<Unstructured<F>>, FullDynamics<Unstructured<G>>, FullDynamics<Unstructured<H>>, FullDynamics<Unstructured<I>>) -> FullDynamics<Unstructured<J>> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, i: FullDynamics<Unstructured<I>>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): FullDynamics<Unstructured<J>> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, i: FullDynamics<Unstructured<I>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): FullDynamics<Unstructured<J>> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (FullDynamics<Unstructured<A>>, FullDynamics<Unstructured<B>>, FullDynamics<Unstructured<C>>, FullDynamics<Unstructured<D>>, FullDynamics<Unstructured<E>>, FullDynamics<Unstructured<F>>, FullDynamics<Unstructured<G>>, FullDynamics<Unstructured<H>>, FullDynamics<Unstructured<I>>, FullDynamics<Unstructured<J>>) -> FullDynamics<Unstructured<K>> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, i: FullDynamics<Unstructured<I>>, j: FullDynamics<Unstructured<J>>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): FullDynamics<Unstructured<K>> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: FullDynamics<Unstructured<A>>, b: FullDynamics<Unstructured<B>>, c: FullDynamics<Unstructured<C>>, d: FullDynamics<Unstructured<D>>, e: FullDynamics<Unstructured<E>>, f: FullDynamics<Unstructured<F>>, g: FullDynamics<Unstructured<G>>, h: FullDynamics<Unstructured<H>>, i: FullDynamics<Unstructured<I>>, j: FullDynamics<Unstructured<J>>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): FullDynamics<Unstructured<K>> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
}

@Suppress("NOTHING_TO_INLINE")
object UnstructuredResourceApplicative {
    inline fun <A> pure(a: A): UnstructuredResource<A> = ResourceMonad.pure(UnstructuredMonad.pure(a)) named a::toString
    inline fun <A, B> apply(a: UnstructuredResource<A>, fn: UnstructuredResource<(A) -> B>): UnstructuredResource<B> =
        ResourceMonad.apply(a, ResourceMonad.map(fn, UnstructuredMonad::apply))
    // "distribute" (and consequently "join") can't be written for this type
    // An Unstructured<Resource<A>> has an expiry on the inner term, which can't reasonably be preserved when you distribute.
    // There's no good way to make a continuously-varying expiry make sense... Perhaps you could just ignore it?
    // Probably better to let the end user do that explicitly, though.
    // Although map can be defined in terms of apply and join, writing it this way instead makes it inlinable.
    // This can be a major boon to performance, so it's worth the redundant code
    inline fun <A, B> map(a: UnstructuredResource<A>, crossinline fn: (A) -> B): UnstructuredResource<B> =
        ResourceMonad.map(a, UnstructuredMonad.map(fn))
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    inline fun <A, B> apply(fn: UnstructuredResource<(A) -> B>): (UnstructuredResource<A>) -> UnstructuredResource<B> = { apply(it, fn) }
    inline fun <A, B> map(crossinline fn: (A) -> B): (UnstructuredResource<A>) -> UnstructuredResource<B> = { map(it, fn) }
    // Auxiliary map
    inline fun <A, B, C> map(crossinline fn: (A, B) -> C): (UnstructuredResource<A>, UnstructuredResource<B>) -> UnstructuredResource<C> = { a, b -> map(a, b, fn) }
    inline fun <A, B, C> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, crossinline fn: (A, B) -> C): UnstructuredResource<C> = map(a, b, curry(fn))
    inline fun <A, B, C> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, crossinline fn: (A) -> (B) -> C): UnstructuredResource<C> = apply(b, map(a, fn))
    inline fun <A, B, C, D> map(crossinline fn: (A, B, C) -> D): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>) -> UnstructuredResource<D> = { a, b, c -> map(a, b, c, fn) }
    inline fun <A, B, C, D> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, crossinline fn: (A, B, C) -> D): UnstructuredResource<D> = map(a, b, c, curry(fn))
    inline fun <A, B, C, D> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, crossinline fn: (A) -> (B) -> (C) -> D): UnstructuredResource<D> = apply(c, map(a, b, fn))
    inline fun <A, B, C, D, E> map(crossinline fn: (A, B, C, D) -> E): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>) -> UnstructuredResource<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    inline fun <A, B, C, D, E> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, crossinline fn: (A, B, C, D) -> E): UnstructuredResource<E> = map(a, b, c, d, curry(fn))
    inline fun <A, B, C, D, E> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): UnstructuredResource<E> = apply(d, map(a, b, c, fn))
    inline fun <A, B, C, D, E, F> map(crossinline fn: (A, B, C, D, E) -> F): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>) -> UnstructuredResource<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    inline fun <A, B, C, D, E, F> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, crossinline fn: (A, B, C, D, E) -> F): UnstructuredResource<F> = map(a, b, c, d, e, curry(fn))
    inline fun <A, B, C, D, E, F> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): UnstructuredResource<F> = apply(e, map(a, b, c, d, fn))
    inline fun <A, B, C, D, E, F, G> map(crossinline fn: (A, B, C, D, E, F) -> G): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>, UnstructuredResource<F>) -> UnstructuredResource<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    inline fun <A, B, C, D, E, F, G> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, crossinline fn: (A, B, C, D, E, F) -> G): UnstructuredResource<G> = map(a, b, c, d, e, f, curry(fn))
    inline fun <A, B, C, D, E, F, G> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): UnstructuredResource<G> = apply(f, map(a, b, c, d, e, fn))
    inline fun <A, B, C, D, E, F, G, H> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>, UnstructuredResource<F>, UnstructuredResource<G>) -> UnstructuredResource<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    inline fun <A, B, C, D, E, F, G, H> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): UnstructuredResource<H> = map(a, b, c, d, e, f, g, curry(fn))
    inline fun <A, B, C, D, E, F, G, H> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): UnstructuredResource<H> = apply(g, map(a, b, c, d, e, f, fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>, UnstructuredResource<F>, UnstructuredResource<G>, UnstructuredResource<H>) -> UnstructuredResource<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    inline fun <A, B, C, D, E, F, G, H, I> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): UnstructuredResource<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): UnstructuredResource<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>, UnstructuredResource<F>, UnstructuredResource<G>, UnstructuredResource<H>, UnstructuredResource<I>) -> UnstructuredResource<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, i: UnstructuredResource<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): UnstructuredResource<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, i: UnstructuredResource<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): UnstructuredResource<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (UnstructuredResource<A>, UnstructuredResource<B>, UnstructuredResource<C>, UnstructuredResource<D>, UnstructuredResource<E>, UnstructuredResource<F>, UnstructuredResource<G>, UnstructuredResource<H>, UnstructuredResource<I>, UnstructuredResource<J>) -> UnstructuredResource<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, i: UnstructuredResource<I>, j: UnstructuredResource<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): UnstructuredResource<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    inline fun <A, B, C, D, E, F, G, H, I, J, K> map(a: UnstructuredResource<A>, b: UnstructuredResource<B>, c: UnstructuredResource<C>, d: UnstructuredResource<D>, e: UnstructuredResource<E>, f: UnstructuredResource<F>, g: UnstructuredResource<G>, h: UnstructuredResource<H>, i: UnstructuredResource<I>, j: UnstructuredResource<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): UnstructuredResource<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
}
