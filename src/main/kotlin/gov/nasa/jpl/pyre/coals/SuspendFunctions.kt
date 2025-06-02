package gov.nasa.jpl.pyre.coals

fun <A, R> suspend(lambda: suspend (A) -> R) = lambda
fun <A, B, R> suspend(lambda: suspend (A, B) -> R) = lambda
fun <A, B, C, R> suspend(lambda: suspend (A, B, C) -> R) = lambda
fun <A, B, C, D, R> suspend(lambda: suspend (A, B, C, D) -> R) = lambda
fun <A, B, C, D, E, R> suspend(lambda: suspend (A, B, C, D, E) -> R) = lambda

fun <R> nosuspend(lambda: () -> R) = lambda
fun <A, R> nosuspend(lambda: (A) -> R) = lambda
fun <A, B, R> nosuspend(lambda: (A, B) -> R) = lambda
fun <A, B, C, R> nosuspend(lambda: (A, B, C) -> R) = lambda
fun <A, B, C, D, R> nosuspend(lambda: (A, B, C, D) -> R) = lambda
fun <A, B, C, D, E, R> nosuspend(lambda: (A, B, C, D, E) -> R) = lambda
