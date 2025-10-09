package gov.nasa.jpl.pyre.flame.debugging

import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now

object Tracing {
    /**
     * Construct an exact copy of this resource, which prints a message with its dynamics every time it's read.
     */
    fun <D> Resource<D>.trace(): Resource<D> = object : Resource<D> {
        context(scope: ResourceScope)
        override suspend fun getDynamics(): FullDynamics<D> {
            val d = this@trace.getDynamics()
            println("${now()} TRACE(${this@trace}): $d")
            return d
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