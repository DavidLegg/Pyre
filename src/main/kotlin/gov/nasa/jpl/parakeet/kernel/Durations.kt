package gov.nasa.jpl.parakeet.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

object Durations {
    /**
     * Shortest non-zero time representable by [Duration]
     */
    val EPSILON : Duration = 1.nanoseconds

    /**
     * Shortest non-zero time step representable when a [Duration] [isCoarse]
     */
    val COARSE_EPSILON : Duration = 1.milliseconds

    /**
     * Maximum [Duration] which uses [EPSILON] precision, copied from Kotlin source code.
     */
    val MAX_PRECISE_DURATION : Duration = (Long.MAX_VALUE / 2 / 1_000_000 * 1_000_000 - 1).nanoseconds

    /**
     * Minimum [Duration] which uses [EPSILON] precision, copied from Kotlin source code.
     */
    val MIN_PRECISE_DURATION : Duration = -MAX_PRECISE_DURATION

    /**
     * Whether this [Duration] uses [EPSILON] precision.
     * Durations outside this range use [COARSE_EPSILON] precision instead.
     */
    fun Duration.isPrecise() : Boolean = this in MIN_PRECISE_DURATION..MAX_PRECISE_DURATION

    /**
     * Whether this [Duration] uses [COARSE_EPSILON] precision.
     * Durations of smaller magnitude may use [EPSILON] precision instead.
     */
    fun Duration.isCoarse() : Boolean = !isPrecise()

    /**
     * Either [EPSILON] or [COARSE_EPSILON], as appropriate for this [Duration]'s magnitude.
     */
    val Duration.epsilon : Duration get() = if (isPrecise()) EPSILON else COARSE_EPSILON
}