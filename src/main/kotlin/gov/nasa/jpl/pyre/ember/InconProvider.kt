package gov.nasa.jpl.pyre.ember

import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface InconProvidingContext {
    fun <T> provide(type: KType): T?

    companion object {
        inline fun <reified T> InconProvidingContext.provide(): T? = provide(typeOf<T>())
    }
}

interface InconProvider : InconProvidingContext {
    fun within(key: String): InconProvider
    fun <R> incremental(block: InconProvidingContext.() -> R): R?

    companion object {
        fun InconProvider.within(keys: Sequence<String>): InconProvider {
            var result = this
            for (key in keys) result = result.within(key)
            return result
        }
        fun InconProvider.within(vararg keys: String): InconProvider = within(keys.asSequence())
    }
}
