package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.STRING_SERIALIZER
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

typealias StringResource = DiscreteResource<String>
typealias MutableStringResource = MutableDiscreteResource<String>

object StringResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: String) =
        discreteResource(name, value, STRING_SERIALIZER)

    fun SparkInitContext.register(name: String, resource: DiscreteResource<String>) {
        register(name, resource, STRING_SERIALIZER)
    }
}