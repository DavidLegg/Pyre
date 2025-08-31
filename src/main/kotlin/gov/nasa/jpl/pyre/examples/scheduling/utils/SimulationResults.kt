package gov.nasa.jpl.pyre.examples.scheduling.utils

import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlin.time.Instant

data class SimulationResults<M>(
    val startTime: Instant,
    val endTime: Instant,
    val resources: Map<String, List<ChannelizedReport<*>>>,
    val activities: Map<Activity<M>, ActivityEvent<M>>,
) {
    fun restrict(newStartTime: Instant = startTime, newEndTime: Instant = endTime): SimulationResults<M> {
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

    infix fun compose(other: SimulationResults<M>): SimulationResults<M> {
        val newStartTime = minOf(startTime, other.startTime)
        val newEndTime = maxOf(endTime, other.endTime)
        val newResources: MutableMap<String, MutableList<ChannelizedReport<*>>> = mutableMapOf()
        val newActivities: MutableMap<Activity<M>, ActivityEvent<M>> = mutableMapOf()

        fun collect(resources: Map<String, List<ChannelizedReport<*>>>) {
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
