package gov.nasa.jpl.pyre.examples.sequencing.primeness

import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

class DualString<T>(
    val primeSide: MutableDiscreteResource<Side>,
    ctor: (SparkInitScope) -> T,
    context: SparkInitScope,
) {
    private val components: Map<Side, T> = Side.entries
        .associateWith { ctor(context.subContext(it.toString())) }

    operator fun get(side: Side) = components.getValue(side)

    context (scope: TaskScope)
    suspend operator fun get(side: SideIndicator) =
        components.getValue(side.resolve(primeSide.getValue()))
}