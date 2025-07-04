package gov.nasa.jpl.pyre.coals

interface InvertibleFunction<A, B> : (A) -> B {
    val inverse : InvertibleFunction<B, A>

    companion object {
        fun <A, B> of(forward: (A) -> B, reverse: (B) -> A): InvertibleFunction<A, B> =
            object : InvertibleFunction<A, B> {
                override val inverse = of(reverse, forward)
                override fun invoke(p1: A) = forward(p1)
            }

        fun <A> identity(): InvertibleFunction<A, A> = of({ it }, { it })
    }
}

infix fun <A, B, C> InvertibleFunction<A, B>.andThen(g: InvertibleFunction<B, C>): InvertibleFunction<A, C> =
    InvertibleFunction.of(
        (this as ((A) -> B)) andThen g,
        (g.inverse as ((C) -> B)) andThen this.inverse)
infix fun <A, B, C> InvertibleFunction<B, C>.compose(g: InvertibleFunction<A, B>): InvertibleFunction<A, C> =
    InvertibleFunction.of(
        (this as ((B) -> C)) compose g,
        (g.inverse as ((B) -> A)) compose this.inverse)
