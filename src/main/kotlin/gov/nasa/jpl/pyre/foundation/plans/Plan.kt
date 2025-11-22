@file:UseSerializers(InstantSerializer::class)

package gov.nasa.jpl.pyre.foundation.plans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Instant

@Serializable
data class Plan<M>(
    @SerialName("start")
    val startTime: Instant,
    @SerialName("end")
    val endTime: Instant,
    val activities: List<GroundedActivity<M>> = emptyList(),
) {
    init {
        require(startTime <= endTime) {
            "Malformed plan starts at $startTime, after it ends at $endTime"
        }
    }
}

