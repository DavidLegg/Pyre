package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.ember.JsonValue.JsonMap

class ActivityFactory<M> {
    private val constructors: MutableMap<String, (JsonMap) -> Activity<M, *>> = mutableMapOf()

    fun addConstructor(name: String, constructor: (JsonMap) -> Activity<M, *>) {
        constructors[name] = constructor
    }

    fun constructActivity(spec: ActivitySpec): Activity<M, *> {
        val ctor = requireNotNull(constructors[spec.typeName]) {
            "No constructors found for type ${spec.typeName}"
        }
        return ctor(spec.arguments)
    }
}
