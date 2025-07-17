package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.reporting.report
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Base unit of planned simulation behavior.
 */
interface Activity<M> {
    context (scope: SparkTaskScope<Unit>)
    suspend fun effectModel(model: M)
}

/**
 * An activity, with all supplemental information attached, except for the start time.
 * This activity "floats" in time until it is grounded by choosing a start time.
 */
@Serializable
data class FloatingActivity<M>(
    val activity: Activity<M>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

/**
 * An activity with all supplemental information attached, including the start time.
 */
@Serializable
data class GroundedActivity<M>(
    val time: Duration,
    val activity: Activity<M>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

fun <M> GroundedActivity<M>.float() = FloatingActivity(activity, typeName, name)
fun <M> FloatingActivity<M>.ground(time: Duration) = GroundedActivity(time, activity, typeName, name)

suspend fun <M> SparkTaskScope<*>.defer(time: Duration, activity: FloatingActivity<M>, model: M) {
    spawn(activity.name, task {
        with(sparkTaskScope()) {
            delay(time)
            report(
                "activities", JsonObject(mapOf(
                    "name" to JsonPrimitive(activity.name),
                    "type" to JsonPrimitive(activity.typeName),
                    "event" to JsonPrimitive("start")
                ))
            )
            val result = activity.activity.effectModel(model)
            report(
                "activities", JsonObject(mapOf(
                    "name" to JsonPrimitive(activity.name),
                    "type" to JsonPrimitive(activity.typeName),
                    "event" to JsonPrimitive("end")
                ))
            )
            result
        }
    })
}

inline fun <reified M : Any> activitySerializersModule(block: ActivitySerializerBuilder<M>.() -> Unit): SerializersModule =
    SerializersModule {
        // Because Activity takes Model as a type parameter, we must specify a serializer for the model type.
        // This is despite never needing to serialize or deserialize a model.
        // Handle this transparently by specifying a dummy contextual serializer here.
        contextual(M::class, object : KSerializer<M> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor(M::class.qualifiedName!!, PrimitiveKind.BOOLEAN)

            override fun serialize(encoder: Encoder, value: M) {
                throw UnsupportedOperationException("Cannot serialize model ${M::class.simpleName}")
            }

            override fun deserialize(decoder: Decoder): M {
                throw UnsupportedOperationException("Cannot deserialize model ${M::class.simpleName}")
            }
        })

        // Present a restricted interface to the caller, who can just call activity(A) for each activity type A
        // to register all the activity types. We'll collect them all as implementations of the polymorphic Activity type.
        polymorphic(Activity::class) {
            object : ActivitySerializerBuilder<M> {
                override fun <A : Activity<M>> activity(clazz: KClass<A>, serializer: KSerializer<A>) {
                    this@polymorphic.subclass(clazz, serializer)
                }
            }.block()
        }
    }

interface ActivitySerializerBuilder<M> {
    fun <A : Activity<M>> activity(clazz: KClass<A>, serializer: KSerializer<A>)
}

inline fun <M, reified A : Activity<M>> ActivitySerializerBuilder<M>.activity(clazz: KClass<A>) =
    activity(clazz, serializer())
