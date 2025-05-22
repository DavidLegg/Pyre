package gov.nasa.jpl.pyre.ember

fun interface FinconCollector {
    fun report(keys: Sequence<String>, value: JsonValue)
    fun report(vararg keys: String, value: JsonValue) = report(keys.asSequence(), value)

    fun withPrefix(key: String): FinconCollector {
        return FinconCollector { keys, value -> report(sequenceOf(key) + keys, value) }
    }

    fun withSuffix(key: String): FinconCollector {
        return FinconCollector { keys, value -> report(keys + key, value) }
    }
}