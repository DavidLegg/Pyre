package gov.nasa.jpl.pyre.general.plans

import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.time.Instant

/**
 * Base unit of planned simulation behavior.
 */
interface Activity<M> {
    context (scope: TaskScope)
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
    @Contextual
    val time: Instant,
    val activity: Activity<M>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

fun <M> GroundedActivity<M>.float() = FloatingActivity(activity, typeName, name)
fun <M> FloatingActivity<M>.ground(time: Instant) = GroundedActivity(time, activity, typeName, name)

fun <M : Any> SerializersModuleBuilder.model(modelClass: KClass<M>) {
    // Because Activity takes Model as a type parameter, we must specify a serializer for the model type.
    // This is despite never needing to serialize or deserialize a model.
    // Handle this transparently by specifying a dummy contextual serializer here.
    contextual(modelClass, object : KSerializer<M> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(modelClass.qualifiedName!!, PrimitiveKind.BOOLEAN)

        override fun serialize(encoder: Encoder, value: M) {
            throw UnsupportedOperationException("Cannot serialize model ${modelClass.simpleName}")
        }

        override fun deserialize(decoder: Decoder): M {
            throw UnsupportedOperationException("Cannot deserialize model ${modelClass.simpleName}")
        }
    })
}

/**
 * List all implementations of [Activity] in a [kotlinx.serialization.modules.SerializersModule].
 */
inline fun <reified M : Any> SerializersModuleBuilder.activities(block: ActivityModuleBuilder<M>.() -> Unit) {
    // Present a restricted interface to the caller, who can just call activity(A) for each activity type A
    // to register all the activity types. We'll collect them all as implementations of the polymorphic Activity type.
    polymorphic(Activity::class) {
        ActivityModuleBuilder(this@activities, this, M::class).block()
    }
}

class ActivityModuleBuilder<M : Any>(
    private val serializersBuilder: SerializersModuleBuilder,
    private val activityBuilder: PolymorphicModuleBuilder<Activity<*>>,
    modelClass: KClass<M>,
) {
    init {
        serializersBuilder.model(modelClass)
    }

    fun <A : Activity<M>> activity(clazz: KClass<A>, serializer: KSerializer<A>) {
        activityBuilder.subclass(clazz, serializer)
    }

    inline fun <reified A : Activity<M>> ActivityModuleBuilder<M>.activity(clazz: KClass<A>) =
        activity(clazz, serializer())

    /**
     * Add activities for a subsystem N of the full model M.
     * It is assumed that an adapter [Activity], which takes an Activity<N> to implement Activity<M>, is registered separately.
     */
    fun <N : Any> subsystemActivities(subsystemClass: KClass<N>, block: ActivityModuleBuilder<N>.() -> Unit) =
        ActivityModuleBuilder(serializersBuilder, activityBuilder, subsystemClass).block()

    inline fun <reified N : Any> ActivityModuleBuilder<M>.subsystemActivities(noinline block: ActivityModuleBuilder<N>.() -> Unit) =
        subsystemActivities(N::class, block)
}
