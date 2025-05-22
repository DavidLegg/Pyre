package gov.nasa.jpl.pyre.ember

fun interface InconProvider {
    fun get(keys: Sequence<String>): JsonValue?
    fun get(vararg keys: String): JsonValue? = get(keys.asSequence())

    fun withPrefix(key: String): InconProvider {
        return InconProvider { keys -> get(sequenceOf(key) + keys) }
    }

    fun withSuffix(key: String): InconProvider {
        return InconProvider { keys -> get(keys + key) }
    }
}