package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.curry
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.spark.BasicSerializers.alias
import gov.nasa.jpl.pyre.spark.BasicSerializers.nullable
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER

data class Expiring<T>(val data: T, val expiry: Expiry)

fun <D : Dynamics<*, D>> Expiring<D>.step(time: Duration) = Expiring(data.step(time), expiry - time)

data class Expiry(val time: Duration?): Comparable<Expiry> {
    override fun compareTo(other: Expiry): Int {
        return if (this.time == null && other.time == null) 0
        else if (this.time == null) 1
        else if (other.time == null) -1
        else this.time.compareTo(other.time)
    }

    override fun toString(): String {
        return this.time?.toString() ?: "NEVER"
    }

    companion object {
        val NOW = Expiry(Duration.ZERO)
        val NEVER = Expiry(null)
        fun serializer() = nullable(Duration.serializer()).alias(InvertibleFunction.of(Expiry::time, ::Expiry))
    }
}

operator fun Expiry.plus(other: Expiry) = Expiry(other.time?.let { this.time?.plus(it) })
operator fun Expiry.plus(other: Duration) = Expiry(this.time?.plus(other))
operator fun Expiry.minus(other: Duration) = Expiry(this.time?.minus(other))
infix fun Expiry.or(other: Expiry) = minOf(this, other)

object ExpiringMonad {
    fun <A> pure(a: A): Expiring<A> = Expiring(a, NEVER)
    fun <A, B> apply(a: Expiring<A>, f: Expiring<(A) -> B>) =
        Expiring(f.data(a.data), f.expiry or a.expiry)
    fun <A> join(a: Expiring<Expiring<A>>) = Expiring(a.data.data, a.expiry or a.data.expiry)
    // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
    fun <A, B> apply(f: Expiring<(A) -> B>): (Expiring<A>) -> Expiring<B> = { apply(it, f) }
    fun <A, B> map(a: Expiring<A>, f: (A) -> B): Expiring<B> = apply(a, pure(f))
    fun <A, B> map(f: (A) -> B): (Expiring<A>) -> Expiring<B> = { map(it, f) }
    fun <A, B> bind(a: Expiring<A>, f: (A) -> Expiring<B>): Expiring<B> = join(map(a, f))
    fun <A, B> bind(f: (A) -> Expiring<B>): (Expiring<A>) -> Expiring<B> = { bind(it, f) }
    // Auxiliary map
    fun <A, B, C> map(fn: (A, B) -> C): (Expiring<A>, Expiring<B>) -> Expiring<C> = { a, b -> map(a, b, fn) }
    fun <A, B, C> map(a: Expiring<A>, b: Expiring<B>, fn: (A, B) -> C): Expiring<C> = map(a, b, curry(fn))
    fun <A, B, C> map(a: Expiring<A>, b: Expiring<B>, fn: (A) -> (B) -> C): Expiring<C> = apply(b, map(a, fn))
    fun <A, B, C, D> map(fn: (A, B, C) -> D): (Expiring<A>, Expiring<B>, Expiring<C>) -> Expiring<D> = { a, b, c -> map(a, b, c, fn) }
    fun <A, B, C, D> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, fn: (A, B, C) -> D): Expiring<D> = map(a, b, c, curry(fn))
    fun <A, B, C, D> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, fn: (A) -> (B) -> (C) -> D): Expiring<D> = apply(c, map(a, b, fn))
    fun <A, B, C, D, E> map(fn: (A, B, C, D) -> E): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>) -> Expiring<E> = { a, b, c, d -> map(a, b, c, d, fn) }
    fun <A, B, C, D, E> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, fn: (A, B, C, D) -> E): Expiring<E> = map(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, fn: (A) -> (B) -> (C) -> (D) -> E): Expiring<E> = apply(d, map(a, b, c, fn))
    fun <A, B, C, D, E, F> map(fn: (A, B, C, D, E) -> F): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>) -> Expiring<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, fn: (A, B, C, D, E) -> F): Expiring<F> = map(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Expiring<F> = apply(e, map(a, b, c, d, fn))
    fun <A, B, C, D, E, F, G> map(fn: (A, B, C, D, E, F) -> G): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>) -> Expiring<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, fn: (A, B, C, D, E, F) -> G): Expiring<G> = map(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Expiring<G> = apply(f, map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G, H> map(fn: (A, B, C, D, E, F, G) -> H): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>) -> Expiring<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, fn: (A, B, C, D, E, F, G) -> H): Expiring<H> = map(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Expiring<H> = apply(g, map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H, I> map(fn: (A, B, C, D, E, F, G, H) -> I): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>) -> Expiring<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, fn: (A, B, C, D, E, F, G, H) -> I): Expiring<I> = map(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Expiring<I> = apply(h, map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(fn: (A, B, C, D, E, F, G, H, I) -> J): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>, Expiring<I>) -> Expiring<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, fn: (A, B, C, D, E, F, G, H, I) -> J): Expiring<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Expiring<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>, Expiring<I>, Expiring<J>) -> Expiring<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, j: Expiring<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> K): Expiring<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> map(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, j: Expiring<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Expiring<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
    // Auxiliary bind
    fun <A, B, C> bind(fn: (A, B) -> Expiring<C>): (Expiring<A>, Expiring<B>) -> Expiring<C> = { a, b -> bind(a, b, fn) }
    fun <A, B, C> bind(a: Expiring<A>, b: Expiring<B>, fn: (A, B) -> Expiring<C>): Expiring<C> = bind(a, b, curry(fn))
    fun <A, B, C> bind(a: Expiring<A>, b: Expiring<B>, fn: (A) -> (B) -> Expiring<C>): Expiring<C> = join(map(a, b, fn))
    fun <A, B, C, D> bind(fn: (A, B, C) -> Expiring<D>): (Expiring<A>, Expiring<B>, Expiring<C>) -> Expiring<D> = { a, b, c -> bind(a, b, c, fn) }
    fun <A, B, C, D> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, fn: (A, B, C) -> Expiring<D>): Expiring<D> = bind(a, b, c, curry(fn))
    fun <A, B, C, D> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, fn: (A) -> (B) -> (C) -> Expiring<D>): Expiring<D> = join(map(a, b, c, fn))
    fun <A, B, C, D, E> bind(fn: (A, B, C, D) -> Expiring<E>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>) -> Expiring<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
    fun <A, B, C, D, E> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, fn: (A, B, C, D) -> Expiring<E>): Expiring<E> = bind(a, b, c, d, curry(fn))
    fun <A, B, C, D, E> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, fn: (A) -> (B) -> (C) -> (D) -> Expiring<E>): Expiring<E> = join(map(a, b, c, d, fn))
    fun <A, B, C, D, E, F> bind(fn: (A, B, C, D, E) -> Expiring<F>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>) -> Expiring<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
    fun <A, B, C, D, E, F> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, fn: (A, B, C, D, E) -> Expiring<F>): Expiring<F> = bind(a, b, c, d, e, curry(fn))
    fun <A, B, C, D, E, F> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> Expiring<F>): Expiring<F> = join(map(a, b, c, d, e, fn))
    fun <A, B, C, D, E, F, G> bind(fn: (A, B, C, D, E, F) -> Expiring<G>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>) -> Expiring<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
    fun <A, B, C, D, E, F, G> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, fn: (A, B, C, D, E, F) -> Expiring<G>): Expiring<G> = bind(a, b, c, d, e, f, curry(fn))
    fun <A, B, C, D, E, F, G> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Expiring<G>): Expiring<G> = join(map(a, b, c, d, e, f, fn))
    fun <A, B, C, D, E, F, G, H> bind(fn: (A, B, C, D, E, F, G) -> Expiring<H>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>) -> Expiring<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
    fun <A, B, C, D, E, F, G, H> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, fn: (A, B, C, D, E, F, G) -> Expiring<H>): Expiring<H> = bind(a, b, c, d, e, f, g, curry(fn))
    fun <A, B, C, D, E, F, G, H> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Expiring<H>): Expiring<H> = join(map(a, b, c, d, e, f, g, fn))
    fun <A, B, C, D, E, F, G, H, I> bind(fn: (A, B, C, D, E, F, G, H) -> Expiring<I>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>) -> Expiring<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
    fun <A, B, C, D, E, F, G, H, I> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, fn: (A, B, C, D, E, F, G, H) -> Expiring<I>): Expiring<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
    fun <A, B, C, D, E, F, G, H, I> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Expiring<I>): Expiring<I> = join(map(a, b, c, d, e, f, g, h, fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(fn: (A, B, C, D, E, F, G, H, I) -> Expiring<J>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>, Expiring<I>) -> Expiring<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, fn: (A, B, C, D, E, F, G, H, I) -> Expiring<J>): Expiring<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Expiring<J>): Expiring<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(fn: (A, B, C, D, E, F, G, H, I, J) -> Expiring<K>): (Expiring<A>, Expiring<B>, Expiring<C>, Expiring<D>, Expiring<E>, Expiring<F>, Expiring<G>, Expiring<H>, Expiring<I>, Expiring<J>) -> Expiring<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, j: Expiring<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> Expiring<K>): Expiring<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
    fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: Expiring<A>, b: Expiring<B>, c: Expiring<C>, d: Expiring<D>, e: Expiring<E>, f: Expiring<F>, g: Expiring<G>, h: Expiring<H>, i: Expiring<I>, j: Expiring<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Expiring<K>): Expiring<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
}
