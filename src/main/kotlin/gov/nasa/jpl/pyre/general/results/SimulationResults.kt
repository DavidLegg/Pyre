package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.time.Instant

// Immutable default data types

data class SimulationResults(
    val startTime: Instant,
    val endTime: Instant,
    val resources: Map<Name, ResourceResults<*>>,
    val activities: Map<Activity<*>, ActivityEvent>,
)

data class ResourceResults<T>(
    val metadata: ChannelMetadata<T>,
    val data: List<ChannelData<T>>,
)

// Mutable variants used to collect results in memory, when appropriate

class MutableSimulationResults(
    var startTime: Instant = Instant.DISTANT_PAST,
    var endTime: Instant = startTime,
    val resources: MutableMap<Name, MutableResourceResults<*>> = mutableMapOf(),
    val activities: MutableMap<Activity<*>, ActivityEvent> = mutableMapOf(),
)

data class MutableResourceResults<T>(
    var metadata: ChannelMetadata<T>,
    val data: MutableList<ChannelData<T>> = mutableListOf(),
)
