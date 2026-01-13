package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.call
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.*
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.channel
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScopeResult
import gov.nasa.jpl.pyre.foundation.tasks.coroutineTask
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.results.ResourceResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import java.util.TreeSet
import kotlin.reflect.KType
import kotlin.time.Instant

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
    private val simulationScope: SimulationScope
    private val model: M
    private val kernelSimulation: KernelIncrementalSimulator
    private val kernelActivityMap: MutableMap<GroundedActivity<*>, KernelActivity> = mutableMapOf()

    init {
        val incrementalReportHandler = object : BaseIncrementalChannelizedReportHandler() {
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
        var tempSimulationScope: SimulationScope? = null
        var tempModel: M? = null
        kernelSimulation = KernelIncrementalSimulator(
            plan.startTime,
            {
                val basicInitScope = contextOf<BasicInitScope>()
                // TODO: Coalesce this construction of an InitScope with that in PlanSimulation
                val initScope = object : InitScope {
                    override fun <T : Any> allocate(
                        name: Name,
                        value: T,
                        valueType: KType,
                        stepBy: (T, Duration) -> T,
                        mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
                    ): Cell<T> = basicInitScope.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)

                    override fun <T> spawn(
                        name: Name,
                        block: suspend context(TaskScope) () -> TaskScopeResult<T>
                    ) =
                        // When spawning a task, build a simulation scope which incorporates the task's Name
                        basicInitScope.spawn(name, context(subSimulationScope(contextName / name)) { coroutineTask(block) })

                    override fun <T> channel(
                        name: Name,
                        metadata: Map<String, Metadatum>,
                        valueType: KType
                    ): Channel<T> {
                        val reportType = ChannelData::class.withArg(valueType)
                        basicInitScope.report(ChannelMetadata<T>(
                            name,
                            metadata,
                            dataType = valueType,
                            reportType = reportType,
                            metadataType = ChannelMetadata::class.withArg(valueType),
                        ))
                        return Channel(name, reportType)
                    }

                    override fun <V> read(cell: Cell<V>): V = basicInitScope.read(cell)

                    override fun <T> report(channel: Channel<T>, value: T) =
                        basicInitScope.report(ChannelData(channel.name, now(), value))

                    override val contextName: Name? = null
                    override fun toString() = ""

                    override val simulationClock: Resource<Timer> = resource("simulation_clock", Timer(ZERO, 1))
                    override val simulationEpoch: Instant = plan.startTime

                    override val activities: Channel<ActivityEvent> = channel(Name("activities"))
                    override val stdout: Channel<String> = channel(Name("stdout"))
                    override val stderr: Channel<String> = channel(Name("stderr"))
                }
                tempSimulationScope = initScope
                tempModel = constructModel(initScope)
                plan.activities.map { activity ->
                    // Convert plan activities to kernel activities, and record those translations
                    activity.toKernelActivity(tempSimulationScope, tempModel).also { kernelActivityMap[activity] = it }
                }
            },
            incrementalReportHandler
        )
        simulationScope = checkNotNull(tempSimulationScope)
        model = checkNotNull(tempModel)
    }

    override fun run(edits: PlanEdits<M>) {
        plan = edits.applyTo(plan)
        kernelSimulation.run(KernelPlanEdits(
            edits.removals.map { activity ->
                // Look up the translated activities, and remove them as we do so
                requireNotNull(kernelActivityMap.remove(activity)) {
                    "Attempted to remove activity $activity which is not part of current plan"
                }
            },
            edits.additions.map { activity ->
                // Convert plan activities to kernel activities, and record those translations
                activity.toKernelActivity(simulationScope, model).also { kernelActivityMap[activity] = it }
            }
        ))
    }

    private fun GroundedActivity<M>.toKernelActivity(simulationScope: SimulationScope, model: M) = KernelActivity(
        Name("activities") / name,
        time,
        context (simulationScope) {
            coroutineTask(task {
                call(activity, model)
            })
        }
    )

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

