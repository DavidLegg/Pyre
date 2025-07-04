package gov.nasa.jpl.pyre.ember

import kotlinx.serialization.json.JsonElement

fun interface InconProvider {
    fun get(keys: Sequence<String>): JsonElement?
    fun get(vararg keys: String): JsonElement? = get(keys.asSequence())

    fun withPrefix(key: String): InconProvider {
        return InconProvider { keys -> get(sequenceOf(key) + keys) }
    }

    fun withSuffix(key: String): InconProvider {
        return InconProvider { keys -> get(keys + key) }
    }
}