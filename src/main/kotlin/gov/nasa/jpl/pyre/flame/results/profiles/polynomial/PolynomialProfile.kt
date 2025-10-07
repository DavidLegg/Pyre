package gov.nasa.jpl.pyre.flame.results.profiles.polynomial

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.flame.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.spark.resources.Expiring
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.time.Instant

class PolynomialProfile(
    val initialValue: Double,
    val segments: NavigableMap<Instant, Polynomial>,
) {
    constructor(initialValue: Double, vararg segments: Pair<Instant, Polynomial>)
            : this(initialValue, TreeMap(sortedMapOf(*segments)))

    fun at(time: Instant): Double = segments.floorEntry(time)?.let {
        it.value.step((time - it.key).toPyreDuration()).value()
    } ?: initialValue

    companion object {
        fun <R> bind1(p: PolynomialProfile, f: (Polynomial, Duration) -> List<Expiring<R>>): Profile<R>
    }
}