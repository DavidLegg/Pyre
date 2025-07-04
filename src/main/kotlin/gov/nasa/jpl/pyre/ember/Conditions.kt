package gov.nasa.jpl.pyre.ember

import kotlinx.serialization.json.JsonElement

interface Conditions : FinconCollector, InconProvider {
    override fun withPrefix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonElement) =
                original.report(sequenceOf(key) + keys, value)
            override fun accrue(keys: Sequence<String>, value: JsonElement) =
                original.accrue(sequenceOf(key) + keys, value)
            override fun get(keys: Sequence<String>) =
                original.get(sequenceOf(key) + keys)
        }
    }
    override fun withSuffix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonElement) =
                original.report(keys + key, value)
            override fun accrue(keys: Sequence<String>, value: JsonElement) =
                original.report(keys + key, value)
            override fun get(keys: Sequence<String>) =
                original.get(keys + key)
        }
    }
}