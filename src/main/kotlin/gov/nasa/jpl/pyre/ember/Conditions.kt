package gov.nasa.jpl.pyre.ember

interface Conditions : FinconCollector, InconProvider {
    override fun withPrefix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                original.report(sequenceOf(key) + keys, value)
            override fun get(keys: Sequence<String>) =
                original.get(sequenceOf(key) + keys)
        }
    }
    override fun withSuffix(key: String): Conditions {
        val original = this
        return object : Conditions {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                original.report(keys + key, value)
            override fun get(keys: Sequence<String>) =
                original.get(keys + key)
        }
    }
}