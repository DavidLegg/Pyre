package gov.nasa.jpl.pyre.kernel

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Read-only view of [Conditions], a snapshot of a simulation we can use to restore a functionally identical simulation.
 */
interface InconProvider {
    fun within(key: String): InconProvider
    fun <T> provide(type: KType): T?
    fun inconExists(): Boolean

    companion object {
        fun InconProvider.within(keys: Sequence<String>): InconProvider {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun InconProvider.within(vararg keys: String): InconProvider = within(keys.asSequence())
        inline fun <reified T> InconProvider.provide(vararg keys: String): T? = within(*keys).provide(typeOf<T>())
        inline fun <reified T> InconProvider.provide(): T? = provide(typeOf<T>())
    }
}
