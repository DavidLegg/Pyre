package gov.nasa.jpl.pyre.general.results.discrete

import gov.nasa.jpl.pyre.general.results.Profile.Companion.end
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.general.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.or
import kotlin.collections.iterator
import kotlin.time.Instant

typealias BooleanProfile = DiscreteProfile<Boolean>

object BooleanProfileOperations {
    /**
     * Return the list of intervals over which this profile is true.
     */
    fun BooleanProfile.windows(): List<OpenEndRange<Instant>> {
        val results = mutableListOf<OpenEndRange<Instant>>()
        var windowStart: Instant? = null
        for (segment in segments) {
            if (segment.value.value && windowStart == null) {
                windowStart = segment.key
            } else if (!segment.value.value && windowStart != null) {
                results += windowStart..<segment.key
                windowStart = null
            }
        }
        if (windowStart != null) {
            results += windowStart..<end
        }
        return results
    }

    fun BooleanProfile.always() = segments.values.all { it.value }
    fun BooleanProfile.never() = segments.values.none { it.value }
    fun BooleanProfile.sometimes() = !never()
    fun BooleanProfile.sometimesNot() = !always()

    infix fun BooleanProfile.and(other: BooleanProfile): BooleanProfile =
        compute { this and other.asResource() }
    infix fun BooleanProfile.or(other: BooleanProfile): BooleanProfile =
        compute { this or other.asResource() }
    operator fun BooleanProfile.not(): BooleanProfile =
        compute { !this }
}