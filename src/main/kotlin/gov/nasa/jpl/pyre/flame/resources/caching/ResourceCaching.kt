package gov.nasa.jpl.pyre.flame.resources.caching

import gov.nasa.jpl.pyre.coals.Closeable
import gov.nasa.jpl.pyre.coals.Closeable.Companion.closesWith
import gov.nasa.jpl.pyre.coals.Serialization.decodeFromJsonElement
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Expiring
import gov.nasa.jpl.pyre.spark.resources.Expiry
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ThinResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.SparkContextExtensions.now
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.sparkResourceScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant


object ResourceCaching {
    /**
     * Trimmed down version of [gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport] for use with [precomputedResource].
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
    fun <V, D : Dynamics<V, D>> SparkInitContext.precomputedResource(
        name: String,
        points: Closeable<Sequence<ResourcePoint<D>>>,
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

        val iterator = points.self.iterator()
        require(iterator.hasNext()) { "Must provide at least one point for precomputed resource $name" }
        var currentPoint = iterator.next()
        var nextPoint: ResourcePoint<D>? = currentPoint
        fun advance() = nextPoint?.let {
            currentPoint = it
            nextPoint = if (iterator.hasNext()) iterator.next() else null
        } ?: { points.close() }
        return ThinResource {
            with (sparkResourceScope()) {
                val now = now()
                while (nextPoint != null && nextPoint!!.time <= now) advance()
                Expiring(currentPoint.data, Expiry(nextPoint?.time?.let { (it - now).toPyreDuration() }))
            }
        } named { name }
    }

    // TODO: Test this routine. If it works, consider these optimizations:
    //   2. A way to run the file reading in a background IO coroutine (but think through if this would actually help first.)
    //   3. A way for multiple precomputed resources to share a single parser/filter operator here.
    //   4. A CSV reader, for CSV output format?

    /**
     * Accepts a JSON Lines file which is a list of [ChannelizedReport]s in time order, one per line.
     * Selects the reports on the given channel, and returns a resource which evolves according to that profile.
     *
     * Note that this is the default output file format produced by [gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation].
     * As such, this method is a way to feed the output of one simulation "layer" into the next.
     */
    fun <V, D : Dynamics<V, D>> SparkInitContext.fileBackedResource(
        name: String,
        file: Path,
        jsonFormat: Json = Json,
        channel: String = name,
        dynamicsType: KType
    ): Resource<D> {
        val reader = file.bufferedReader()
        val points = reader.lineSequence()
            .map { jsonFormat.decodeFromString<ChannelizedReport<JsonElement>>(it) }
            .filter { it.channel == channel }
            .map { ResourcePoint(it.time, jsonFormat.decodeFromJsonElement<D>(dynamicsType, it.data)) }
            .closesWith { reader.close() }
        return precomputedResource(name, points)
    }

    inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.fileBackedResource(
        name: String,
        file: Path,
        jsonFormat: Json = Json,
        channel: String = name,
    ): Resource<D> = fileBackedResource(name, file, jsonFormat, channel, typeOf<D>())
}