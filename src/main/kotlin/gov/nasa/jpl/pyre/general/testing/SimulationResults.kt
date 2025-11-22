package gov.nasa.jpl.pyre.general.testing

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import kotlin.time.Instant

data class SimulationResults(
    val startTime: Instant,
    val endTime: Instant,
    val resources: Map<String, List<ChannelizedReport<*>>>,
    val activities: Map<Activity<*>, ActivityEvent>,
)
