package gov.nasa.jpl.pyre.coals

fun <A> identity(): (A) -> A = {it}
infix fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C = { g(this(it)) }
infix fun <A, B, C> ((B) -> C).compose(g: (A) -> B): (A) -> C = { this(g(it)) }

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
