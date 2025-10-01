package gov.nasa.jpl.pyre.examples.scheduling.support_files

import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ScienceOp(
    @Contextual
    override val start: Instant,
    @Contextual
    override val end: Instant,
    val critical: Boolean,
    val target: GeometryModel.PointingTarget,
) : ScheduleEvent
