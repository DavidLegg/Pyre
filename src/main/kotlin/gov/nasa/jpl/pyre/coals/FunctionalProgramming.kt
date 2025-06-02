package gov.nasa.jpl.pyre.coals

fun <A> identity(): (A) -> A = {it}
infix fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C = { g(this(it)) }
infix fun <A, B, C> ((B) -> C).compose(g: (A) -> B): (A) -> C = { this(g(it)) }
