package gov.nasa.jpl.pyre.utilities

fun <A> identity(): (A) -> A = { a: A -> a } named { "identity" }
inline infix fun <A, B, C> ((A) -> B).andThen(crossinline g: (B) -> C): (A) -> C = { g(this(it)) }
inline infix fun <A, B, C> ((B) -> C).compose(crossinline g: (A) -> B): (A) -> C = { this(g(it)) }

// Special composition operators to treat Pairs as a single arg to a 2-arg function.
// This is especially useful for report handlers, which have a value and a type.

inline infix fun <A, B, C, D> ((A, B) -> C).andThen(crossinline g: (C) -> D): (A, B) -> D = { a, b -> g(this(a, b)) }
inline infix fun <A, B, C, D> ((C) -> D).compose(crossinline g: (A, B) -> C): (A, B) -> D = { a, b -> this(g(a, b)) }

inline infix fun <A, B, C, D> ((A) -> Pair<B, C>).andThen(crossinline g: (B, C) -> D): (A) -> D = { val (b, c) = this(it); g(b, c) }
inline infix fun <A, B, C, D> ((B, C) -> D).compose(crossinline g: (A) -> Pair<B, C>): (A) -> D = { val (b, c) = g(it); this(b, c) }

inline infix fun <A, B, C, D, E> ((A, B) -> Pair<C, D>).andThen(crossinline g: (C, D) -> E): (A, B) -> E = { a, b -> val (c, d) = this(a, b); g(c, d) }
inline infix fun <A, B, C, D, E> ((C, D) -> E).compose(crossinline g: (A, B) -> Pair<C, D>): (A, B) -> E = { a, b -> val (c, d) = g(a, b); this(c, d) }

inline fun <A, B, C> curry(crossinline fn: (A, B) -> C):
            (A) -> (B) -> C =
    { a -> { b -> fn(a, b) } }
inline fun <A, B, C, D> curry(crossinline fn: (A, B, C) -> D):
            (A) -> (B) -> (C) -> D =
    { a -> { b -> {c -> fn(a, b, c) } } }
inline fun <A, B, C, D, E> curry(crossinline fn: (A, B, C, D) -> E):
            (A) -> (B) -> (C) -> (D) -> E =
    { a -> { b -> {c -> { d -> fn(a, b, c, d) } } } }
inline fun <A, B, C, D, E, F> curry(crossinline fn: (A, B, C, D, E) -> F):
            (A) -> (B) -> (C) -> (D) -> (E) -> F =
    { a -> { b -> {c -> { d -> { e -> fn(a, b, c, d, e) } } } } }
inline fun <A, B, C, D, E, F, G> curry(crossinline fn: (A, B, C, D, E, F) -> G):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G =
    { a -> { b -> {c -> { d -> { e -> { f -> fn(a, b, c, d, e, f) } } } } } }
inline fun <A, B, C, D, E, F, G, H> curry(crossinline fn: (A, B, C, D, E, F, G) -> H):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> fn(a, b, c, d, e, f, g) } } } } } } }
inline fun <A, B, C, D, E, F, G, H, I> curry(crossinline fn: (A, B, C, D, E, F, G, H) -> I):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> fn(a, b, c, d, e, f, g, h) } } } } } } } }
inline fun <A, B, C, D, E, F, G, H, I, J> curry(crossinline fn: (A, B, C, D, E, F, G, H, I) -> J):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> { i -> fn(a, b, c, d, e, f, g, h, i) } } } } } } } } }
inline fun <A, B, C, D, E, F, G, H, I, J, K> curry(crossinline fn: (A, B, C, D, E, F, G, H, I, J) -> K):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> { i -> { j -> fn(a, b, c, d, e, f, g, h, i, j) } } } } } } } } } }

// Partial application overloads, inspired by an answer on Stack Overflow:
// https://stackoverflow.com/questions/52711621/does-kotlin-support-partial-application

operator fun <A, B, C> ((A, B) -> C).invoke(a: A): (B) -> C = { b -> this(a, b) }
operator fun <A, B, C, D> ((A, B, C) -> D).invoke(a: A): (B, C) -> D = { b, c -> this(a, b, c) }
operator fun <A, B, C, D, E> ((A, B, C, D) -> E).invoke(a: A): (B, C, D) -> E = { b, c, d -> this(a, b, c, d) }
operator fun <A, B, C, D, E, F> ((A, B, C, D, E) -> F).invoke(a: A): (B, C, D, E) -> F = { b, c, d, e -> this(a, b, c, d, e) }

operator fun <A, B, C, D> ((A, B, C) -> D).invoke(a: A, b: B): (C) -> D = { c -> this(a, b, c) }
operator fun <A, B, C, D, E> ((A, B, C, D) -> E).invoke(a: A, b: B): (C, D) -> E = { c, d -> this(a, b, c, d) }
operator fun <A, B, C, D, E, F> ((A, B, C, D, E) -> F).invoke(a: A, b: B): (C, D, E) -> F = { c, d, e -> this(a, b, c, d, e) }

operator fun <A, B, C, D, E> ((A, B, C, D) -> E).invoke(a: A, b: B, c: C): (D) -> E = { d -> this(a, b, c, d) }
operator fun <A, B, C, D, E, F> ((A, B, C, D, E) -> F).invoke(a: A, b: B, c: C): (D, E) -> F = { d, e -> this(a, b, c, d, e) }

operator fun <A, B, C, D, E, F> ((A, B, C, D, E) -> F).invoke(a: A, b: B, c: C, d: D): (E) -> F = { e -> this(a, b, c, d, e) }

