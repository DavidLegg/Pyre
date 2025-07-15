package gov.nasa.jpl.pyre.ember

import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface FinconCollector {
    fun <T> report(keys: Sequence<String>, value: T, type: KType)
    fun <T> accrue(keys: Sequence<String>, value: T, type: KType)

    // TODO: This is an ugly wart on this interface, a hack to make task history encoding/decoding work correctly...
    //  Consider any way we might be able to remove this.
    fun <T> encode(value: T, type: KType): JsonElement

    companion object {
        inline fun <reified T> FinconCollector.report(keys: Sequence<String>, value: T) = report(keys, value, typeOf<T>())
        inline fun <reified T> FinconCollector.report(vararg keys: String, value: T) = report(keys.asSequence(), value)
        inline fun <reified T> FinconCollector.accrue(keys: Sequence<String>, value: T) = accrue(keys, value, typeOf<T>())
        inline fun <reified T> FinconCollector.accrue(vararg keys: String, value: T) = accrue(keys.asSequence(), value)

        fun FinconCollector.withPrefix(key: String): FinconCollector {
            val original = this
            return object : FinconCollector by original {
                override fun <T> report(keys: Sequence<String>, value: T, type: KType) =
                    original.report(sequenceOf(key) + keys, value, type)
                override fun <T> accrue(keys: Sequence<String>, value: T, type: KType) =
                    original.accrue(sequenceOf(key) + keys, value, type)
            }
        }

        fun FinconCollector.withSuffix(key: String): FinconCollector {
            val original = this
            return object : FinconCollector by original {
                override fun <T> report(keys: Sequence<String>, value: T, type: KType) =
                    original.report(keys + key, value, type)
                override fun <T> accrue(keys: Sequence<String>, value: T, type: KType) =
                    original.accrue(keys + key, value, type)
            }
        }
    }
}