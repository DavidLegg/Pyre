package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.toKernelTask
import gov.nasa.jpl.pyre.foundation.plans.ActivityTaskCheckpoint
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.general.results.ResourceResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.incremental.IncrementalSimulatorOperations.applyTo
import gov.nasa.jpl.pyre.incremental.SGNode.ReportNode
import gov.nasa.jpl.pyre.kernel.KernelTaskCheckpoint
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.tasks.KernelTask
import java.util.*
import kotlin.time.Instant

fun <M : Any> IncrementalSimulator(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    incon: Checkpoint<M>? = null,
): IncrementalSimulator<M> = IncrementalSimulatorImpl(constructModel, plan, incon)

/**
 * Implements [IncrementalSimulator] using an in-memory directed acyclic graph of the events that took place.
 */
class IncrementalSimulatorImpl<M>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    incon: Checkpoint<M>? = null,
) : IncrementalSimulator<M> {
    override var plan: Plan<M> = plan
        private set
    override val results: SimulationResults get() = SimulationResults(
        plan.startTime,
        plan.endTime,
        resourceResults.mapValues { (_, inc) -> inc.toResourceResults() },
        activityResults.map { it.content.data }
    )

    private val resourceResults: MutableMap<Name, MutableIncrementalResourceResults<*>> = mutableMapOf()
    private val activityResults: TreeSet<ReportNode<ChannelData<ActivityEvent>>> = TreeSet(compareBy { it.time })
    private val simulationScope: SimulationScope
    private val model: M
    private val kernelSimulator: KernelIncrementalSimulator
    private val kernelActivityMap: MutableMap<GroundedActivity<M>, KernelTask> = mutableMapOf()

    init {
        val incrementalReportHandler = object : BaseIncrementalChannelizedReportHandler() {
            override fun <T> constructChannel(metadata: ChannelMetadata<T>): IncrementalChannelHandler<T> =
                if (metadata.channel == Name("activities")) {
                    @Suppress("UNCHECKED_CAST")
                    object : IncrementalChannelHandler<ActivityEvent> {
                        override fun report(report: ReportNode<ChannelData<ActivityEvent>>) {
                            activityResults += report
                        }
                        override fun revoke(report: ReportNode<ChannelData<ActivityEvent>>) {
                            activityResults -= report
                        }
                    } as IncrementalChannelHandler<T>
                } else {
                    val thisResourceResults = MutableIncrementalResourceResults(metadata)
                    resourceResults[metadata.channel] = thisResourceResults
                    object : IncrementalChannelHandler<T> {
                        override fun report(report: ReportNode<ChannelData<T>>) {
                            thisResourceResults.data.add(report)
                        }
                        override fun revoke(report: ReportNode<ChannelData<T>>) {
                            thisResourceResults.data.remove(report)
                        }
                    }
                }
        }
        var tempSimulationScope: SimulationScope? = null
        var tempModel: M? = null
        kernelSimulator = KernelIncrementalSimulator(
            plan.startTime,
            plan.endTime,
            {
                val initScope = InitScope(plan.startTime)
                tempSimulationScope = initScope
                tempModel = constructModel(initScope)
                plan.activities.map { activity ->
                    context (tempSimulationScope) {
                        // Convert plan activities to kernel activities, and record those translations
                        activity.toKernelTask(tempModel).also { kernelActivityMap[activity] = it }
                    }
                }
            },
            incrementalReportHandler,
            incon?.let { TODO("incon handling") }
        )
        simulationScope = checkNotNull(tempSimulationScope)
        model = checkNotNull(tempModel)
    }

    override fun run(edits: PlanEdits<M>) {
        plan = edits.applyTo(plan)
        kernelSimulator.run(KernelPlanEdits(
            edits.removals.map { activity ->
                // Look up the translated activities, and remove them as we do so
                requireNotNull(kernelActivityMap.remove(activity)) {
                    "Attempted to remove activity $activity which is not part of current plan"
                }
            },
            edits.additions.map { activity ->
                context (simulationScope) {
                    // Convert plan activities to kernel activities, and record those translations
                    activity.toKernelTask(model).also { kernelActivityMap[activity] = it }
                }
            }
        ))
    }

    override fun save(time: Instant): Checkpoint<M> {
        val activityByKernelTaskName = kernelActivityMap.entries
            .associate { (activity, kernelTask) -> kernelTask.name to activity }
        val kernelCheckpoint = kernelSimulator.save(time)
        val daemons = mutableListOf<KernelTaskCheckpoint>()
        val activities = mutableListOf<ActivityTaskCheckpoint<M>>()
        for (taskCheckpoint in kernelCheckpoint.tasks) {
            val rootActivity = activityByKernelTaskName[taskCheckpoint.root]
            if (rootActivity != null) {
                // This task is, or is spawned by, an activity
                if (taskCheckpoint.history != null) {
                    // This task is still loaded in the simulator
                    activities += ActivityTaskCheckpoint(
                        taskCheckpoint.time,
                        taskCheckpoint.name,
                        rootActivity,
                        taskCheckpoint.history,
                    )
                }
                // else: activity is completed, throw it away.
                // Unlike daemons, which get restarted if we throw away their checkpoint,
                // a completed activity can just be forgotten.
            } else {
                daemons += taskCheckpoint
            }
        }
        return Checkpoint(kernelCheckpoint.time, kernelCheckpoint.cells, daemons, activities)
    }

    private data class MutableIncrementalResourceResults<T>(
        val metadata: ChannelMetadata<T>,
        // By using a TreeSet and sorting by report time, we maintain a fully-ordered list of reports on each channel,
        // but insertions and deletions remain O(log n) in the number of reports on this channel.
        val data: TreeSet<ReportNode<ChannelData<T>>> = TreeSet(compareBy { it.time }),
    ) {
        fun toResourceResults(): ResourceResults<T> =
            ResourceResults(metadata, data.map { it.content })
    }
}
