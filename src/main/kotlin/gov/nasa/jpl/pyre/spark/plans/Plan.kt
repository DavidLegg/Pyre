package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer

// TODO: Consider letting a plan use Activity, not ActivityDirective, - the fully-constructed, runnable class.
//   Maybe equip Activity with a Serializer? Not sure how the save/restore in that situation works...
data class Plan(
    val name: String,
    val startTime: Duration,
    val endTime: Duration,
    val activities: List<ActivityDirective>,
)

data class ActivityDirective(
    val time: Duration,
    val activitySpec: ActivitySpec,
) {
    companion object {
        // TODO: Build up automatic record serialization somehow
        fun serializer(): Serializer<ActivityDirective> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "time" to Duration.serializer().serialize(it.time),
                    "name" to JsonString(it.activitySpec.name),
                    "type" to JsonString(it.activitySpec.typeName),
                    "args" to it.activitySpec.arguments
                ))
            },
            {
                with ((it as JsonMap).values) {
                    ActivityDirective(
                        Duration.serializer().deserialize(requireNotNull(get("time"))),
                        ActivitySpec(
                            (get("name") as JsonString).value,
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
    val name: String,
    val typeName: String = name,
    val arguments: JsonMap = JsonMap.empty(),
)
