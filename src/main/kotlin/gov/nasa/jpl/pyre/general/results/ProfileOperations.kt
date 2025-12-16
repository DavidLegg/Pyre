package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.general.results.Profile.Companion.end
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.ThinResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationEpoch
import gov.nasa.jpl.pyre.general.results.Profile.Companion.start
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.toPyreDuration
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
    fun <D : Dynamics<*, D>> List<ChannelizedReport<*>>.asProfile(name: Name, end: Instant): Profile<D> {
        @Suppress("UNCHECKED_CAST")
        val segments = this as List<ChannelizedReport<D>>
        return Profile(
            name,
            end,
            // TODO: Use "takeWhile" instead of "filter", assuming a time-ordered channel
            segments.filter { it.time < end }.associateTo(TreeMap()) { it.time to it.data }
        )
    }

    /**
     * Read a profile from simulation results.
     *
     * @param name The channel name to read, also becomes the profile name.
     */
    fun <D : Dynamics<*, D>> SimulationResults.getProfile(name: Name): Profile<D> =
        resources.getValue(name).asProfile(name, endTime)

    /**
     * Read the value at the end time of the given profile.
     *
     * @param name The channel name to read, also becomes the profile name.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V, D : Dynamics<V, D>> SimulationResults.lastValue(name: Name): V {
        val report = resources.getValue(name).last() as ChannelizedReport<D>
        return report.data.step((endTime - report.time).toPyreDuration()).value()
    }

    /**
     * Create a resource which exactly replays this [Profile].
     *
     * This can be used for deriving profiles in a small simulation, e.g. with [compute].
     * This can also be used for subsystem simulations, where the inputs to a subsystem are replayed
     * instead of running the full system.
     */
    context (scope: InitScope)
    fun <D : Dynamics<*, D>> Profile<D>.asResource(): Resource<D> =
        ResourceMonad.bind(simulationClock) {
            ThinResourceMonad.pure(this.getSegment(simulationEpoch + it.time.toKotlinDuration()))
        }

    /**
     * Create a resource which exactly replays the channel named [name].
     *
     * Combines [getProfile] with [asResource]
     */
    context (scope: InitScope)
    fun <D : Dynamics<*, D>> SimulationResults.getResource(name: Name): Resource<D> =
        getProfile<D>(name).asResource()

    /**
     * Compute a profile by running a simulation.
     */
    fun <V, D : Dynamics<V, D>> computeProfile(
        start: Instant,
        end: Instant,
        derivation: context (InitScope) () -> Resource<D>,
        dynamicsType: KType,
    ): Profile<D> {
        val results = mutableListOf<ChannelizedReport<*>>()
        lateinit var resultName: Name
        // Construct and run a simulation to compute the derived profile
        PlanSimulation.withoutIncon(
            channels(
                Name("__result") to { value, _ -> results.add(value as ChannelizedReport<*>) }
            ),
            start,
            start,
            {
                val resultResource = derivation()
                resultName = resultResource.toString()
                register(resultResource.named { "__result" }, dynamicsType)
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
        noinline derivation: context (InitScope) () -> Resource<D>,
    ) = computeProfile(start, end, derivation, typeOf<D>())

    /**
     * Compute a profile, based on these [SimulationResults], by running a simulation.
     */
    fun <V, D : Dynamics<V, D>> SimulationResults.compute(
        start: Instant = startTime,
        end: Instant = endTime,
        derivation: context (InitScope) SimulationResults.() -> Resource<D>,
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
        noinline derivation: context (InitScope) SimulationResults.() -> Resource<D>,
    ) = compute(start, end, derivation, typeOf<D>())

    /**
     * Compute a profile, based on this [Profile], by running a simulation.
     */
    fun <U, E : Dynamics<U, E>, V, D : Dynamics<V, D>> Profile<E>.compute(
        start: Instant = this.start,
        end: Instant = this.end,
        derivation: context (InitScope) Resource<E>.() -> Resource<D>,
        dynamicsType: KType,
    ): Profile<D> = computeProfile(start, end, { asResource().derivation() }, dynamicsType)

    /**
     * Compute a profile, based on this [Profile], by running a simulation.
     */
    inline fun <U, E : Dynamics<U, E>, V, reified D : Dynamics<V, D>> Profile<E>.compute(
        start: Instant = this.start,
        end: Instant = this.end,
        noinline derivation: context (InitScope) Resource<E>.() -> Resource<D>,
    ) = compute(start, end, derivation, typeOf<D>())

    operator fun <T : Comparable<T>> ClosedRange<T>.contains(other: ClosedRange<T>): Boolean =
        other.start >= this.start && other.endInclusive <= this.endInclusive

    fun <D : Dynamics<*, D>> Profile<D>.restrictTo(interval: ClosedRange<Instant>): Profile<D> {
        require(interval in window) {
            "Restriction interval ${interval.start} - ${interval.endInclusive} must be contained in" +
                    " profile window ${window.start} - ${window.endInclusive}"
        }
        return Profile(
            name,
            interval.endInclusive,
            segments.entries
                // TODO: Since segments is a navigable map, lookup the start segment and walk to the end of the interval,
                //   rather than iterating over all the keys, for better performance.
                .filter { it.key in interval }
                .associateTo(TreeMap<Instant, D>()) { it.key to it.value }
                .apply { put(interval.start, getSegment(interval.start).data) }
        )
    }
}