package gov.nasa.jpl.pyre.kernel

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KType

// TODO: Use these types to replace incremental reporting and providing,
//   which should collapse FinconCollectingContext into FinconCollector and simiarly for incons.

interface TaskHistoryCollector {
    fun <T> report(value: T, type: KType)
}

interface TaskHistoryProvider {
    fun <T> provide(type: KType): T
}

@Serializable(with = TaskHistory.TaskHistorySerializer::class)
sealed interface TaskHistory {
    fun provider(): TaskHistoryProvider

    class TaskHistorySerializer : KSerializer<TaskHistory> {
        override val descriptor: SerialDescriptor =
            SerialDescriptor(TaskHistory::class.qualifiedName!!, serialDescriptor<List<JsonElement>>())

        override fun serialize(encoder: Encoder, value: TaskHistory) {
            val steps: List<JsonElement> = when (value) {
                is MemoryTaskHistory -> value.steps.map { (v, t) -> Json.encodeToJsonElement(encoder.serializersModule.serializer(t), v) }
                is SerializedTaskHistory -> value.steps
            }
            encoder.serializersModule.serializer<List<JsonElement>>()
                .serialize(encoder, steps)
        }

        override fun deserialize(decoder: Decoder): TaskHistory {
            val steps = decoder.serializersModule.serializer<List<JsonElement>>()
                .deserialize(decoder)
            return SerializedTaskHistory(steps, decoder.serializersModule)
        }

    }

    private class MemoryTaskHistory(
        var steps: MutableList<Pair<*, KType>> = mutableListOf()
    ) : TaskHistory, TaskHistoryCollector {

        override fun <T> report(value: T, type: KType) {
            steps.add(value to type)
        }

        override fun provider() = object : TaskHistoryProvider {
            private var i = 0

            @Suppress("UNCHECKED_CAST")
            override fun <T> provide(type: KType): T = steps[i++] as T
        }
    }

    private class SerializedTaskHistory(
        var steps: List<JsonElement>,
        private var serializersModule: SerializersModule,
    ) : TaskHistory {
        override fun provider() = object : TaskHistoryProvider {
            private var i = 0

            @Suppress("UNCHECKED_CAST")
            override fun <T> provide(type: KType): T =
                Json.decodeFromJsonElement(serializersModule.serializer(type), steps[i++]) as T
        }
    }

    companion object {
        fun construct(): TaskHistory = MemoryTaskHistory()
    }
}

fun TaskHistory(): TaskHistory = TaskHistory.construct()
