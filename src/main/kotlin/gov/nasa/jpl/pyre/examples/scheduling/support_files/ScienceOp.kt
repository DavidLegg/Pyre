package gov.nasa.jpl.pyre.examples.scheduling.support_files

import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ScienceOp(
    @Contextual
    val start: Instant,
    @Contextual
    val end: Instant,
    val critical: Boolean,
    val target: GeometryModel.PointingTarget,
)
