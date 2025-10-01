package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.coals.curry
import java.util.SortedMap
import kotlin.time.Instant

class DiscreteProfile<T>(
    val initialValue: T,
    val values: SortedMap<Instant, T>,
) {
    override fun toString(): String = "DiscreteProfile(\n  ${" ".repeat(20)} - $initialValue,${
        values.map { (t, v) -> "\n  $t - $v" }.joinToString(",")
    }\n)"

    /**
     * Discrete timelines form a monad, which lets us express a wide variety of operations on them.
     */
    companion object DiscreteProfileMonad {
        fun <A> pure(a: A) = DiscreteProfile(a, sortedMapOf())
        fun <A, B> apply(a: DiscreteProfile<A>, f: DiscreteProfile<(A) -> B>): DiscreteProfile<B> {
            var a0 = a.initialValue
            var f0 = f.initialValue
            val initialB = f0(a0)
            var b0 = initialB
            val resultValues = mutableMapOf<Instant, B>()
            fun update(t: Instant, b: B) {
                if (b != b0) resultValues[t] = b
                b0 = b
            }

            val aValues = a.values.entries.toMutableList()
            val fValues = f.values.entries.toMutableList()
            while (aValues.isNotEmpty() && fValues.isNotEmpty()) {
                val t = minOf(aValues.first().key, fValues.first().key)
                if (aValues.first().key == t) {
                    a0 = aValues.removeFirst().value
                }
                if (fValues.first().key == t) {
                    f0 = fValues.removeFirst().value
                }
                update(t, f0(a0))
            }
            while (aValues.isNotEmpty()) {
                val (t, a0) = aValues.removeFirst()
                update(t, f0(a0))
            }
            while (fValues.isNotEmpty()) {
                val (t, f0) = fValues.removeFirst()
                update(t, f0(a0))
            }

            return DiscreteProfile(initialB, resultValues.toSortedMap())
        }
        fun <A> join(a: DiscreteProfile<DiscreteProfile<A>>): DiscreteProfile<A> {
            val resultValues = mutableMapOf<Instant, A>()
            val futureMetaSegments = a.values.entries.toMutableList()
            fun nextMetaSegmentStart() = futureMetaSegments.firstOrNull()?.key ?: Instant.DISTANT_FUTURE
            fun addSegment(segment: DiscreteProfile<A>) = segment.values.entries
                        .takeWhile { (k, _) -> k < nextMetaSegmentStart() }
                        .forEach { (k, v) -> resultValues[k] = v }
            addSegment(a.initialValue)
            while (futureMetaSegments.isNotEmpty()) {
                        addSegment(futureMetaSegments.removeFirst().value)
                    }
            return DiscreteProfile(a.initialValue.initialValue, resultValues.toSortedMap())
        }
        fun <A, B> map(a: DiscreteProfile<A>, f: (A) -> B) = apply(a, pure(f))
        // Auxiliary methods - These are defined only in terms of pure/apply/join above, and can be copied from Monad to Monad
        fun <A, B> apply(fn: DiscreteProfile<(A) -> B>): (DiscreteProfile<A>) -> DiscreteProfile<B> = { DiscreteProfileMonad.apply(it, fn) }
        fun <A, B> map(fn: (A) -> B): (DiscreteProfile<A>) -> DiscreteProfile<B> = { DiscreteProfileMonad.map(it, fn) }
        fun <A, B> bind(a: DiscreteProfile<A>, fn: (A) -> DiscreteProfile<B>): DiscreteProfile<B> = join(map(a, fn))
        fun <A, B> bind(fn: (A) -> DiscreteProfile<B>): (DiscreteProfile<A>) -> DiscreteProfile<B> = { bind(it, fn) }
        // Auxiliary map
        fun <A, B, C> map(fn: (A, B) -> C): (DiscreteProfile<A>, DiscreteProfile<B>) -> DiscreteProfile<C> = { a, b -> map(a, b, fn) }
        fun <A, B, C> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, fn: (A, B) -> C): DiscreteProfile<C> = map(a, b, curry(fn))
        fun <A, B, C> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, fn: (A) -> (B) -> C): DiscreteProfile<C> = apply(b, map(a, fn))
        fun <A, B, C, D> map(fn: (A, B, C) -> D): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>) -> DiscreteProfile<D> = { a, b, c -> map(a, b, c, fn) }
        fun <A, B, C, D> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, fn: (A, B, C) -> D): DiscreteProfile<D> = map(a, b, c, curry(fn))
        fun <A, B, C, D> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, fn: (A) -> (B) -> (C) -> D): DiscreteProfile<D> = apply(c, map(a, b, fn))
        fun <A, B, C, D, E> map(fn: (A, B, C, D) -> E): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>) -> DiscreteProfile<E> = { a, b, c, d -> map(a, b, c, d, fn) }
        fun <A, B, C, D, E> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, fn: (A, B, C, D) -> E): DiscreteProfile<E> = map(a, b, c, d, curry(fn))
        fun <A, B, C, D, E> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, fn: (A) -> (B) -> (C) -> (D) -> E): DiscreteProfile<E> = apply(d, map(a, b, c, fn))
        fun <A, B, C, D, E, F> map(fn: (A, B, C, D, E) -> F): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>) -> DiscreteProfile<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
        fun <A, B, C, D, E, F> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, fn: (A, B, C, D, E) -> F): DiscreteProfile<F> = map(a, b, c, d, e, curry(fn))
        fun <A, B, C, D, E, F> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): DiscreteProfile<F> = apply(e, map(a, b, c, d, fn))
        fun <A, B, C, D, E, F, G> map(fn: (A, B, C, D, E, F) -> G): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>) -> DiscreteProfile<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
        fun <A, B, C, D, E, F, G> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, fn: (A, B, C, D, E, F) -> G): DiscreteProfile<G> = map(a, b, c, d, e, f, curry(fn))
        fun <A, B, C, D, E, F, G> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): DiscreteProfile<G> = apply(f, map(a, b, c, d, e, fn))
        fun <A, B, C, D, E, F, G, H> map(fn: (A, B, C, D, E, F, G) -> H): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>) -> DiscreteProfile<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
        fun <A, B, C, D, E, F, G, H> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, fn: (A, B, C, D, E, F, G) -> H): DiscreteProfile<H> = map(a, b, c, d, e, f, g, curry(fn))
        fun <A, B, C, D, E, F, G, H> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): DiscreteProfile<H> = apply(g, map(a, b, c, d, e, f, fn))
        fun <A, B, C, D, E, F, G, H, I> map(fn: (A, B, C, D, E, F, G, H) -> I): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>) -> DiscreteProfile<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
        fun <A, B, C, D, E, F, G, H, I> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, fn: (A, B, C, D, E, F, G, H) -> I): DiscreteProfile<I> = map(a, b, c, d, e, f, g, h, curry(fn))
        fun <A, B, C, D, E, F, G, H, I> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): DiscreteProfile<I> = apply(h, map(a, b, c, d, e, f, g, fn))
        fun <A, B, C, D, E, F, G, H, I, J> map(fn: (A, B, C, D, E, F, G, H, I) -> J): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>, DiscreteProfile<I>) -> DiscreteProfile<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
        fun <A, B, C, D, E, F, G, H, I, J> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, fn: (A, B, C, D, E, F, G, H, I) -> J): DiscreteProfile<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
        fun <A, B, C, D, E, F, G, H, I, J> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): DiscreteProfile<J> = apply(i, map(a, b, c, d, e, f, g, h, fn))
        fun <A, B, C, D, E, F, G, H, I, J, K> map(fn: (A, B, C, D, E, F, G, H, I, J) -> K): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>, DiscreteProfile<I>, DiscreteProfile<J>) -> DiscreteProfile<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
        fun <A, B, C, D, E, F, G, H, I, J, K> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, j: DiscreteProfile<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> K): DiscreteProfile<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
        fun <A, B, C, D, E, F, G, H, I, J, K> map(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, j: DiscreteProfile<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): DiscreteProfile<K> = apply(j, map(a, b, c, d, e, f, g, h, i, fn))
        // Auxiliary bind
        fun <A, B, C> bind(fn: (A, B) -> DiscreteProfile<C>): (DiscreteProfile<A>, DiscreteProfile<B>) -> DiscreteProfile<C> = { a, b -> bind(a, b, fn) }
        fun <A, B, C> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, fn: (A, B) -> DiscreteProfile<C>): DiscreteProfile<C> = bind(a, b, curry(fn))
        fun <A, B, C> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, fn: (A) -> (B) -> DiscreteProfile<C>): DiscreteProfile<C> = join(map(a, b, fn))
        fun <A, B, C, D> bind(fn: (A, B, C) -> DiscreteProfile<D>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>) -> DiscreteProfile<D> = { a, b, c -> bind(a, b, c, fn) }
        fun <A, B, C, D> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, fn: (A, B, C) -> DiscreteProfile<D>): DiscreteProfile<D> = bind(a, b, c, curry(fn))
        fun <A, B, C, D> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, fn: (A) -> (B) -> (C) -> DiscreteProfile<D>): DiscreteProfile<D> = join(map(a, b, c, fn))
        fun <A, B, C, D, E> bind(fn: (A, B, C, D) -> DiscreteProfile<E>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>) -> DiscreteProfile<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
        fun <A, B, C, D, E> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, fn: (A, B, C, D) -> DiscreteProfile<E>): DiscreteProfile<E> = bind(a, b, c, d, curry(fn))
        fun <A, B, C, D, E> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, fn: (A) -> (B) -> (C) -> (D) -> DiscreteProfile<E>): DiscreteProfile<E> = join(map(a, b, c, d, fn))
        fun <A, B, C, D, E, F> bind(fn: (A, B, C, D, E) -> DiscreteProfile<F>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>) -> DiscreteProfile<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
        fun <A, B, C, D, E, F> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, fn: (A, B, C, D, E) -> DiscreteProfile<F>): DiscreteProfile<F> = bind(a, b, c, d, e, curry(fn))
        fun <A, B, C, D, E, F> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> DiscreteProfile<F>): DiscreteProfile<F> = join(map(a, b, c, d, e, fn))
        fun <A, B, C, D, E, F, G> bind(fn: (A, B, C, D, E, F) -> DiscreteProfile<G>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>) -> DiscreteProfile<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
        fun <A, B, C, D, E, F, G> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, fn: (A, B, C, D, E, F) -> DiscreteProfile<G>): DiscreteProfile<G> = bind(a, b, c, d, e, f, curry(fn))
        fun <A, B, C, D, E, F, G> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> DiscreteProfile<G>): DiscreteProfile<G> = join(map(a, b, c, d, e, f, fn))
        fun <A, B, C, D, E, F, G, H> bind(fn: (A, B, C, D, E, F, G) -> DiscreteProfile<H>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>) -> DiscreteProfile<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
        fun <A, B, C, D, E, F, G, H> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, fn: (A, B, C, D, E, F, G) -> DiscreteProfile<H>): DiscreteProfile<H> = bind(a, b, c, d, e, f, g, curry(fn))
        fun <A, B, C, D, E, F, G, H> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> DiscreteProfile<H>): DiscreteProfile<H> = join(map(a, b, c, d, e, f, g, fn))
        fun <A, B, C, D, E, F, G, H, I> bind(fn: (A, B, C, D, E, F, G, H) -> DiscreteProfile<I>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>) -> DiscreteProfile<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
        fun <A, B, C, D, E, F, G, H, I> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, fn: (A, B, C, D, E, F, G, H) -> DiscreteProfile<I>): DiscreteProfile<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
        fun <A, B, C, D, E, F, G, H, I> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> DiscreteProfile<I>): DiscreteProfile<I> = join(map(a, b, c, d, e, f, g, h, fn))
        fun <A, B, C, D, E, F, G, H, I, J> bind(fn: (A, B, C, D, E, F, G, H, I) -> DiscreteProfile<J>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>, DiscreteProfile<I>) -> DiscreteProfile<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
        fun <A, B, C, D, E, F, G, H, I, J> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, fn: (A, B, C, D, E, F, G, H, I) -> DiscreteProfile<J>): DiscreteProfile<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
        fun <A, B, C, D, E, F, G, H, I, J> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> DiscreteProfile<J>): DiscreteProfile<J> = join(map(a, b, c, d, e, f, g, h, i, fn))
        fun <A, B, C, D, E, F, G, H, I, J, K> bind(fn: (A, B, C, D, E, F, G, H, I, J) -> DiscreteProfile<K>): (DiscreteProfile<A>, DiscreteProfile<B>, DiscreteProfile<C>, DiscreteProfile<D>, DiscreteProfile<E>, DiscreteProfile<F>, DiscreteProfile<G>, DiscreteProfile<H>, DiscreteProfile<I>, DiscreteProfile<J>) -> DiscreteProfile<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
        fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, j: DiscreteProfile<J>, fn: (A, B, C, D, E, F, G, H, I, J) -> DiscreteProfile<K>): DiscreteProfile<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
        fun <A, B, C, D, E, F, G, H, I, J, K> bind(a: DiscreteProfile<A>, b: DiscreteProfile<B>, c: DiscreteProfile<C>, d: DiscreteProfile<D>, e: DiscreteProfile<E>, f: DiscreteProfile<F>, g: DiscreteProfile<G>, h: DiscreteProfile<H>, i: DiscreteProfile<I>, j: DiscreteProfile<J>, fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> DiscreteProfile<K>): DiscreteProfile<K> = join(map(a, b, c, d, e, f, g, h, i, j, fn))
    }
}

