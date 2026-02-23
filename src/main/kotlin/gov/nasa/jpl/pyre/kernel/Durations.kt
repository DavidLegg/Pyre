package gov.nasa.jpl.pyre.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

object Durations {
    /**
     * Shortest non-zero time representable by [Duration]
     */
    val EPSILON : Duration = 1.nanoseconds
}