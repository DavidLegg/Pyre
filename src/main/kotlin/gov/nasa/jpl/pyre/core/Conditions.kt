package org.example.gov.nasa.jpl.pyre.core

interface Conditions : FinconCollector, InconProvider {
    override fun withPrefix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                (original as FinconCollector).withPrefix(key).report(keys, value)

            override fun get(keys: Sequence<String>) =
                (original as InconProvider).withSuffix(key).get(keys)
        }
    }
    override fun withSuffix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                (original as FinconCollector).withSuffix(key).report(keys, value)

            override fun get(keys: Sequence<String>) =
                (original as InconProvider).withSuffix(key).get(keys)
        }
    }
}