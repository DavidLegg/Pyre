package gov.nasa.jpl.pyre.flame.results.profiles_2

import gov.nasa.jpl.pyre.flame.results.SimulationResults
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now
import kotlin.reflect.KType

object ProfileOperations {
    context (scope: InitScope)
    fun <V, D : Dynamics<V, D>> Profile<D>.asResource(dynamicsType: KType): Resource<D> {
        val r = resource(name, initialSegment, dynamicsType = dynamicsType)

    }
}