package gov.nasa.jpl.pyre.utilities

fun <A> identity(): (A) -> A = {it}
infix fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C = { g(this(it)) }
infix fun <A, B, C> ((B) -> C).compose(g: (A) -> B): (A) -> C = g andThen this

// Special composition operators to treat Pairs as a single arg to a 2-arg function.
// This is especially useful for report handlers, which have a value and a type.

infix fun <A, B, C, D> ((A, B) -> C).andThen(g: (C) -> D): (A, B) -> D = { a, b -> g(this(a, b)) }
infix fun <A, B, C, D> ((C) -> D).compose(g: (A, B) -> C): (A, B) -> D = g andThen this

infix fun <A, B, C, D> ((A) -> Pair<B, C>).andThen(g: (B, C) -> D): (A) -> D = { this(it).run { g(first, second) } }
infix fun <A, B, C, D> ((B, C) -> D).compose(g: (A) -> Pair<B, C>): (A) -> D = g andThen this

infix fun <A, B, C, D, E> ((A, B) -> Pair<C, D>).andThen(g: (C, D) -> E): (A, B) -> E = { a, b -> this(a, b).run { g(first, second) } }
infix fun <A, B, C, D, E> ((C, D) -> E).compose(g: (A, B) -> Pair<C, D>): (A, B) -> E = g andThen this

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
