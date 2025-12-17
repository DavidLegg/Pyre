package gov.nasa.jpl.pyre.general.results

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions
import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.collections.mapValues

object SimulationResultsOperations {
    fun MutableSimulationResults.toSimulationResults(): SimulationResults =
        SimulationResults(
            startTime,
            endTime,
            resources.mapValues { (_, r) -> r.toResourceResults() },
            activities.toMap(),
        )

    fun <T> MutableResourceResults<T>.toResourceResults(): ResourceResults<T> =
        ResourceResults(metadata, data.toList())

    fun MutableSimulationResults.toMutableSimulationResults(): MutableSimulationResults =
        MutableSimulationResults(
            startTime,
            endTime,
            resources.mapValuesTo(mutableMapOf()) { (_, r) -> r.toMutableResourceResults() },
            activities.toMutableMap(),
        )

    fun <T> MutableResourceResults<T>.toMutableResourceResults(): MutableResourceResults<T> =
        MutableResourceResults(metadata, data.toMutableList())

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
                    // The event coming straight out of the simulator will have a non-null activity.
                    // It's only when deserializing ActivityEvents that we lose the activity object reference.
                    // Additionally, ActivityEvents are cumulative - we only want to keep the last one for any given activity.
                    activities[requireNotNull(it.data.activity)] = it.data
                }
            } else {
                val resourceResults = MutableResourceResults(metadata)
                resources[metadata.channel] = resourceResults
                return resourceResults.data::add
            }
        }
    }
}