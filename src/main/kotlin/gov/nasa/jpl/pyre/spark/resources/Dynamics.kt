package gov.nasa.jpl.pyre.spark.resources

import gov.nasa.jpl.pyre.kernel.Duration

interface Dynamics<V, D : Dynamics<V, D>> {
    fun value() : V
    fun step(t: Duration) : D
}