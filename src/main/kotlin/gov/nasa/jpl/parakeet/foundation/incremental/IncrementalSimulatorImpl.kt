package gov.nasa.jpl.parakeet.foundation.incremental

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.kernelTaskName
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
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.applyTo
import gov.nasa.jpl.pyre.kernel.incremental.IncSimNode.ReportNode
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.spawn
import gov.nasa.jpl.pyre.kernel.KernelCheckpoint
import gov.nasa.jpl.pyre.kernel.KernelTaskCheckpoint
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.incremental.KernelIncrementalSimulator
import gov.nasa.jpl.pyre.kernel.incremental.KernelPlanEdits
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
    private val _results = MutableIncrementalSimulationResults(plan.startTime, plan.endTime)
    override val results: SimulationResults get() = _results

    private val simulationScope: SimulationScope
    private val model: M
    private val kernelSimulator: KernelIncrementalSimulator
    private val kernelActivityMap: MutableMap<GroundedActivity<M>, KernelTask> = mutableMapOf()
    // Incon activities have to be tracked separate from "normal" activities.
    // Since they are in general partially-run, they can't in general be removed from the sim.
    // Further, there's a dependency-ordering problem with them, where we can't construct the KernelTask for them.
    // We use them to build the kernel incon, which we need to have to build the model, which we need to build the KernelTask.
    private val inconActivities: MutableMap<Name, GroundedActivity<M>> = mutableMapOf()

    init {
        val incrementalReportHandler = object : BaseIncrementalChannelizedReportHandler() {
            override fun <T> constructChannel(metadata: ChannelMetadata<T>): IncrementalChannelHandler<T> =
                if (metadata.channel == Name("activities")) {
                    @Suppress("UNCHECKED_CAST")
                    object : IncrementalChannelHandler<ActivityEvent> {
                        override fun report(report: ReportNode<ChannelData<ActivityEvent>>) {
                            _results.incrementalActivities += report
                        }
                        override fun revoke(report: ReportNode<ChannelData<ActivityEvent>>) {
                            _results.incrementalActivities -= report
                        }
                    } as IncrementalChannelHandler<T>
                } else {
                    val thisResourceResults = MutableIncrementalResourceResults(metadata)
                    _results.resources[metadata.channel] = thisResourceResults
                    object : IncrementalChannelHandler<T> {
                        override fun report(report: ReportNode<ChannelData<T>>) {
                            thisResourceResults.incrementalData.add(report)
                        }
                        override fun revoke(report: ReportNode<ChannelData<T>>) {
                            thisResourceResults.incrementalData.remove(report)
                        }
                    }
                }
        }
        var tempSimulationScope: SimulationScope? = null
        var tempModel: M? = null
        // Build a kernel checkpoint by combining daemon and activity tasks.
        val kernelIncon = incon?.run {
            val kernelTasks = daemons.toMutableList()
            for (activityCheckpoint in activities) {
                with (activityCheckpoint) {
                    // Load each activity in the incon, and build a kernel task checkpoint for it.
                    val rootTaskName = kernelTaskName(activity.name)
                    inconActivities.put(rootTaskName, activity).also {
                        require(it == null || it == activity) {
                            "Malformed incon: Activity name $rootTaskName references two different activities: $it and $activity"
                        }
                    }
                    kernelTasks += KernelTaskCheckpoint(name, rootTaskName, time, history)
                }
            }
            KernelCheckpoint(time, cells, kernelTasks)
        }
        kernelSimulator = KernelIncrementalSimulator(
            plan.startTime,
            plan.endTime,
            {
                val initScope = InitScope(plan.startTime)
                tempSimulationScope = initScope
                tempModel = constructModel(initScope)
                // Finally, restart all the activities that we loaded from the incon.
                // This won't actually start these activities now; instead, it'll provide the root tasks necessary
                // to restore these activities to however they were when the incon was produced.
                for (activity in inconActivities.values) {
                    // For maximum control, bypass the foundation-level init scope and directly start the kernel task.
                    val task = context (tempSimulationScope) { activity.toKernelTask(tempModel) }
                    spawn(task.name, task.step)
                }
                plan.activities.map { activity ->
                    context (tempSimulationScope) {
                        // Convert plan activities to kernel activities, and record those translations
                        activity.toKernelTask(tempModel).also { kernelActivityMap[activity] = it }
                    }
                }
            },
            incrementalReportHandler,
            kernelIncon,
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
        val activityByKernelTaskName = inconActivities + kernelActivityMap.entries
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

    private data class MutableIncrementalSimulationResults(
        override var startTime: Instant,
        override var endTime: Instant,
        override val resources: MutableMap<Name, MutableIncrementalResourceResults<*>> = mutableMapOf(),
        val incrementalActivities: TreeSet<ReportNode<ChannelData<ActivityEvent>>> = TreeSet(compareBy { it.time })
    ) : SimulationResults {
        override val activities: List<ActivityEvent>
            get() = incrementalActivities.map { it.content.data }
    }

    private data class MutableIncrementalResourceResults<T>(
        override val metadata: ChannelMetadata<T>,
        // By using a TreeSet and sorting by report time, we maintain a fully-ordered list of reports on each channel,
        // but insertions and deletions remain O(log n) in the number of reports on this channel.
        val incrementalData: TreeSet<ReportNode<ChannelData<T>>> = TreeSet(compareBy { it.time }),
    ) : ResourceResults<T> {
        override val data: List<ChannelData<T>>
            get() = incrementalData.map { it.content }
    }
}
