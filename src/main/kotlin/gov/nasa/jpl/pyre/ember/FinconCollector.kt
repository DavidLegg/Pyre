package gov.nasa.jpl.pyre.ember

import kotlinx.serialization.json.JsonElement

interface FinconCollector {
    fun report(keys: Sequence<String>, value: JsonElement)
    fun report(vararg keys: String, value: JsonElement) = report(keys.asSequence(), value)
    fun accrue(keys: Sequence<String>, value: JsonElement)
    fun accrue(vararg keys: String, value: JsonElement) = report(keys.asSequence(), value)

    fun withPrefix(key: String): FinconCollector {
        val original = this
        return object : FinconCollector {
            override fun report(keys: Sequence<String>, value: JsonElement) =
                original.report(sequenceOf(key) + keys, value)
            override fun accrue(keys: Sequence<String>, value: JsonElement) =
                original.accrue(sequenceOf(key) + keys, value)
        }
    }

    fun withSuffix(key: String): FinconCollector {
        val original = this
        return object : FinconCollector {
            override fun report(keys: Sequence<String>, value: JsonElement) =
                original.report(keys + key, value)
            override fun accrue(keys: Sequence<String>, value: JsonElement) =
                original.accrue(keys + key, value)
        }
    }
}