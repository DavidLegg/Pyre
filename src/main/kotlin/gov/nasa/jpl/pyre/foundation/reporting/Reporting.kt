package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.wheneverChanges
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.channel
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object Reporting {
    context (scope: InitScope)
    inline fun <reified T> channel(name: String, vararg metadata: Pair<String, String>): Channel<T> =
        channel(scope.contextName / name, *metadata)

    /**
     * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
     */
    context (scope: InitScope)
    fun <V, D : Dynamics<V, D>> register(
        resource: Resource<D>,
        dynamicsType: KType,
        metadata: Map<String, String> = mapOf(),
    ) {
        val channel = scope.channel<D>(resource.name, metadata, dynamicsType)
        channel.report(resource.getDynamics().data)
        spawn("Report resource ${resource.name.simpleName}", wheneverChanges(resource) {
            channel.report(resource.getDynamics().data)
        })
    }

    /**
     * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
     */
    context (scope: InitScope)
    inline fun <V, reified D : Dynamics<V, D>, R : Resource<D>> R.registered(vararg metadata: Pair<String, String>): R =
        also { register(it, typeOf<D>(), metadata.toMap()) }
}
