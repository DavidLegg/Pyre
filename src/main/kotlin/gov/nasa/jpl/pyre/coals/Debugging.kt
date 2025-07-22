package gov.nasa.jpl.pyre.coals


/**
 * Add a lazily-computed name to a lambda function.
 */
infix fun <A> (() -> A).named(nameFn: () -> String) = object : () -> A by this {
    override fun toString() = nameFn()
}

/**
 * Add a lazily-computed name to a lambda function.
 */
infix fun <A, B> ((A) -> B).named(nameFn: () -> String) = object : (A) -> B by this {
    override fun toString() = nameFn()
}
