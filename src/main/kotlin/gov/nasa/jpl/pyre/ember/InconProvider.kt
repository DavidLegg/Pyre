package gov.nasa.jpl.pyre.ember

import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface InconProvider {
    fun <T> get(keys: Sequence<String>, type: KType): T?

    // TODO: This is an ugly wart on this interface, a hack to make task history encoding/decoding work correctly...
    // Consider any way we might be able to remove this.
    fun <T> decode(value: JsonElement, type: KType): T

    companion object {
        inline fun <reified T> InconProvider.get(keys: Sequence<String>): T? = get(keys, typeOf<T>())
        inline fun <reified T> InconProvider.get(vararg keys: String): T? = get(keys.asSequence())

        fun InconProvider.withPrefix(key: String): InconProvider {
            var original = this
            return object : InconProvider by original {
                override fun <T> get(keys: Sequence<String>, type: KType): T? = original.get(sequenceOf(key) + keys, type)
            }
        }

        fun InconProvider.withSuffix(key: String): InconProvider {
            var original = this
            return object : InconProvider by original {
                override fun <T> get(keys: Sequence<String>, type: KType): T? = original.get(keys + key, type)
            }
        }
    }
}
