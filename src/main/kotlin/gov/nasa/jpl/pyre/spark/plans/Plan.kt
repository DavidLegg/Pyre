package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer

data class Plan(
    val name: String,
    val startTime: Duration,
    val endTime: Duration,
    val activities: List<ActivityDirective>,
)

data class ActivityDirective(
    val time: Duration,
    val name: String,
    val activitySpec: ActivitySpec,
) {
    companion object {
        // TODO: Build up automatic record serialization somehow
        fun serializer(): Serializer<ActivityDirective> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "time" to Duration.serializer().serialize(it.time),
                    "name" to JsonString(it.name),
                    "type" to JsonString(it.activitySpec.typeName),
                    "args" to it.activitySpec.arguments
                ))
            },
            {
                with ((it as JsonMap).values) {
                    ActivityDirective(
                        Duration.serializer().deserialize(requireNotNull(get("time"))),
                        (get("name") as JsonString).value,
                        ActivitySpec(
                            (get("type") as JsonString).value,
                            (get("args") as JsonMap?) ?: JsonMap.empty()
                        )
                    )
                }
            }
        ))
    }
}

data class ActivitySpec(
    val typeName: String,
    val arguments: JsonMap
)
