package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.spark.BasicSerializers.alias


class ActivitySerializer<M> : Serializer<GroundedActivity<M, *>> {
    private val serializers: MutableMap<String, Serializer<Activity<M, *>>> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <A : Activity<M, *>> add(typeName: String, activitySerializer: Serializer<A>): ActivitySerializer<M> {
        require(typeName !in serializers) { "A serializer has already been added for $typeName" }
        serializers[typeName] = activitySerializer.alias(
            InvertibleFunction.of({ it as A }, { it }))
        return this
    }

    override fun serialize(obj: GroundedActivity<M, *>): JsonValue {
        return JsonMap(mapOf(
            "time" to Duration.serializer().serialize(obj.time),
            "activity" to requireNotNull(serializers[obj.typeName]) {
                "No serializer registered for ${obj.typeName}"
            }.serialize(obj.activity),
            "type" to JsonString(obj.typeName),
            "name" to JsonString(obj.name),
        ))
    }

    override fun deserialize(jsonValue: JsonValue): GroundedActivity<M, *> {
        val typeName = ((jsonValue as JsonMap).values["type"] as JsonString).value
        return GroundedActivity(
            Duration.serializer().deserialize(requireNotNull(jsonValue.values["time"])),
            requireNotNull(serializers[typeName]) {
                "No serializer registered for $typeName"
            }.deserialize(requireNotNull(jsonValue.values["activity"])),
            typeName,
            (jsonValue.values["name"] as JsonString).value,
        )
    }
}
