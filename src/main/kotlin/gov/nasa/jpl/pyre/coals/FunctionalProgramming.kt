package gov.nasa.jpl.pyre.coals

fun <A> identity(): (A) -> A = {it}
infix fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C = { g(this(it)) }
infix fun <A, B, C> ((B) -> C).compose(g: (A) -> B): (A) -> C = { this(g(it)) }

fun <A, B, C> curry(fn: (A, B) -> C):
            (A) -> (B) -> C =
    { a -> { b -> fn(a, b) } }
fun <A, B, C, D> curry(fn: (A, B, C) -> D):
            (A) -> (B) -> (C) -> D =
    { a -> { b -> {c -> fn(a, b, c) } } }
fun <A, B, C, D, E> curry(fn: (A, B, C, D) -> E):
            (A) -> (B) -> (C) -> (D) -> E =
    { a -> { b -> {c -> { d -> fn(a, b, c, d) } } } }
fun <A, B, C, D, E, F> curry(fn: (A, B, C, D, E) -> F):
            (A) -> (B) -> (C) -> (D) -> (E) -> F =
    { a -> { b -> {c -> { d -> { e -> fn(a, b, c, d, e) } } } } }
fun <A, B, C, D, E, F, G> curry(fn: (A, B, C, D, E, F) -> G):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> G =
    { a -> { b -> {c -> { d -> { e -> { f -> fn(a, b, c, d, e, f) } } } } } }
fun <A, B, C, D, E, F, G, H> curry(fn: (A, B, C, D, E, F, G) -> H):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> H =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> fn(a, b, c, d, e, f, g) } } } } } } }
fun <A, B, C, D, E, F, G, H, I> curry(fn: (A, B, C, D, E, F, G, H) -> I):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> I =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> fn(a, b, c, d, e, f, g, h) } } } } } } } }
fun <A, B, C, D, E, F, G, H, I, J> curry(fn: (A, B, C, D, E, F, G, H, I) -> J):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> J =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> { i -> fn(a, b, c, d, e, f, g, h, i) } } } } } } } } }
fun <A, B, C, D, E, F, G, H, I, J, K> curry(fn: (A, B, C, D, E, F, G, H, I, J) -> K):
            (A) -> (B) -> (C) -> (D) -> (E) -> (F) -> (G) -> (H) -> (I) -> (J) -> K =
    { a -> { b -> {c -> { d -> { e -> { f -> { g -> { h -> { i -> { j -> fn(a, b, c, d, e, f, g, h, i, j) } } } } } } } } } }
