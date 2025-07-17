package gov.nasa.jpl.pyre.ember

interface Conditions : FinconCollector, InconProvider {
    override fun within(key: String) : Conditions

    companion object {
        fun Conditions.within(keys: Sequence<String>): Conditions {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun Conditions.within(vararg keys: String): Conditions = within(keys.asSequence())
    }
}