package gov.nasa.jpl.parakeet.general.results

import gov.nasa.jpl.parakeet.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.parakeet.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.parakeet.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.time.Instant

// Immutable default data types

interface SimulationResults {
    val startTime: Instant
    val endTime: Instant
    val resources: Map<Name, ResourceResults<*>>
    val activities: List<ActivityEvent>
}

interface ResourceResults<T> {
    val metadata: ChannelMetadata<T>
    val data: List<ChannelData<T>>
}

// Mutable variants used to collect results in memory, when appropriate

data class MutableSimulationResults(
    override var startTime: Instant = Instant.DISTANT_PAST,
    override var endTime: Instant = startTime,
    override val resources: MutableMap<Name, MutableResourceResults<*>> = mutableMapOf(),
    // Activity results are just a list of activity start- and end-events.
    // It's tempting to make this a map over activity instances, but an instance may be re-used in a plan.
    override val activities: MutableList<ActivityEvent> = mutableListOf(),
) : SimulationResults

data class MutableResourceResults<T>(
    override var metadata: ChannelMetadata<T>,
    override val data: MutableList<ChannelData<T>> = mutableListOf(),
) : ResourceResults<T>
