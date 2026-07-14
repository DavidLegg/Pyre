package gov.nasa.jpl.parakeet.general.resources.caching

import gov.nasa.jpl.parakeet.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.parakeet.foundation.resources.*
import gov.nasa.jpl.parakeet.foundation.resources.discrete.Discrete
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.parakeet.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.parakeet.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.parakeet.general.resources.caching.ResourceCaching.precomputedResource
import gov.nasa.jpl.parakeet.kernel.Name
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Instant


object ResourceCaching {
    /**
     * Cache a resource, either for performance or to stabilize references.
     * Optionally accepts an [equals] parameter to indicate when the value is "close enough" to not update the cache.
     */
    context (scope: InitScope)
    inline fun <V, reified D : Dynamics<V, D>> Resource<D>.cached(
        name: String,
        noinline equals: (D, D) -> Boolean = Any::equals,
    ): Resource<D> {
        val cache: MutableResource<D> = resource(name, getDynamics().data)
        val cacheIsOutOfDate = ResourceMonad.map(this, cache) { t, c -> Discrete(!equals(t, c)) }
            .named { "$cache is out of date" }
        spawn("Update $name", whenever(cacheIsOutOfDate) {
            cache.set(getDynamics().data)
        })
        return cache
    }

    /**
     * Trimmed down version of [ChannelData] for use with [precomputedResource].
     */
    @Serializable
    data class ResourcePoint<D>(
        @Contextual
        val time: Instant,
        val data: D,
    )

    /**
     * Generate a resource which evolves according to the given pre-computed profile.
     * Points in the profile must be in time order.
     * Sequence is consumed lazily, as simulation runs.
     */
    fun <V, D : Dynamics<V, D>> precomputedResource(
        name: String,
        points: Sequence<ResourcePoint<D>>,
    ): Resource<D> {
        // Note: This is a mildly unsafe implementation, in order to lazily generate points using a Sequence (iterator).
        // The full state of this resource is the combination of mutable variables and the points iterator state.
        // This all lives outside of cells, so the simulator doesn't have full control over this.
        // So long as the simulator only runs forward, this is fine.
        // Since the resource is a pure function of time, with no dependencies or effects, we don't have to worry
        // about concurrency or task scoping. This is why we can get away with not using a cell.

        // If you don't understand why we can get away with not using a cell here, don't copy this pattern.
        // Just use a cell instead.

        // Additionally, this code is strongly thread-unsafe. If multiple tasks read this in parallel,
        // we could advance the iterator too much, close it multiple times, etc.

        val iterator = points.iterator()
        require(iterator.hasNext()) { "Must provide at least one point for precomputed resource $name" }
        var currentPoint = iterator.next()
        var nextPoint: ResourcePoint<D>? = currentPoint
        fun advance() = nextPoint?.let {
            currentPoint = it
            nextPoint = if (iterator.hasNext()) iterator.next() else null
        }
        return ThinResource {
            val now = now()
            while (nextPoint != null && nextPoint!!.time <= now) advance()
            Expiring(currentPoint.data, nextPoint?.time?.let { it - now } ?: INFINITE)
        }.fullyNamed { Name(name) }
    }
}