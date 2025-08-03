package gov.nasa.jpl.pyre.examples.sequencing.primeness

import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

class DualString<T>(
    val primeSide: MutableDiscreteResource<Side>,
    ctor: (SparkInitContext) -> T,
    context: SparkInitContext,
) {
    private val components: Map<Side, T> = Side.entries
        .associateWith { ctor(context.subContext(it.toString())) }

    operator fun get(side: Side) = components.getValue(side)

    context (scope: SparkTaskScope)
    suspend operator fun get(side: SideIndicator) =
        components.getValue(side.resolve(primeSide.getValue()))
}