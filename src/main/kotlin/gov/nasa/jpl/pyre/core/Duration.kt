package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.core.JsonValue.*

data class Duration(val ticks: Long) : Comparable<Duration> {
    override fun compareTo(other: Duration): Int {
        return ticks.compareTo(other.ticks)
    }

    operator fun plus(other: Duration): Duration {
        return Duration(ticks + other.ticks)
    }

    operator fun minus(other: Duration): Duration {
        return Duration(ticks - other.ticks)
    }

    companion object {
        val ZERO: Duration = Duration(0)

        // TODO: Consider a more human-readable serialization...
        fun serializer(): Serializer<Duration> = object : Serializer<Duration> {
            override fun serialize(obj: Duration): JsonValue {
                return JsonInt(obj.ticks)
            }

            override fun deserialize(jsonValue: JsonValue): Result<Duration> = runCatching {
                Duration((jsonValue as JsonInt).value)
            }
        }
    }
}
