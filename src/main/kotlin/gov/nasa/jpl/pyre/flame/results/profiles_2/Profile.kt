package gov.nasa.jpl.pyre.flame.results.profiles_2

import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import java.util.NavigableMap
import kotlin.time.Instant

class Profile<D : Dynamics<*, D>>(
    /**
     * Name of this profile, mostly for debugging purposes
     */
    val name: String,
    /**
     * Initial segment, extending backwards forever.
     * Assumed to be constant, i.e., assumed that `initialSegment.step(t) == initialSegment` for any t.
     */
    val initialSegment: D,
    /**
     * All non-initial segments, if any exist.
     */
    val segments: NavigableMap<Instant, D>
) {
    operator fun get(time: Instant): D = segments.floorEntry(time)?.let {
        it.value.step((time - it.key).toPyreDuration())
    } ?: initialSegment
}
