package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

// No type alias, just use (Mutable)DiscreteResource<E>

object EnumResourceOperations {
    inline fun <reified E : Enum<E>> SimulationState.SimulationInitContext.discreteResource(name: String, value: E) =
        discreteResource(name, value, enumSerializer())

    inline fun <reified E : Enum<E>> SparkInitContext.register(name: String, resource: DiscreteResource<E>) {
        register(name, resource, enumSerializer())
    }

    inline fun <reified E : Enum<E>> enumSerializer(): Serializer<E> = Serializer.of(InvertibleFunction.of(
        { JsonString(it.name) },
        { enumValueOf((it as JsonString).value) }
    ))
}