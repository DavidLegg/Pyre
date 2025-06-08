package gov.nasa.jpl.pyre.spark.plans


class ActivityFactory<M> {
    private val constructors: MutableMap<String, (ActivitySpec) -> Activity<M, *>> = mutableMapOf()

    fun addConstructor(typeName: String, constructor: (ActivitySpec) -> Activity<M, *>): ActivityFactory<M> {
        constructors[typeName] = constructor
        return this
    }

    fun constructActivity(spec: ActivitySpec): Activity<M, *> {
        val ctor = requireNotNull(constructors[spec.typeName]) {
            "No constructors found for type ${spec.typeName}"
        }
        return ctor(spec)
    }
}
