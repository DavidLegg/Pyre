package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.wheneverChanges
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.report
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

typealias Channel = String

@Serializable
data class ChannelizedReport<T>(
    val channel: String,
    @Contextual
    val time: Instant,
    val data: T,
)

object Reporting {
    /**
     * Wraps the simple simulation report function with [ChannelizedReport],
     * categorizing the report on a channel and adding the time of report.
     *
     * Note: reportType must be [ChannelizedReport] with an invariant argument for the type of data.
     * Giving this type directly, instead of constructing it within this function,
     * offers opportunities to improve performance by computing the reified type at init or even compile time.
     */
    context (scope: TaskScope)
    fun <T> report(channel: Channel, data: T, reportType: KType) {
        report(ChannelizedReport(
            channel,
            now(),
            data,
        ), reportType)
    }

    /**
     * Wraps the simple simulation report function with [ChannelizedReport],
     * categorizing the report on a channel and adding the time of report.
     */
    context (scope: TaskScope)
    inline fun <reified T> report(channel: Channel, data: T) = report(channel, data, typeOf<ChannelizedReport<T>>())

    /**
     * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
     */
    context (scope: InitScope)
    fun <V, D : Dynamics<V, D>> register(
        name: String,
        resource: Resource<D>,
        dynamicsType: KType,
    ) {
        val reportType = ChannelizedReport::class.withArg(dynamicsType)
        val reportedResourceName = scope.contextName / name
        scope.report(ChannelizedReport(reportedResourceName.toString(), now(), resource.getDynamics().data), reportType)
        spawn("Report resource $name", wheneverChanges(resource) {
            report(reportedResourceName.toString(), resource.getDynamics().data, reportType)
        })
    }

    /**
     * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
     */
    context (scope: InitScope)
    inline fun <V, reified D : Dynamics<V, D>> register(name: String, resource: Resource<D>) =
        register(name, resource, typeOf<D>())

    /**
     * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
     * Use the resource's own toString method as its name, for use with [gov.nasa.jpl.pyre.foundation.resources.named].
     */
    context (scope: InitScope)
    inline fun <V, reified D : Dynamics<V, D>> register(resource: Resource<D>) =
        register(resource.toString(), resource, typeOf<D>())
}
