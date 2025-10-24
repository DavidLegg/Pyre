package gov.nasa.jpl.pyre.examples.sequencing.primeness

import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

class DualString<T>(
    val primeSide: MutableDiscreteResource<Side>,
    ctor: (InitScope) -> T,
    context: InitScope,
) {
    private val components: Map<Side, T> = with (context) {
            Side.entries.associateWith {
                ctor(subContext(it.toString()))
            }
        }

    operator fun get(side: Side) = components.getValue(side)

    context (scope: TaskScope)
    suspend operator fun get(side: SideIndicator) =
        components.getValue(side.resolve(primeSide.getValue()))
}