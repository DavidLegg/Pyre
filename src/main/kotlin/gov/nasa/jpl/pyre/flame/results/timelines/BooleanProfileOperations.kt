package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map
import kotlin.time.Instant

typealias BooleanProfile = DiscreteProfile<Boolean>

object BooleanProfileOperations {
    fun interval(start: Instant, end: Instant?): BooleanProfile = DiscreteProfile(
        false,
        if (end == null) sortedMapOf(start to true)
        else sortedMapOf(start to true, end to false))

    infix fun BooleanProfile.and(other: BooleanProfile): BooleanProfile = map(this, other, Boolean::and)
    infix fun BooleanProfile.or(other: BooleanProfile): BooleanProfile = map(this, other, Boolean::or)
    fun not(timeline: BooleanProfile): BooleanProfile = map(timeline, Boolean::not)

    /**
     * "set union", equivalent to [or]
     */
    operator fun BooleanProfile.plus(other: BooleanProfile): BooleanProfile = this or other

    /**
     * "set minus", equivalent to `this and not(other)`
     */
    operator fun BooleanProfile.minus(other: BooleanProfile): BooleanProfile = this and not(other)

    fun BooleanProfile.always(): Boolean = initialValue && values.values.all { it }
    fun BooleanProfile.never(): Boolean = !initialValue && values.values.all { !it }
    fun BooleanProfile.sometimes(): Boolean = !never()
    fun BooleanProfile.sometimesNot(): Boolean = !always()

    /**
     * Return the time ranges when this profile is true
     */
    fun BooleanProfile.windows(): List<OpenEndRange<Instant>> {
        var v0 = initialValue
        var start = Instant.DISTANT_PAST
        val result = mutableListOf<OpenEndRange<Instant>>()
        for ((t, v1) in values) {
            if (v1 && !v0) {
                // leading edge:
                start = t
            }
            else if (!v1 && v0) {
                // trailing edge
                result += start..<t
            }
            // else: value agrees with prior value, ignore it
            v0 = v1
        }
        if (v0) {
            // There's a half-open interval extending into the distant future
            result += start..<Instant.DISTANT_FUTURE
        }
        return result
    }
}