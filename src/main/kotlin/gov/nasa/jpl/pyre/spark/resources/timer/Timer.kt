package gov.nasa.jpl.pyre.spark.resources.timer

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.INT_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.Dynamics

data class Timer(val time: Duration, val rate: Int) : Dynamics<Duration, Timer> {
    override fun value(): Duration = time
    override fun step(t: Duration): Timer = Timer(time + t * rate, rate)

    companion object {
        fun serializer(): Serializer<Timer> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "time" to Duration.serializer().serialize(it.time),
                    "rate" to INT_SERIALIZER.serialize(it.rate)
                ))
            },
            {
                Timer(
                    Duration.serializer().deserialize(requireNotNull((it as JsonMap).values["time"])),
                    INT_SERIALIZER.deserialize(requireNotNull(it.values["rate"])),
                )
            }
        ))
    }
}

operator fun Timer.plus(other: Timer): Timer = Timer(this.time + other.time, this.rate + other.rate)
operator fun Timer.plus(other: Duration): Timer = Timer(this.time + other, this.rate)
operator fun Duration.plus(other: Timer): Timer = Timer(this + other.time, other.rate)
operator fun Timer.unaryPlus(): Timer = this
operator fun Timer.minus(other: Timer): Timer = Timer(this.time - other.time, this.rate - other.rate)
operator fun Timer.minus(other: Duration): Timer = Timer(this.time - other, this.rate)
operator fun Duration.minus(other: Timer): Timer = Timer(this - other.time, other.rate)
operator fun Timer.unaryMinus(): Timer = Timer(-time, -rate)
