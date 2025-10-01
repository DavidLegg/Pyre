package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.flame.results.SimulationResults
import gov.nasa.jpl.pyre.flame.results.timelines.BooleanProfileOperations.interval
import gov.nasa.jpl.pyre.flame.results.timelines.BooleanProfileOperations.not
import gov.nasa.jpl.pyre.flame.results.timelines.BooleanProfileOperations.or
import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map
import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.pure
import gov.nasa.jpl.pyre.flame.results.timelines.SetProfileOperations.empty
import gov.nasa.jpl.pyre.flame.results.timelines.SetProfileOperations.singleton
import gov.nasa.jpl.pyre.flame.results.timelines.SetProfileOperations.union
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import kotlin.reflect.KClass
import kotlin.time.Instant

object DiscreteProfileOperations {
    fun <T> constant(value: T): DiscreteProfile<T> = pure(value)

    fun <T> List<ChannelizedReport<*>>.toDiscreteProfile(): DiscreteProfile<T> {
        @Suppress("UNCHECKED_CAST")
        val values = this.associate {
            it.time to (it.data as Discrete<T>).value
        }.toSortedMap()
        val initialValue = requireNotNull(values.firstEntry()) {
            "No data reported"
        }.value
        return DiscreteProfile(initialValue, values)
    }

    fun <T> SimulationResults.discreteProfile(name: String): DiscreteProfile<T> =
        resources.getValue(name).toDiscreteProfile()

    fun SimulationResults.running(predicate: (ActivityEvent) -> Boolean): BooleanProfile = activities.values
        .filter(predicate)
        .map { interval(it.start, it.end) }
        .reduceOrNull { p, q -> p or q } ?: constant(false)

    infix fun <T> DiscreteProfile<T>.equals(other: DiscreteProfile<T>): BooleanProfile =
        map(this, other) { x, y -> x == y }

    infix fun <T> DiscreteProfile<T>.notEquals(other: DiscreteProfile<T>): BooleanProfile =
        not(this equals other)

    infix fun <T> DiscreteProfile<T>.isIn(other: DiscreteProfile<out Collection<T>>): BooleanProfile =
        other contains this

    infix fun <T> DiscreteProfile<T>.notIn(other: DiscreteProfile<out Collection<T>>): BooleanProfile =
        other notContains this

    infix fun <T> DiscreteProfile<out Collection<T>>.contains(other: DiscreteProfile<T>): BooleanProfile =
        map(this, other, Collection<T>::contains)

    infix fun <T> DiscreteProfile<out Collection<T>>.notContains(other: DiscreteProfile<T>): BooleanProfile =
        not(this contains other)
}