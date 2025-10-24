package gov.nasa.jpl.pyre.flame.results

import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Expiring
import gov.nasa.jpl.pyre.spark.resources.Expiry
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.time.Instant

/**
 * The time-history of a resource over an interval from [start] to [end]
 */
class Profile<D : Dynamics<*, D>>(
    val name: String,
    end: Instant,
    segments: Map<Instant, D>
) {
    val window: OpenEndRange<Instant>
    val segments: NavigableMap<Instant, D>
    init {
        require(segments.isNotEmpty()) {
            "Profile segments cannot be empty"
        }
        this.segments = TreeMap(segments.filterKeys { it < end })
        window = this.segments.firstKey()..<end
        require(!window.isEmpty()) {
            "Profile end $end must be later than start $start"
        }
    }

    /**
     * Get the dynamics segment for [time].
     * Starts at [time], and expires when the next segment starts (if there is a next segment).
     */
    fun getSegment(time: Instant): Expiring<D> = Expiring(
        getSegmentData(time),
        Expiry(((segments.higherKey(time) ?: end) - time).toPyreDuration())
    )

    private fun getSegmentData(time: Instant): D {
        require(time >= start && time <= end) {
            "Time $time is outside of profile range $start - $end"
        }
        return segments.floorEntry(time).let {
            it.value.step((time - it.key).toPyreDuration())
        }
    }

    companion object {
        operator fun <V, D : Dynamics<V, D>> Profile<D>.get(time: Instant) = getSegmentData(time).value()
        val Profile<*>.start get() = window.start
        val Profile<*>.end get() = window.endExclusive
    }
}
