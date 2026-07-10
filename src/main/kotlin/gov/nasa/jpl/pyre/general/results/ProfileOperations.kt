package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.general.results.Profile.Companion.end
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Expiring
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.ThinResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.general.results.Profile.Companion.start
import gov.nasa.jpl.pyre.kernel.Name
import java.util.TreeMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.require
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Instant

object ProfileOperations {
    val Profile2<*>.start: Instant get() = window.start
    val Profile2<*>.end: Instant get() = window.endInclusive

    // TODO: I don't like the distinction between ResourceResults and Profile.
    //   Despite the fact that I can "view" one as the other without copying, which is neat, it's conceptually messy...

    /**
     * Returns a view of [this] channel of results as a profile.
     * This method does not copy the results.
     *
     * Assumes that [data] is already sorted by time, and does not verify this.
     * Results are undefined if this assumption is violated.
     */
    fun <D : Dynamics<*, D>> ResourceResults<D>.asProfile(endTime: Instant): Profile2<D> {
        require(data.isNotEmpty()) {
            "Cannot construct a profile from empty results"
        }
        val startTime = data.first().time
        require(startTime <= endTime) {
            "Invalid end time $endTime before first data point at $startTime"
        }
        return object : Profile2<D> {
            override val name: Name = metadata.channel
            override val window: ClosedRange<Instant> = startTime..endTime

            override fun getSegment(time: Instant): Expiring<D> {
                require(time in window) {
                    "Time $time is outside of profile window [$start, $end]"
                }
                val i = data.binarySearchBy(time) { it.time }
                val nextSegmentIndex: Int
                val dynamics: D
                if (i >= 0) {
                    // This time is the exact start of a profile segment.
                    // We can return the dynamics exactly, without stepping it
                    nextSegmentIndex = i + 1
                    dynamics = data[i].data
                } else {
                    // This time is not the exact start of a profile segment.
                    // Instead, the binary search returns the segment after the active one:
                    nextSegmentIndex = i.inv()
                    val activeSegment = data[nextSegmentIndex - 1]
                    // Finally, use that to look up which dynamics is active, and how long to step it forward:
                    dynamics = activeSegment.data.step(time - activeSegment.time)
                }
                return Expiring(dynamics, data.getOrNull(nextSegmentIndex)?.time?.let { it - time } ?: INFINITE)
            }

            override fun iterator(): Iterator<Pair<Instant, D>> = data.map { it.time to it.data }.iterator()
        }
    }

    /**
     * Read a profile from simulation results.
     *
     * @param name The channel name to read, also becomes the profile name.
     */
    fun <D : Dynamics<*, D>> SimulationResults.getProfile(name: Name): Profile2<D> =
        @Suppress("UNCHECKED_CAST")
        (resources.getValue(name) as ResourceResults<D>).asProfile(endTime)

    /**
     * Create a resource which exactly replays this [Profile].
     *
     * This can be used for deriving profiles in a small simulation, e.g. with [compute].
     * This can also be used for subsystem simulations, where the inputs to a subsystem are replayed
     * instead of running the full system.
     */
    context (_: InitScope)
    fun <D : Dynamics<*, D>> Profile2<D>.asResource(): Resource<D> =
        ResourceMonad.bind(simulationClock) {
            ThinResourceMonad.pure(this.getSegment(it.time))
        }.fullyNamed { name }

    /**
     * Create a resource which exactly replays the channel named [name].
     *
     * Combines [getProfile] with [asResource]
     */
    context (_: InitScope)
    fun <D : Dynamics<*, D>> SimulationResults.getResource(name: Name): Resource<D> =
        getProfile<D>(name).asResource()

    // --- TODO: Rewrite everything below this line in terms of new profile interface ---

    /**
     * Compute a profile by running a simulation.
     */
    fun <V, D : Dynamics<V, D>> computeProfile(
        start: Instant,
        end: Instant,
        derivation: context (InitScope) () -> Resource<D>,
        dynamicsType: KType,
    ): Profile2<D> {
        lateinit var results: MutableResourceResults<D>
        lateinit var resultName: Name
        // Construct and run a simulation to compute the derived profile
        Simulator(
            object : BaseChannelizedReportHandler() {
                override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelData<T>) -> Unit {
                    @Suppress("UNCHECKED_CAST")
                    if (metadata.channel == resultName) {
                        results = MutableResourceResults(metadata) as MutableResourceResults<D>
                        return { results.data.add(it as ChannelData<D>) }
                    } else {
                        // Just ignore all other channels
                        return {}
                    }
                }
            },
            start,
        ) {
            val resultResource = derivation()
            resultName = resultResource.name
            register(resultResource, dynamicsType)
        }.runUntil(end)

        require(results.data.isNotEmpty())
        // Pack up the result segments in a profile and return it
        return results.asProfile()
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