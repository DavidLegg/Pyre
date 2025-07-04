package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MICROSECOND
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.microseconds

@Serializable(with = DurationSerializer::class)
data class Duration(val ticks: Long) : Comparable<Duration> {
    override fun compareTo(other: Duration): Int {
        return ticks.compareTo(other.ticks)
    }

    override fun toString(): String {
        // TODO: Consider an optional "days" field in this format
        if (this < ZERO) return "-" + (-this).toString()
        val hours = this / HOUR
        val minutes = (this % HOUR) / MINUTE
        val seconds = (this % MINUTE) / SECOND
        val microseconds = (this % SECOND) / MICROSECOND
        return String.format("%02d:%02d:%02d.%06d", hours, minutes, seconds, microseconds)
    }

    companion object {
        val ZERO: Duration = Duration(0)
        val EPSILON: Duration = Duration(1)
        val MICROSECOND: Duration = EPSILON
        val MILLISECOND: Duration = 1000 * MICROSECOND
        val SECOND: Duration = 1000 * MILLISECOND
        val MINUTE: Duration = 60 * SECOND
        val HOUR: Duration = 60 * MINUTE
        val DAY: Duration = 24 * HOUR
        val WEEK: Duration = 7 * DAY
        // According to https://www.jpl.nasa.gov/_edu/pdfs/leapday_answers.pdf
        val ASTRONOMICAL_YEAR: Duration = 365 * DAY + 5 * HOUR + 48 * MINUTE + 46 * SECOND

        val MIN_VALUE = Duration(Long.MIN_VALUE)
        val MAX_VALUE = Duration(Long.MAX_VALUE)
    }
}

class DurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Duration::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Duration {
        var s = decoder.decodeString()
        var signum = 1
        if (s[0] == '-') {
            signum = -1
            s = s.substring(1)
        }

        val parts = s.split(':')
        require(parts.size == 3)
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val secondsParts = parts[2].split('.')
        require(secondsParts.size == 2)
        val seconds = secondsParts[0].toInt()
        require(secondsParts[1].length == 6)
        val microseconds = secondsParts[1].toInt()

        return signum * (hours * HOUR + minutes * MINUTE + seconds * SECOND + microseconds * MICROSECOND)
    }
}

// TODO: These should move to spark layer
// Operator overloads:
operator fun Duration.plus(other: Duration): Duration = Duration(ticks + other.ticks)
operator fun Duration.minus(other: Duration): Duration = Duration(ticks - other.ticks)
operator fun Duration.times(scale: Int) = Duration(ticks * scale)
operator fun Duration.times(scale: Long) = Duration(ticks * scale)
operator fun Duration.unaryPlus(): Duration = Duration(+ticks)
operator fun Duration.unaryMinus(): Duration = Duration(-ticks)
operator fun Duration.div(scale: Long) = Duration(ticks / scale)
operator fun Duration.div(divisor: Duration) = ticks / divisor.ticks
infix fun Duration.ratioOver(other: Duration) = ticks.toDouble() / other.ticks
operator fun Duration.rem(other: Duration): Duration = Duration(ticks % other.ticks)
operator fun Long.times(duration: Duration) = Duration(duration.ticks * this)
operator fun Int.times(duration: Duration) = Duration(duration.ticks * this)
infix fun Duration.roundTimes(scale: Double) = Duration((ticks * scale).roundToLong())
infix fun Double.roundTimes(scale: Duration) = Duration((scale.ticks * this).roundToLong())
infix fun Duration.floorTimes(scale: Double) = Duration(floor(ticks * scale).roundToLong())
infix fun Double.floorTimes(scale: Duration) = Duration(floor(scale.ticks * this).roundToLong())
infix fun Duration.ceilTimes(scale: Double) = Duration(ceil(ticks * scale).roundToLong())
infix fun Double.ceilTimes(scale: Duration) = Duration(ceil(scale.ticks * this).roundToLong())

fun abs(duration: Duration): Duration = Duration(abs(duration.ticks))

fun Duration.toKotlinDuration(): kotlin.time.Duration = (this / MICROSECOND).microseconds
fun Duration.toJavaDuration(): java.time.Duration = java.time.Duration.of(this / MICROSECOND, ChronoUnit.MICROS)
fun kotlin.time.Duration.toPyreDuration(): Duration = this.inWholeMicroseconds * MICROSECOND
fun java.time.Duration.toPyreDuration(): Duration = this.dividedBy(java.time.Duration.of(1, ChronoUnit.MICROS)) * MICROSECOND
