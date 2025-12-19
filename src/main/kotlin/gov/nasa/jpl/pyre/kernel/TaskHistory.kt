package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.Serialization.decodeFromJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// TODO: Use these types to replace incremental reporting and providing,
//   which should collapse FinconCollectingContext into FinconCollector and simiarly for incons.

/**
 * Collects each step of a task history as an individual report, for saving to [MutableSnapshot].
 * Implementations of this type are stateful and are mutated by [report].
 */
interface TaskHistoryCollector {
    fun <T> report(value: T, type: KType)

    companion object {
        inline fun <reified T> TaskHistoryCollector.report(value: T) = report(value, typeOf<T>())
    }
}

/**
 * Provides reports indicating what steps a task has taken, usually from [MutableSnapshot].
 * Implementations of this type are stateful and are mutated by [provide], like an Iterator would be.
 */
interface TaskHistoryProvider {
    fun hasNext(): Boolean
    fun <T> provide(type: KType): T?

    companion object {
        inline fun <reified T> TaskHistoryProvider.provide(): T? = provide(typeOf<T>())
    }
}

/**
 * A [TaskHistory] to which new steps may be reported.
 * It is assumed that new steps will not be reported while a [provider] is active.
 * Doing so may cause undefined behavior.
 */
sealed interface MutableTaskHistory : TaskHistory, TaskHistoryCollector

/**
 * A collection of reports from a task indicating what steps that task has taken.
 * Reports may be read using [provider] to construct an Iterator-like [TaskHistoryProvider],
 * which can provide each report sequentially.
 */
@Serializable(with = TaskHistory.TaskHistorySerializer::class)
sealed interface TaskHistory {
    fun provider(): TaskHistoryProvider

    class TaskHistorySerializer : KSerializer<TaskHistory> {
        override val descriptor: SerialDescriptor =
            SerialDescriptor(TaskHistory::class.qualifiedName!!, serialDescriptor<List<JsonElement>>())

        override fun serialize(encoder: Encoder, value: TaskHistory) {
            val steps: List<JsonElement> = when (value) {
                is MemoryTaskHistory -> value.steps.map { (v, t) ->
                    (encoder as JsonEncoder).json.encodeToJsonElement(
                        encoder.serializersModule.serializer(t), v)
                }
                is SerializedTaskHistory -> value.steps
            }
            encoder.serializersModule.serializer<List<JsonElement>>()
                .serialize(encoder, steps)
        }

        override fun deserialize(decoder: Decoder): TaskHistory {
            val steps = decoder.serializersModule.serializer<List<JsonElement>>()
                .deserialize(decoder)
            return SerializedTaskHistory(steps, (decoder as JsonDecoder).json)
        }

    }

    private class MemoryTaskHistory(
        var steps: MutableList<Pair<*, KType>> = mutableListOf()
    ) : MutableTaskHistory {
        override fun <T> report(value: T, type: KType) {
            steps.add(value to type)
        }

        override fun provider() = object : TaskHistoryProvider {
            private var i = 0
            override fun hasNext(): Boolean = i < steps.size

            @Suppress("UNCHECKED_CAST")
            override fun <T> provide(type: KType): T? = steps.getOrNull(i++)?.first as T?
        }
    }

    private class SerializedTaskHistory(
        var steps: List<JsonElement>,
        private var json: Json,
    ) : TaskHistory {
        override fun provider() = object : TaskHistoryProvider {
            private var i = 0
            override fun hasNext(): Boolean = i <= steps.size

            @Suppress("UNCHECKED_CAST")
            override fun <T> provide(type: KType): T? =
                steps.getOrNull(i++)?.let {
                    json.decodeFromJsonElement(type, it) as T
                }
        }
    }

    companion object {
        internal fun construct(): MutableTaskHistory = MemoryTaskHistory()
    }
}

fun MutableTaskHistory(): MutableTaskHistory = TaskHistory.construct()
