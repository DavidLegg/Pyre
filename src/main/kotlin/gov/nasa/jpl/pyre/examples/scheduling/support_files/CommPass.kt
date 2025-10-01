package gov.nasa.jpl.pyre.examples.scheduling.support_files

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CommPass(
    @Contextual
    override val start: Instant,
    @Contextual
    override val end: Instant,
    val critical: Boolean,
    @SerialName("downlink_rate")
    val downlinkRate: Double,
) : ScheduleEvent
