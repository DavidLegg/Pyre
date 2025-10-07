package gov.nasa.jpl.pyre.flame.results.profiles

import gov.nasa.jpl.pyre.coals.curry
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Expiring
import gov.nasa.jpl.pyre.spark.resources.ExpiringMonad
import gov.nasa.jpl.pyre.spark.resources.Expiry
import gov.nasa.jpl.pyre.spark.resources.Expiry.Companion.NEVER
import gov.nasa.jpl.pyre.spark.resources.minus
import gov.nasa.jpl.pyre.spark.resources.step
import kotlin.time.Instant

class Profile<D : Dynamics<*, D>>(
    val start: Instant,
    val segments: List<Expiring<D>>,
) : Dynamics<D?, Profile<D>> {
    override fun value(): D? = segments.firstOrNull()?.data

    override fun step(t: Duration): Profile<D> {
        if (t == ZERO) return this

        // Consider whether there's a way to avoid this requirement - it's clunky and feels unnecessary
        require(t > ZERO) {
            "step only accepts positive durations"
        }

        val steppedSegments = segments.toMutableList()
        var timeRemaining = t
        while (steppedSegments.isNotEmpty()) {
            val head = steppedSegments.first()
            if (Expiry(t) >= head.expiry) {
                steppedSegments.removeFirst()
                timeRemaining -= head.expiry.time!!
            } else {
                steppedSegments[0] = head.step(timeRemaining)
                break
            }
        }
        return Profile(start + t.toKotlinDuration(), steppedSegments)
    }

    fun span(): OpenEndRange<Instant> = start..<(segments.map {it.expiry}.reduce { t, s -> t + s }.time?.let { start + it.toKotlinDuration() } ?: Instant.DISTANT_FUTURE)

    @Suppress("NOTHING_TO_INLINE")
    companion object ProfileMonad {
        inline fun <A : Dynamics<*, A>> pure(a: A): Profile<A> =
            Profile(Instant.DISTANT_PAST, listOf(Expiring(a, NEVER)))
        // Note: ProfileMonad is not applicative, (can't write "apply") because functions aren't dynamics, so can't be profile type arguments.
        // This is handled implicitly in ResourceMonad, "cheating" the type system a little bit, but we can't do that here.
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>> map(a: Profile<A>, crossinline fn: (A) -> B): Profile<B> =
            Profile(a.start, a.segments.map(ExpiringMonad.map(fn)))
        inline fun <A : Dynamics<*, A>> join(a: Profile<Profile<A>>): Profile<A> {
            var metaSegmentStart = a.start
            val resultSegments = mutableListOf<Expiring<A>>()
            for (metaSegment in a.segments) {
                var metaSegmentTimeRemaining = metaSegment.expiry
                for (segment in metaSegment.data.step((metaSegmentStart - metaSegment.data.start).toPyreDuration()).segments) {
                    if (segment.expiry < metaSegmentTimeRemaining) {
                        // Segment doesn't cover all of meta-segment; add it and move to next segment
                        resultSegments += segment
                        // Non-null assertion is safe because segment.expiry < another expiry, so can't be NEVER
                        metaSegmentTimeRemaining -= segment.expiry.time!!
                    } else {
                        // Segment covers all of meta-segment; add it (limited to this meta-segment) and move to next meta-segment
                        resultSegments += Expiring(segment.data, metaSegmentTimeRemaining)
                        metaSegmentTimeRemaining = Expiry(ZERO)
                        break
                    }
                }
                val metaSegmentEnd = metaSegment.expiry.time?.let { metaSegmentStart + it.toKotlinDuration() } ?: Instant.DISTANT_FUTURE
                require(metaSegmentTimeRemaining == Expiry(ZERO)) {
                    "Cannot join profiles - coverage is not complete for $metaSegmentStart - $metaSegmentEnd"
                }
                metaSegmentStart = metaSegmentEnd
            }
            return Profile(a.start, resultSegments)
        }
        // Auxiliary methods - These are defined only in terms of pure/apply/join above
        // Note that because ProfileMonad is not an Applicative, we have to use a bind-based construction rather than apply-based.
        // This is less efficient because it adds and removes layers of wrapping, but it does work.
        // It would be nice if we could recover the apply-based construction, somehow.
        // Fundamentally, we just need a way to describe how to distribute the "step" operation through the function type to its arguments...
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>> map(crossinline fn: (A) -> B): (Profile<A>) -> Profile<B> = { map(it, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>> bind(a: Profile<A>, crossinline fn: (A) -> Profile<B>): Profile<B> =
            join(map(a, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>> bind(crossinline fn: (A) -> Profile<B>): (Profile<A>) -> Profile<B> = { bind(it, fn) }
        // Auxiliary map
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> map(crossinline fn: (A, B) -> C): (Profile<A>, Profile<B>) -> Profile<C> = { a, b -> map(a, b, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> map(a: Profile<A>, b: Profile<B>, crossinline fn: (A, B) -> C): Profile<C> = map(a, b, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> map(a: Profile<A>, b: Profile<B>, crossinline fn: (A) -> (B) -> C): Profile<C> = bind(a, b, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> map(crossinline fn: (A, B, C) -> D): (Profile<A>, Profile<B>, Profile<C>) -> Profile<D> = { a, b, c -> map(a, b, c, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, crossinline fn: (A, B, C) -> D): Profile<D> = map(a, b, c, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, crossinline fn: (A) -> (B) -> (C) -> D): Profile<D> = bind(a, b, c, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> map(crossinline fn: (A, B, C, D) -> E): (Profile<A>, Profile<B>, Profile<C>, Profile<D>) -> Profile<E> = { a, b, c, d -> map(a, b, c, d, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, crossinline fn: (A, B, C, D) -> E): Profile<E> = map(a, b, c, d, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> E): Profile<E> = bind(a, b, c, d, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> map(crossinline fn: (A, B, C, D, E) -> F): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>) -> Profile<F> = { a, b, c, d, e -> map(a, b, c, d, e, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, crossinline fn: (A, B, C, D, E) -> F): Profile<F> = map(a, b, c, d, e, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> F): Profile<F> = bind(a, b, c, d, e, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> map(crossinline fn: (A, B, C, D, E, F) -> G): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>) -> Profile<G> = { a, b, c, d, e, f -> map(a, b, c, d, e, f, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, crossinline fn: (A, B, C, D, E, F) -> G): Profile<G> = map(a, b, c, d, e, f, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G): Profile<G> = bind(a, b, c, d, e, f, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> map(crossinline fn: (A, B, C, D, E, F, G) -> H): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>) -> Profile<H> = { a, b, c, d, e, f, g -> map(a, b, c, d, e, f, g, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, crossinline fn: (A, B, C, D, E, F, G) -> H): Profile<H> = map(a, b, c, d, e, f, g, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H): Profile<H> = bind(a, b, c, d, e, f, g, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> map(crossinline fn: (A, B, C, D, E, F, G, H) -> I): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>) -> Profile<I> = { a, b, c, d, e, f, g, h -> map(a, b, c, d, e, f, g, h, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> I): Profile<I> = map(a, b, c, d, e, f, g, h, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I): Profile<I> = bind(a, b, c, d, e, f, g, h, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> map(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>, Profile<I>) -> Profile<J> = { a, b, c, d, e, f, g, h, i -> map(a, b, c, d, e, f, g, h, i, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> J): Profile<J> = map(a, b, c, d, e, f, g, h, i, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J): Profile<J> = bind(a, b, c, d, e, f, g, h, i, fn.andThen(::pure))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> map(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>, Profile<I>, Profile<J>) -> Profile<K> = { a, b, c, d, e, f, g, h, i, j -> map(a, b, c, d, e, f, g, h, i, j, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, j: Profile<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K): Profile<K> = map(a, b, c, d, e, f, g, h, i, j, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> map(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, j: Profile<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K): Profile<K> = bind(a, b, c, d, e, f, g, h, i, j, fn.andThen(::pure))
        // Auxiliary bind
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> bind(crossinline fn: (A, B) -> Profile<C>): (Profile<A>, Profile<B>) -> Profile<C> = { a, b -> bind(a, b, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> bind(a: Profile<A>, b: Profile<B>, crossinline fn: (A, B) -> Profile<C>): Profile<C> = bind(a, b, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>> bind(a: Profile<A>, b: Profile<B>, crossinline fn: (A) -> (B) -> Profile<C>): Profile<C> = bind(b, bind(a, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> bind(crossinline fn: (A, B, C) -> Profile<D>): (Profile<A>, Profile<B>, Profile<C>) -> Profile<D> = { a, b, c -> bind(a, b, c, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, crossinline fn: (A, B, C) -> Profile<D>): Profile<D> = bind(a, b, c, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, crossinline fn: (A) -> (B) -> (C) -> Profile<D>): Profile<D> = bind(c, bind(a, b, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> bind(crossinline fn: (A, B, C, D) -> Profile<E>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>) -> Profile<E> = { a, b, c, d -> bind(a, b, c, d, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, crossinline fn: (A, B, C, D) -> Profile<E>): Profile<E> = bind(a, b, c, d, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, crossinline fn: (A) -> (B) -> (C) -> (D) -> Profile<E>): Profile<E> = bind(d, bind(a, b, c, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> bind(crossinline fn: (A, B, C, D, E) -> Profile<F>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>) -> Profile<F> = { a, b, c, d, e -> bind(a, b, c, d, e, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, crossinline fn: (A, B, C, D, E) -> Profile<F>): Profile<F> = bind(a, b, c, d, e, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> Profile<F>): Profile<F> = bind(e, bind(a, b, c, d, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> bind(crossinline fn: (A, B, C, D, E, F) -> Profile<G>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>) -> Profile<G> = { a, b, c, d, e, f -> bind(a, b, c, d, e, f, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, crossinline fn: (A, B, C, D, E, F) -> Profile<G>): Profile<G> = bind(a, b, c, d, e, f, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> Profile<G>): Profile<G> = bind(f, bind(a, b, c, d, e, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> bind(crossinline fn: (A, B, C, D, E, F, G) -> Profile<H>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>) -> Profile<H> = { a, b, c, d, e, f, g -> bind(a, b, c, d, e, f, g, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, crossinline fn: (A, B, C, D, E, F, G) -> Profile<H>): Profile<H> = bind(a, b, c, d, e, f, g, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> Profile<H>): Profile<H> = bind(g, bind(a, b, c, d, e, f, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> bind(crossinline fn: (A, B, C, D, E, F, G, H) -> Profile<I>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>) -> Profile<I> = { a, b, c, d, e, f, g, h -> bind(a, b, c, d, e, f, g, h, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, crossinline fn: (A, B, C, D, E, F, G, H) -> Profile<I>): Profile<I> = bind(a, b, c, d, e, f, g, h, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> Profile<I>): Profile<I> = bind(h, bind(a, b, c, d, e, f, g, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> bind(crossinline fn: (A, B, C, D, E, F, G, H, I) -> Profile<J>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>, Profile<I>) -> Profile<J> = { a, b, c, d, e, f, g, h, i -> bind(a, b, c, d, e, f, g, h, i, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, crossinline fn: (A, B, C, D, E, F, G, H, I) -> Profile<J>): Profile<J> = bind(a, b, c, d, e, f, g, h, i, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> Profile<J>): Profile<J> = bind(i, bind(a, b, c, d, e, f, g, h, fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> bind(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Profile<K>): (Profile<A>, Profile<B>, Profile<C>, Profile<D>, Profile<E>, Profile<F>, Profile<G>, Profile<H>, Profile<I>, Profile<J>) -> Profile<K> = { a, b, c, d, e, f, g, h, i, j -> bind(a, b, c, d, e, f, g, h, i, j, fn) }
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, j: Profile<J>, crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> Profile<K>): Profile<K> = bind(a, b, c, d, e, f, g, h, i, j, curry(fn))
        inline fun <A : Dynamics<*, A>, B : Dynamics<*, B>, C : Dynamics<*, C>, D : Dynamics<*, D>, E : Dynamics<*, E>, F : Dynamics<*, F>, G : Dynamics<*, G>, H : Dynamics<*, H>, I : Dynamics<*, I>, J : Dynamics<*, J>, K : Dynamics<*, K>> bind(a: Profile<A>, b: Profile<B>, c: Profile<C>, d: Profile<D>, e: Profile<E>, f: Profile<F>, g: Profile<G>, h: Profile<H>, i: Profile<I>, j: Profile<J>, crossinline fn: (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> Profile<K>): Profile<K> = bind(j, bind(a, b, c, d, e, f, g, h, i, fn))
    }
}