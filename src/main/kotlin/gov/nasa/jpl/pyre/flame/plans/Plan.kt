@file:UseSerializers(InstantSerializer::class)

package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

@Serializable
data class Plan<M>(
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
    val activities: List<@Contextual GroundedActivity<M, *>>,
) {
    init {
        require(startTime <= endTime) {
            "Malformed plan starts at $startTime, after it ends at $endTime"
        }
    }
}

class InstantSerializer : KSerializer<Instant> by String.serializer().alias(InvertibleFunction.of(
    Instant::parse,
    Instant::toString
))
