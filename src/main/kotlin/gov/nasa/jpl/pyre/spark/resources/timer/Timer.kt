package gov.nasa.jpl.pyre.spark.resources.timer

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.*
import gov.nasa.jpl.pyre.spark.resources.Dynamics

data class Timer(val time: Duration, val rate: Int) : Dynamics<Duration, Timer> {
    override fun value(): Duration = time
    override fun step(t: Duration): Timer = Timer(time + t * rate, rate)
}
