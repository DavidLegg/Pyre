package gov.nasa.jpl.pyre.kernel.tasks

import gov.nasa.jpl.pyre.kernel.tasks.PureTask.TaskHistoryStep
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

/*
 * Why is this file so complicated?
 *
 * Basically the same reason that Checkpoint is so complicated.
 * When copying task history in memory, we don't want to serialize, not even to JsonElements.
 * To support this, I have MemoryTaskHistory.
 *
 * If I do serialize, I don't know what type each read value in the history is until I restore the task.
 * To defer deserializing those values until I restore the task, I have JsonTaskHistory.
 *
 * To hide all of this ugliness, I have the (Mutable)TaskHistory interfaces.
 * The Task just asks for the appropriate history, oblivious to how and when (de)serialization happens.
 */

/**
 * Collects each step of a task history as an individual report, for saving to [gov.nasa.jpl.pyre.kernel.KernelCheckpoint].
 * Implementations of this type are stateful and are mutated by [report].
 */
interface TaskHistoryCollector {
    fun <T> report(value: T, type: KType)

    companion object {
        inline fun <reified T> TaskHistoryCollector.report(value: T) = report(value, typeOf<T>())
    }
}

/**
 * Provides reports indicating what steps a task has taken, usually from [gov.nasa.jpl.pyre.kernel.KernelCheckpoint].
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

    private data class MemoryTaskHistory(
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

    private data class SerializedTaskHistory(
        var steps: List<JsonElement>,
        private var json: Json,
    ) : TaskHistory {
        override fun provider() = object : TaskHistoryProvider {
            private var i = 0
            override fun hasNext(): Boolean = i < steps.size

            @Suppress("UNCHECKED_CAST")
            override fun <T> provide(type: KType): T? =
                steps.getOrNull(i++)?.let {
                    json.decodeFromJsonElement(type, it) as T
                }
        }
    }

    companion object {
        internal fun construct(): MutableTaskHistory = MemoryTaskHistory()

        fun TaskHistory.valueEquals(other: TaskHistory, valueComparator: (Any?, Any?) -> Boolean): Boolean {
            when (this) {
                is MemoryTaskHistory -> {
                    // Use the type information in this history object to convert steps in other
                    val otherProvider = other.provider()
                    for ((thisStep, stepType) in steps) {
                        if (!otherProvider.hasNext()) return false
                        val otherStep = otherProvider.provide<Any>(stepType)
                        if (!valueComparator(thisStep, otherStep)) return false
                    }
                    // Then make sure that other is also exhausted
                    return !otherProvider.hasNext()
                }
                is SerializedTaskHistory -> when (other) {
                    is MemoryTaskHistory -> return other.valueEquals(this, valueComparator)
                    is SerializedTaskHistory -> {
                        // Compare as JSON objects instead
                        return steps.size == other.steps.size
                                && steps.zip(other.steps).all { (x, y) -> valueComparator(x, y) }
                    }
                }
            }
        }
    }
}

fun MutableTaskHistory(): MutableTaskHistory = TaskHistory.construct()
