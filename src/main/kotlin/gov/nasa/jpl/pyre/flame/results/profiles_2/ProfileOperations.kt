package gov.nasa.jpl.pyre.flame.results.profiles_2

import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.flame.results.SimulationResults
import gov.nasa.jpl.pyre.flame.results.profiles_2.Profile.Companion.end
import gov.nasa.jpl.pyre.flame.results.profiles_2.Profile.Companion.get
import gov.nasa.jpl.pyre.flame.results.profiles_2.ProfileOperations.asResource
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.ThinResourceMonad
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationClock
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationEpoch
import java.util.TreeMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.require
import kotlin.time.Instant

object ProfileOperations {
    /**
     * Convert a channel of simulation results into a profile.
     *
     * @param name Name of the resulting profile
     */
    fun <V, D : Dynamics<V, D>> List<ChannelizedReport<*>>.asProfile(name: String, end: Instant): Profile<D> {
        @Suppress("UNCHECKED_CAST")
        val segments = this as List<ChannelizedReport<D>>
        require(segments.isNotEmpty())
        return Profile(name, end, TreeMap(segments.associate { it.time to it.data }))
    }

    /**
     * Read a profile from simulation results.
     *
     * @param name The channel name to read, also becomes the profile name.
     */
    fun <V, D : Dynamics<V, D>> SimulationResults.getProfile(name: String): Profile<D> =
        resources.getValue(name).asProfile(name, endTime)

    /**
     * Read the value at the end time of the given profile.
     *
     * @param name The channel name to read, also becomes the profile name.
     */
    fun <V, D : Dynamics<V, D>> SimulationResults.lastValue(name: String): V =
        // TODO: This is not the most efficient way to do this - consider extracting only the last segment instead
        getProfile<V, D>(name).let { it[it.end] }

    /**
     * Create a resource which exactly replays this [Profile].
     *
     * This can be used for deriving profiles in a small simulation, e.g. with [compute].
     * This can also be used for subsystem simulations, where the inputs to a subsystem are replayed
     * instead of running the full system.
     */
    context (scope: InitScope)
    fun <V, D : Dynamics<V, D>> Profile<D>.asResource(): Resource<D> =
        ResourceMonad.bind(simulationClock) {
            ThinResourceMonad.pure(this.getSegment(simulationEpoch + it.time.toKotlinDuration()))
        }

    /**
     * Compute a profile by running a simulation.
     */
    fun <V, D : Dynamics<V, D>> computeProfile(
        start: Instant,
        end: Instant,
        derivation: suspend context (InitScope) () -> Resource<D>,
        dynamicsType: KType,
    ): Profile<D> {
        val results = mutableListOf<ChannelizedReport<*>>()
        lateinit var resultName: String
        // Construct and run a simulation to compute the derived profile
        PlanSimulation.withoutIncon(
            channels(
                "__result" to { value, _ -> results.add(value as ChannelizedReport<*>) }
            ),
            start,
            start,
            {
                val resultResource = derivation()
                resultName = resultResource.toString()
                register("__result", resultResource, dynamicsType)
            },
            typeOf<Unit>(),
        ).runUntil(end)
        require(results.isNotEmpty())
        // Pack up the result segments in a profile and return it
        return results.asProfile(resultName, end)
    }

    /**
     * Compute a profile by running a simulation.
     */
    inline fun <V, reified D : Dynamics<V, D>> computeProfile(
        start: Instant,
        end: Instant,
        noinline derivation: suspend context (InitScope) () -> Resource<D>,
    ) = computeProfile(start, end, derivation, typeOf<D>())

    /**
     * Compute a profile, based on these [SimulationResults], by running a simulation.
     */
    fun <V, D : Dynamics<V, D>> SimulationResults.compute(
        start: Instant = startTime,
        end: Instant = endTime,
        derivation: suspend context (InitScope) SimulationResults.() -> Resource<D>,
        dynamicsType: KType,
    ): Profile<D> = computeProfile(start, end, { derivation() }, dynamicsType)

    /**
     * Compute a derived profile by running a small simulation.
     *
     * A full simulation is constructed and run, but only the returned resource's profile is kept.
     *
     * The initial dynamics segment for the returned profile is copied from the first returned segment.
     */
    inline fun <V, reified D : Dynamics<V, D>> SimulationResults.compute(
        start: Instant = startTime,
        end: Instant = endTime,
        noinline derivation: suspend context (InitScope) SimulationResults.() -> Resource<D>,
    ) = compute(start, end, derivation, typeOf<D>())

    /**
     * Compute a profile, based on this [Profile], by running a simulation.
     */
    fun <U, E : Dynamics<U, E>, V, D : Dynamics<V, D>> Profile<E>.compute(
        start: Instant = window.start,
        end: Instant = window.endExclusive,
        derivation: suspend context (InitScope) Resource<E>.() -> Resource<D>,
        dynamicsType: KType,
    ): Profile<D> = computeProfile(start, end, { asResource().derivation() }, dynamicsType)

    /**
     * Compute a profile, based on this [Profile], by running a simulation.
     */
    inline fun <U, E : Dynamics<U, E>, V, reified D : Dynamics<V, D>> Profile<E>.compute(
        start: Instant = window.start,
        end: Instant = window.endExclusive,
        noinline derivation: suspend context (InitScope) Resource<E>.() -> Resource<D>,
    ) = compute(start, end, derivation, typeOf<D>())

    operator fun <T : Comparable<T>> OpenEndRange<T>.contains(other: OpenEndRange<T>): Boolean =
        other.start >= this.start && other.endExclusive <= this.endExclusive

    fun <D : Dynamics<*, D>> Profile<D>.restrictTo(interval: OpenEndRange<Instant>): Profile<D> {
        require(interval in window) {
            "Restriction interval ${interval.start} - ${interval.endExclusive} must be contained in" +
                    " profile window ${window.start} - ${window.endExclusive}"
        }
        return Profile(
            name,
            interval.endExclusive,
            segments.filterKeys { it in interval } + (interval.start to getSegment(interval.start).data)
        )
    }
}