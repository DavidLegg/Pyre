package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions
import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.collections.mapValues

object SimulationResultsOperations {
    /**
     * Construct a report handler that will collect all channelized reports into this [MutableSimulationResults]
     *
     * Note that this will capture all simulation results in memory.
     * For large simulations, consider alternate approaches that write results to disk as the simulation runs.
     */
    fun MutableSimulationResults.reportHandler(): ChannelizedReportHandler = object : BaseChannelizedReportHandler() {
        override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelReport.ChannelData<T>) -> Unit {
            if (metadata.channel == Name("activities")) {
                return {
                    @Suppress("UNCHECKED_CAST")
                    it as ChannelReport.ChannelData<ActivityActions.ActivityEvent>
                    // Just add the activity event to the list
                    activities += it.data
                }
            } else {
                val resourceResults = MutableResourceResults(metadata)
                resources[metadata.channel] = resourceResults
                return resourceResults.data::add
            }
        }
    }

    /**
     * Clear [activities] and [resources], resetting the results object for a new simulation.
     * Leaves [startTime] and [endTime] as-is.
     */
    fun MutableSimulationResults.clear(): Unit {
        activities.clear()
        resources.clear()
    }
}