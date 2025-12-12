package gov.nasa.jpl.pyre.general.debugging

import gov.nasa.jpl.pyre.foundation.resources.FullDynamics
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceEffect
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Condition

object Tracing {
    /**
     * Construct an exact copy of this resource, which prints a message with its dynamics every time it's read.
     */
    fun <D> Resource<D>.trace(formatter: (FullDynamics<D>) -> String = FullDynamics<D>::toString) = Resource {
        getDynamics().also { println("${now()} TRACE($this): ${formatter(it)}") }
    }.fullyNamed { name }

    /**
     * Construct an exact copy of this resource, which prints a message with its dynamics every time it's read or written.
     */
    fun <D> MutableResource<D>.trace(formatter: (FullDynamics<D>) -> String = FullDynamics<D>::toString): MutableResource<D> =
        object : MutableResource<D>, Resource<D> by (this as Resource<D>).trace(formatter) {
            context(scope: TaskScope)
            override fun emit(effect: ResourceEffect<D>) {
                println("${now()} TRACE(${this@trace}): Emit $effect")
                this@trace.emit(effect)
            }
        } named this::toString

    // Uncommon - more common to trace a boolean resource than the condition is made from
    fun Condition.trace(): Condition = { this(it).also { println("TRACE($this): $it") } }
}