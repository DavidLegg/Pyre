package gov.nasa.jpl.pyre.coals

interface InvertibleFunction<A, B> : (A) -> B {
    fun inverse() : InvertibleFunction<B, A>

    companion object {
        fun <A, B> of(forward: (A) -> B, reverse: (B) -> A): InvertibleFunction<A, B> =
            object : InvertibleFunction<A, B> {
                override fun inverse() = of(reverse, forward)
                override fun invoke(p1: A) = forward(p1)
            }

        infix fun <A, B> ((A) -> B).withInverse(inverse: (B) -> A): InvertibleFunction<A, B> = of(this, inverse)
    }
}