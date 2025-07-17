package gov.nasa.jpl.pyre.ember

import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface FinconCollectingContext {
    fun <T> report(value: T, type: KType)

    companion object {
        inline fun <reified T> FinconCollectingContext.report(value: T) = report(value, typeOf<T>())
    }
}

interface FinconCollector : FinconCollectingContext {
    fun within(key: String): FinconCollector
    fun incremental(block: FinconCollectingContext.() -> Unit)

    companion object {
        fun FinconCollector.within(keys: Sequence<String>): FinconCollector {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun FinconCollector.within(vararg keys: String): FinconCollector = within(keys.asSequence())
    }
}
