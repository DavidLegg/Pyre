package gov.nasa.jpl.pyre.general.results

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
    val activities: List<ActivityEvent>,
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
    // Activity results are just a list of activity start- and end-events.
    // It's tempting to make this a map over activity instances, but an instance may be re-used in a plan.
    val activities: MutableList<ActivityEvent> = mutableListOf(),
)

data class MutableResourceResults<T>(
    var metadata: ChannelMetadata<T>,
    val data: MutableList<ChannelData<T>> = mutableListOf(),
)
