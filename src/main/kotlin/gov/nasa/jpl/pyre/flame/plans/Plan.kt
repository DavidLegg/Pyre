@file:UseSerializers(InstantSerializer::class)

package gov.nasa.jpl.pyre.flame.plans

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Instant

@Serializable
data class Plan<M>(
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
    val activities: List<GroundedActivity<M>>,
) {
    init {
        require(startTime <= endTime) {
            "Malformed plan starts at $startTime, after it ends at $endTime"
        }
    }
}

