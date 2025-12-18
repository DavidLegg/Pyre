package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable(with = Name.NameSerializer::class)
data class Name(val namespace: Name?, val simpleName: String) {
    constructor(simpleName: String) : this(null, simpleName)

    init {
        // All layers above simpleName are checked when constructing those names, and don't need to be re-checked here
        require(SEPARATOR !in simpleName) {
            "Name separator '$SEPARATOR' is not allowed in simpleName $simpleName"
        }
    }

    override fun toString(): String = (namespace?.let { it.toString() + SEPARATOR } ?: "") + simpleName

    companion object {
        private const val SEPARATOR: Char = '/'
    }

    private class NameSerializer: KSerializer<Name> by String.serializer().alias(InvertibleFunction.of(
        { it.split(SEPARATOR).fold(null) { ns, n -> ns / n }!! },
        { it.toString() }
    ))
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
