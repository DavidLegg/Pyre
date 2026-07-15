package gov.nasa.jpl.parakeet.general.results

import gov.nasa.jpl.parakeet.foundation.resources.Expiring
import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.time.Instant

/**
 * The profile of dynamics for a single resource from a simulation.
 *
 * This profile models some resource over [window].
 * It can be seen as an iterable of (time, dynamics) pairs if the entire profile needs to be examined,
 * or [get] can efficiently retrieve the dynamics at a specific time.
 */
interface Profile<D> : Iterable<Pair<Instant, D>> {
    val name: Name
    val window: ClosedRange<Instant>

    /**
     * Returns the dynamics of this profile at this time.
     *
     * This is expected to be an efficient operation, usually O(log n) in the number of segments in this profile.
     */
    fun getSegment(time: Instant): Expiring<D>
}