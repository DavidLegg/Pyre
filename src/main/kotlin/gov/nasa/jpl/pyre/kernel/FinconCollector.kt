package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Writeable view of [Conditions], used to capture the state of a simulation.
 */
interface FinconCollector {
    fun within(key: String): FinconCollector
    fun <T> report(value: T, type: KType)

    companion object {
        fun FinconCollector.within(keys: Sequence<String>): FinconCollector {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun FinconCollector.within(vararg keys: String): FinconCollector = within(keys.asSequence())
        inline fun <reified T> FinconCollector.report(value: T) = report(value, typeOf<T>())
    }
}
