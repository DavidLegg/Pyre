package gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.time

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.toJavaDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.toJavaDuration


class Time(private val instant: Instant) : Comparable<Time> {
    override fun compareTo(other: Time): Int = instant.compareTo(other.instant)

    operator fun minus(duration: java.time.Duration) = Time(instant - duration)
    operator fun plus(duration: java.time.Duration) = Time(instant + duration)
    operator fun minus(duration: kotlin.time.Duration) = this - duration.toJavaDuration()
    operator fun plus(duration: kotlin.time.Duration) = this + duration.toJavaDuration()
    operator fun minus(duration: Duration) = this - duration.toJavaDuration()
    operator fun plus(duration: Duration) = this + duration.toJavaDuration()
    operator fun minus(other: Time) = java.time.Duration.between(other.instant, this.instant).toPyreDuration()

    fun utcTimestamp(): String {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).format(UTC_FORMAT)
    }

    fun lmstTimestamp(): String {
        val offset = java.time.Duration.between(Mars_Time_Origin.instant, instant);
        val fractionalSeconds = offset.getSeconds()/Mars_Time_Scale;
        val nanoPart = (offset.getNano()/Mars_Time_Scale);

        var totalSeconds = floor(fractionalSeconds).toLong()
        var nanos = (fractionalSeconds - totalSeconds)*1_000_000_000 + nanoPart
        if (nanos > 1_000_000_000) {
            totalSeconds += 1
            nanos -= 1_000_000_000
        }

        val sols = (totalSeconds / (60*60*24))
        var remainder = totalSeconds % (60*60*24)
        val hours = (remainder / (60*60))
        remainder = remainder % (60*60)
        val minutes = (remainder / (60))
        remainder = remainder % (60)
        val seconds = remainder
        val microseconds = (nanos / 1000).roundToLong()

        return String.format("Sol-%04dM%02d:%02d:%02d.%06d", sols, hours, minutes, seconds, microseconds);
    }

    companion object {
        val UTC_FORMAT: DateTimeFormatter =
            DateTimeFormatterBuilder().appendPattern("uuuu-DDD'T'HH:mm:ss")
                .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
                .toFormatter()

        val Mars_Time_Origin: Time = Time.fromUTC("2018-330T05:10:50.3356")
        val Mars_Time_Scale: Double = 1.02749125

        fun fromUTC(utcTimestamp: String): Time {
            return Time(LocalDateTime.parse(utcTimestamp, UTC_FORMAT).atZone(ZoneOffset.UTC).toInstant())
        }
    }
}
