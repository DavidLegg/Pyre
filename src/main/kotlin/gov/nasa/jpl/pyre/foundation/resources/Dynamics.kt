package gov.nasa.jpl.pyre.foundation.resources

import kotlin.time.Duration

interface Dynamics<V, D : Dynamics<V, D>> {
    fun value() : V
    fun step(t: Duration) : D
}