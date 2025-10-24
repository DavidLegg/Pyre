package gov.nasa.jpl.pyre.general.debugging

import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.foundation.resources.FullDynamics
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceEffect
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

object Tracing {
    /**
     * Construct an exact copy of this resource, which prints a message with its dynamics every time it's read.
     */
    fun <D> Resource<D>.trace(formatter: (FullDynamics<D>) -> String = FullDynamics<D>::toString): Resource<D> = object : Resource<D> {
        context(scope: ResourceScope)
        override suspend fun getDynamics(): FullDynamics<D> {
            val d = this@trace.getDynamics()
            println("${now()} TRACE(${this@trace}): ${formatter(d)}")
            return d
        }
    } named this::toString

    /**
     * Construct an exact copy of this resource, which prints a message with its dynamics every time it's read or written.
     */
    fun <D> MutableResource<D>.trace(formatter: (FullDynamics<D>) -> String = FullDynamics<D>::toString): MutableResource<D> =
        object : MutableResource<D>, Resource<D> by (this as Resource<D>).trace(formatter) {
            context(scope: TaskScope)
            override suspend fun emit(effect: ResourceEffect<D>) {
                println("${now()} TRACE(${this@trace}): Emit $effect")
                this@trace.emit(effect)
            }
        } named this::toString

    // Uncommon - more common to trace a boolean resource the condition is made from
    fun (() -> Condition).trace(): () -> Condition = {
        val c = this@trace()
        when (c) {
            is Condition.SatisfiedAt, is Condition.UnsatisfiedUntil ->
                println("TRACE(${this@trace}): $c")
            is Condition.Read<*> -> {}
        }
        c
    }
}