package gov.nasa.jpl.pyre.kernel

data class Name(val namespace: Name?, val simpleName: String) {
    constructor(simpleName: String) : this(null, simpleName)

    init {
        // All layers above simpleName are checked when constructing those names, and don't need to be re-checked here
        require('.' !in simpleName) {
            "'.' is not an allowed character in simpleName $simpleName"
        }
    }

    override fun toString(): String = (namespace?.let { "$it." } ?: "") + simpleName
}

object NameOperations {
    /**
     * Use [this] as the namespace for [simpleName]
     */
    operator fun Name?.div(simpleName: String) = Name(this, simpleName)

    /**
     * Resolve [partiallyQualifiedName] within [this] namespace.
     */
    operator fun Name?.div(partiallyQualifiedName: Name): Name =
        partiallyQualifiedName.asSequence().fold(this) { ns, n -> ns / n }!!

    /**
     * Return a sequence of all name parts in [namespace], ending with [simpleName].
     * Returned sequence is always finite and non-empty.
     */
    fun Name.asSequence(): Sequence<String> = (namespace?.run { asSequence() } ?: emptySequence()) + simpleName
}
