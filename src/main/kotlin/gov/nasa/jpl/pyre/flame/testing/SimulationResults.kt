package gov.nasa.jpl.pyre.flame.testing

import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlin.time.Instant

data class SimulationResults(
    val startTime: Instant,
    val endTime: Instant,
    val resources: Map<String, List<ChannelizedReport<*>>>,
    val activities: Map<Activity<*>, ActivityEvent>,
)
