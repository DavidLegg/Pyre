package gov.nasa.jpl.pyre.spark.resources.timer

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.spark.BasicSerializers.INT_SERIALIZER
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
