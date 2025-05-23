package gov.nasa.jpl.pyre.ember

interface FinconCollector {
    fun report(keys: Sequence<String>, value: JsonValue)
    fun report(vararg keys: String, value: JsonValue) = report(keys.asSequence(), value)
    fun accrue(keys: Sequence<String>, value: JsonValue)
    fun accrue(vararg keys: String, value: JsonValue) = report(keys.asSequence(), value)

    fun withPrefix(key: String): FinconCollector {
        val original = this
        return object : FinconCollector {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                original.report(sequenceOf(key) + keys, value)
            override fun accrue(keys: Sequence<String>, value: JsonValue) =
                original.accrue(sequenceOf(key) + keys, value)
        }
    }

    fun withSuffix(key: String): FinconCollector {
        val original = this
        return object : FinconCollector {
            override fun report(keys: Sequence<String>, value: JsonValue) =
                original.report(keys + key, value)
            override fun accrue(keys: Sequence<String>, value: JsonValue) =
                original.accrue(keys + key, value)
        }
    }
}