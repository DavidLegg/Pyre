package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.kernel.FinconCollector
import gov.nasa.jpl.pyre.kernel.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvider
import gov.nasa.jpl.pyre.kernel.SimulationState
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.resources.timer.Timer
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScopeResult
import gov.nasa.jpl.pyre.foundation.tasks.coroutineTask
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.isNotNull
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.isNull
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.channel
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.provide
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * Factors a simulation into a model and activities.
 *
 * The model is constructed irrespective of the plan, and defines the world the activities interact with.
 *
 * Activities are atomic units of simulation behavior, which may interact directly with the model but not each other.
 * Activities are specified in a Plan as ActivityDirectives, indicating what activity to perform, and when to perform it.
 * A Plan can be loaded into a running simulation, augmenting its behavior with additional Activities.
 *
 * A standard workflow involves
 * 1. restoring a simulation from a fincon taken at the end of the last planning cycle,
 * 2. loading a plan with the next cycle's activities,
 * 3. simulating until the end of this cycle, and
 * 4. cutting a fincon in preparation for the next cycle.
 */
class PlanSimulation<M> {
    private val simulationScope: SimulationScope
    private val state: SimulationState
    private val activityResource: MutableDiscreteResource<GroundedActivity<M>?>

    constructor(
        reportHandler: ChannelizedReportHandler,
        start: Instant? = null,
        inconProvider: InconProvider? = null,
        constructModel: context (InitScope) () -> M,
        modelClass: KType,
    ) {
        val epoch: Instant
        val startDuration: Duration
        if (inconProvider == null) {
            epoch = requireNotNull(start) {
                "If inconProvider is null, start must be provided."
            }
            startDuration = Duration.ZERO
        } else {
            epoch = requireNotNull(inconProvider.provide<Instant>("simulation", "epoch")) {
                "Incon must provide simulation.epoch"
            }
            startDuration = requireNotNull(inconProvider.provide<Duration>("simulation", "time")) {
                "Incon must provide simulation.time"
            }
            val inconStart = epoch + startDuration.toKotlinDuration()
            require(start == null || start == inconStart) {
                "start time $start must be null or match incon start time $inconStart"
            }
        }

        state = SimulationState(reportHandler, inconProvider)
        simulationScope = object : InitScope {
            override fun <T : Any> allocate(
                name: Name,
                value: T,
                valueType: KType,
                stepBy: (T, Duration) -> T,
                mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
            ): Cell<T> = state.initScope.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)

            override fun <T> spawn(name: Name, block: suspend context (TaskScope) () -> TaskScopeResult<T>) =
                // When spawning a task, build a simulation scope which incorporates the task's Name
                state.initScope.spawn(name, context(subSimulationScope(contextName / name)) { coroutineTask(block) })

            override fun <T> read(cell: Cell<T>): T =
                state.initScope.read(cell)

            override fun <T> channel(name: Name, metadata: Map<String, ChannelReport.Metadatum>, valueType: KType): Channel<T> {
                val reportType = ChannelData::class.withArg(valueType)
                state.initScope.report(ChannelMetadata<T>(
                    name,
                    metadata,
                    dataType = valueType,
                    reportType = reportType,
                    metadataType = ChannelMetadata::class.withArg(valueType),
                ))
                return Channel(name, reportType)
            }

            override fun <T> report(channel: Channel<T>, value: T) =
                state.initScope.report(ChannelData(channel.name, now(), value))

            override val contextName: Name? = null
            override fun toString() = ""

            override val simulationClock = resource("simulation_clock", Timer(startDuration, 1))
            override val simulationEpoch = epoch

            override val activities = channel<ActivityEvent>(Name("activities"))
            override val stdout = channel<String>(Name("stdout"))
            override val stderr = channel<String>(Name("stderr"))
        }
        with (simulationScope) {
            // Construct the model itself
            val model = constructModel()

            // Construct the activity daemon
            // This reaction loop will build an activity whenever the directive resource is loaded.
            // Reacting to a resource like this plays nicely with the fincon - each iteration of the loop
            // is a function of the resource value (activity directive) read on that iteration.
            // If the activity is captured by a fincon, it will record the directive's serialization
            // in the task history, such that it can be re-launched when the simulation is restored.
            activityResource = discreteResource<GroundedActivity<M>?>("activity_to_schedule", null,
                GroundedActivity::class.withArg(modelClass).withNullability(true))
            spawn("activities", whenever(activityResource.isNotNull()) {
                val groundedActivity = requireNotNull(activityResource.getValue())
                activityResource.set(null)
                ActivityActions.spawn(groundedActivity, model)
            })
        }
    }

    companion object {
        /**
         * The maximum number of iterations the simulation may perform without advancing in time,
         * before it is declared "stalled" and aborted.
         *
         * This provides protection against some kinds of infinitely looping tasks.
         */
        var SIMULATION_STALL_LIMIT: Int = 100
    }

    fun time() = simulationScope.simulationEpoch + state.time().toKotlinDuration()

    /**
     * Run the simulation until [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun runUntil(endTime: Instant) {
        val endDuration = (endTime - simulationScope.simulationEpoch).toPyreDuration()
        require(endDuration >= state.time()) {
            "Simulation time is currently ${simulationScope.simulationEpoch + state.time().toKotlinDuration()}, cannot step backwards to $endTime"
        }
        while (state.time() < endDuration) stepTo(endTime)
    }

    private var stepsWithoutAdvancingTime = 0

    /**
     * Advance the simulation by one step, no further than [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun stepTo(endTime: Instant) {
        val endDuration = (endTime - simulationScope.simulationEpoch).toPyreDuration()
        val timeBeforeStep = state.time()
        state.stepTo(endDuration)
        if (state.time() > timeBeforeStep) {
            stepsWithoutAdvancingTime = 0
        } else if (++stepsWithoutAdvancingTime > SIMULATION_STALL_LIMIT) {
            state.dump()
            throw IllegalStateException("Simulation has stalled at ${state.time()} after $stepsWithoutAdvancingTime iterations.")
        }
    }

    fun save(finconCollector: FinconCollector) {
        state.save(finconCollector)
        finconCollector.within("simulation", "epoch").report(simulationScope.simulationEpoch)
    }

    fun addActivities(activities: List<GroundedActivity<M>>) {
        // TODO: Consider formalizing this as a way to "safely" ingest info into the sim.
        val activitiesToLoad = activities.toMutableList()
        var activityLoaderActive = true

        // The directive loader will iteratively pull directives off the queue
        // and set them in the activityDirectiveResource.
        // The activity launcher will react to this by constructing and launching the activity.
        // That nulls out the resource, allowing this task to load the next activity.

        // Note that because this task depends on state not captured in a cell, it is not "safe" for simulation.
        // However, because it works in conjunction with the activity launcher, it will always complete
        // before the simulation advances in time.
        // Combined with the loop below to exercise this task to completion, thereby unloading this unsafe task,
        // the simulation is always in a safe state to save/restore when this function returns.
        state.addEphemeralTask(Name("activity loader"), with (simulationScope) {
            coroutineTask {
                if (activitiesToLoad.isEmpty()) {
                    activityLoaderActive = false
                    TaskScopeResult.Complete(Unit)
                } else {
                    await(activityResource.isNull())
                    val a = activitiesToLoad.removeFirst()
                    activityResource.set(a)
                    TaskScopeResult.Restart()
                }
            }
        })

        // Now, actually load the plan by cycling the simulation without advancing it.
        while (activityLoaderActive) state.stepTo(state.time())
    }

    fun runPlan(plan: Plan<M>) {
        val absoluteSimulationTime = simulationScope.simulationEpoch + state.time().toKotlinDuration()
        require(plan.startTime == absoluteSimulationTime) {
            "Cannot run plan starting at ${plan.startTime}. Simulation is at $absoluteSimulationTime"
        }
        addActivities(plan.activities)
        runUntil(plan.endTime)
    }
}

inline fun <reified M> PlanSimulation(
    reportHandler: ChannelizedReportHandler,
    start: Instant? = null,
    inconProvider: InconProvider? = null,
    noinline constructModel: context (InitScope) () -> M,
) = PlanSimulation(reportHandler, start, inconProvider, constructModel, typeOf<M>())
