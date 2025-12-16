package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.collections.iterator
import kotlin.time.Instant

data class SimulationResults(
    val startTime: Instant,
    val endTime: Instant,
    val resources: Map<Name, List<ChannelizedReport<*>>>,
    val activities: Map<Activity<*>, ActivityActions.ActivityEvent>,
) {
    fun restrict(newStartTime: Instant = startTime, newEndTime: Instant = endTime): SimulationResults {
        val newResources = resources.mapValues {  (_, events) ->
            // Slice the events according to the new time window.
            // For the first event, move the time of the last-applicable resource event up as needed.
            val newInitialEvent = events
                .lastOrNull { it.time <= newStartTime }
                ?.copy(time = newStartTime)
                ?.let(::listOf)
                ?: listOf()
            newInitialEvent + events.filter { it.time > newStartTime && it.time < newEndTime }
        }
        // Activities are messier, because they span a window of time.
        // Selecting by start time means selecting the activities that "ran" during this window, even if they overhang.
        // Hopefully this is "good enough" for our use case...?
        // TODO: Consider a more detailed algorithm that cuts up activity spans...
        val newActivities = activities.filterValues { it.start >= newStartTime && it.start < newEndTime }
        return SimulationResults(newStartTime, newEndTime, newResources, newActivities)
    }

    infix fun compose(other: SimulationResults): SimulationResults {
        val newStartTime = minOf(startTime, other.startTime)
        val newEndTime = maxOf(endTime, other.endTime)
        val newResources: MutableMap<Name, MutableList<ChannelizedReport<*>>> = mutableMapOf()
        val newActivities: MutableMap<Activity<*>, ActivityActions.ActivityEvent> = mutableMapOf()

        fun collect(resources: Map<Name, List<ChannelizedReport<*>>>) {
            for ((resourceName, events) in resources) {
                newResources.getOrPut(resourceName, ::mutableListOf) += events
            }
        }

        val prefix = this.restrict(newEndTime = other.startTime)
        val suffix = this.restrict(newStartTime = other.endTime)

        // Collect resource events in time order, so we don't need to sort them later
        collect(prefix.resources)
        collect(other.resources)
        collect(suffix.resources)

        // Collect activity spans in reverse-precedence order, so we override low-precedence results with higher-precedence ones
        newActivities += suffix.activities
        newActivities += prefix.activities
        newActivities += other.activities

        return SimulationResults(newStartTime, newEndTime, newResources, newActivities)
    }
}