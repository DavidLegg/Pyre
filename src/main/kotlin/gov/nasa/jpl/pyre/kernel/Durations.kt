package gov.nasa.jpl.pyre.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

object Durations {
    /**
     * Shortest non-zero time representable by [Duration]
     */
    val EPSILON : Duration = 1.nanoseconds

    /**
     * Maximum [Duration] which uses [EPSILON] precision, copied from Kotlin source code.
     */
    val MAX_PRECISE_DURATION : Duration = (Long.MAX_VALUE / 2 / 1_000_000 * 1_000_000 - 1).nanoseconds
}