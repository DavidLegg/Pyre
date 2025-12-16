package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Expiring
import gov.nasa.jpl.pyre.foundation.resources.Expiry
import gov.nasa.jpl.pyre.kernel.Name
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.time.Instant

/**
 * The time-history of a resource over an interval from [start] to [end]
 */
class Profile<D : Dynamics<*, D>>(
    val name: Name,
    end: Instant,
    val segments: NavigableMap<Instant, D>
) {
    val window: ClosedRange<Instant>
    init {
        require(segments.isNotEmpty()) {
            "Profile segments cannot be empty"
        }
        window = this.segments.firstKey()..end
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
        require(time in window) {
            "Time $time is outside of profile range $start - $end"
        }
        return segments.floorEntry(time).let {
            it.value.step((time - it.key).toPyreDuration())
        }
    }

    companion object {
        operator fun <V, D : Dynamics<V, D>> Profile<D>.get(time: Instant) = getSegmentData(time).value()
        val Profile<*>.start get() = window.start
        val Profile<*>.end get() = window.endInclusive
    }
}
