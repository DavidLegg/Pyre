package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.*
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.ResourceResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.kernel.Name
import java.util.TreeSet
import kotlin.reflect.KType

/**
 * Implements [IncrementalPlanSimulation] using an in-memory directed acyclic graph of the events that took place.
 */
class GraphIncrementalPlanSimulation<M>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    modelClass: KType,
) : IncrementalPlanSimulation<M> {
    override var plan: Plan<M> = plan
        private set
    override val results: SimulationResults get() = SimulationResults(
        plan.startTime,
        plan.endTime,
        resourceResults.mapValues { (_, inc) -> inc.toResourceResults() },
        activityResults.mapValues { (_, progress) ->
            checkNotNull(progress.end ?: progress.start).content.data
        }
    )

    private val resourceResults: MutableMap<Name, MutableIncrementalResourceResults<*>> = mutableMapOf()
    private val activityResults: MutableMap<Activity<*>, MutableActivityProgress> = mutableMapOf()
    private val incrementalReportHandler = object : BaseIncrementalChannelizedReportHandler() {
        override fun <T> constructChannel(metadata: ChannelMetadata<T>): IncrementalChannelHandler<T> =
            if (metadata.channel == Name("activities")) {
                @Suppress("UNCHECKED_CAST")
                object : IncrementalChannelHandler<ActivityEvent> {
                    override fun report(report: IncrementalReport<ChannelData<ActivityEvent>>) {
                        // The event coming straight out of the simulator will have a non-null activity.
                        // It's only when deserializing ActivityEvents that we lose the activity object reference.
                        // Additionally, ActivityEvents are cumulative - we only want to keep the last one for any given activity.
                        val progress = activityResults.computeIfAbsent(
                            requireNotNull(report.content.data.activity)
                        ) { MutableActivityProgress() }
                        if (report.content.data.end == null) {
                            // This is a start event
                            progress.start = report
                        } else {
                            // This is an end event
                            progress.end = report
                        }
                    }

                    override fun revoke(report: IncrementalReport<ChannelData<ActivityEvent>>) {
                        // The event coming straight out of the simulator will have a non-null activity.
                        // It's only when deserializing ActivityEvents that we lose the activity object reference.
                        activityResults.compute(requireNotNull(report.content.data.activity)) { _, progress ->
                            checkNotNull(progress) {
                                "Internal error! Attempted to revoke an activity event with no matching activity entry."
                            }
                            // Clear whichever half of the entry was revoked
                            when (report) {
                                progress.start -> progress.copy(start = null)
                                progress.end -> progress.copy(end = null)
                                else -> throw IllegalArgumentException("Internal error!")
                            }.takeUnless {
                                // Delete the entry entirely if both start and end were revoked.
                                it.start == null && it.end == null
                            }
                        }
                    }
                } as IncrementalChannelHandler<T>
            } else {
                val thisResourceResults = MutableIncrementalResourceResults(metadata)
                resourceResults[metadata.channel] = thisResourceResults
                object : IncrementalChannelHandler<T> {
                    override fun report(report: IncrementalReport<ChannelData<T>>) {
                        thisResourceResults.data.add(report)
                    }

                    override fun revoke(report: IncrementalReport<ChannelData<T>>) {
                        thisResourceResults.data.remove(report)
                    }
                }
            }
    }
    private val model: M
    private val kernelSimulation: KernelIncrementalSimulator = KernelIncrementalSimulator(
        { model = constructModel(TODO()) },
        TODO("plan"),
        incrementalReportHandler
    )

    override fun run(edits: PlanEdits<M>) {
        TODO("Not yet implemented")
    }

    private data class MutableIncrementalResourceResults<T>(
        val metadata: ChannelMetadata<T>,
        // By using a TreeSet and sorting by report time, we maintain a fully-ordered list of reports on each channel,
        // but insertions and deletions remain O(log n) in the number of reports on this channel.
        val data: TreeSet<IncrementalReport<ChannelData<T>>> = TreeSet(compareBy { it.time }),
    ) {
        fun toResourceResults(): ResourceResults<T> =
            ResourceResults(metadata, data.map { it.content })
    }

    private data class MutableActivityProgress(
        // In order to keep the exact incremental report object, we have to store the full activity event for both start and end
        // This is a bit wasteful, as the end event subsumes the start event by design.
        var start: IncrementalReport<ChannelData<ActivityEvent>>? = null,
        var end: IncrementalReport<ChannelData<ActivityEvent>>? = null,
    )
}

