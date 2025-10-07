package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.ember.Cell
import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.ember.FinconCollector
import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.ember.InconProvider
import gov.nasa.jpl.pyre.ember.InconProvider.Companion.within
import gov.nasa.jpl.pyre.ember.InconProvidingContext.Companion.provide
import gov.nasa.jpl.pyre.ember.InternalLogger
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.ember.SimulationState
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.resources.timer.Timer
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScopeResult
import gov.nasa.jpl.pyre.spark.tasks.coroutineTask
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.isNotNull
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.isNull
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.spark.tasks.SparkScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
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
    private val sparkScope: SparkScope
    private val state: SimulationState
    private val activityResource: MutableDiscreteResource<GroundedActivity<M>?>

    private constructor(
        reportHandler: ReportHandler,
        simulationEpoch: Instant?,
        simulationStart: Duration?,
        inconProvider: InconProvider?,
        constructModel: context (InitScope) () -> M,
        modelClass: KType,
    ) {
        val simulationEpoch = requireNotNull(simulationEpoch ?: inconProvider?.within("simulation", "epoch")?.provide<Instant>())
        var start: Duration = requireNotNull(simulationStart ?: inconProvider?.within("simulation", "time")?.provide<Duration>())
        state = SimulationState(reportHandler)
        val initContext = state.initScope()
        val startupTasks: MutableList<Pair<String, suspend context (TaskScope) () -> Unit>> = mutableListOf()
        sparkScope = object : InitScope {
            override fun <T : Any, E> allocate(cell: Cell<T, E>): CellSet.CellHandle<T, E> =
                initContext.allocate(cell.copy(name = "/${cell.name}"))

            override fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>) =
                initContext.spawn("/$name", step)

            override fun <T : Any, E> read(cellHandle: CellSet.CellHandle<T, E>): T =
                initContext.read(cellHandle)

            override fun onStartup(name: String, block: suspend TaskScope.() -> Unit) {
                startupTasks += name to block
            }

            override val simulationClock = resource("simulation_clock", Timer(start, 1))
            override val simulationEpoch = simulationEpoch
            override fun toString() = ""
        }
        with (sparkScope) {
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
                InternalLogger.log { "Scheduling activity ${groundedActivity.name} @ ${groundedActivity.time}" }
                ActivityActions.spawn(groundedActivity, model)
            })

            // Now that the root tasks are in place, we can restore the simulation
            inconProvider?.let(state::restore)

            // Having restored the simulation, load in the startup tasks
            InternalLogger.block({ "Loading startup tasks" }) {
                for ((name, block) in startupTasks) {
                    InternalLogger.log { "Loading ${this@with}/$name" }
                    spawn(name, task { block() })
                }
            }
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

        inline fun <reified M> withIncon(
            noinline reportHandler: ReportHandler,
            inconProvider: InconProvider,
            noinline constructModel: InitScope.() -> M,
        ) = withIncon(
            reportHandler,
            inconProvider,
            constructModel,
            typeOf<M>(),
        )

        fun <M> withIncon(
            reportHandler: ReportHandler,
            inconProvider: InconProvider,
            constructModel: InitScope.() -> M,
            modelClass: KType,
        ) = PlanSimulation(
            reportHandler = reportHandler,
            inconProvider = inconProvider,
            simulationEpoch = null,
            simulationStart = null,
            constructModel = constructModel,
            modelClass = modelClass,
        )

        inline fun <reified M> withoutIncon(
            noinline reportHandler: ReportHandler,
            simulationEpoch: Instant,
            simulationStart: Instant,
            noinline constructModel: InitScope.() -> M,
        ) = withoutIncon(
            reportHandler,
            simulationEpoch,
            simulationStart,
            constructModel,
            typeOf<M>(),
        )

        fun <M> withoutIncon(
            reportHandler: ReportHandler,
            simulationEpoch: Instant,
            simulationStart: Instant,
            constructModel: InitScope.() -> M,
            modelClass: KType,
        ) = PlanSimulation(
            reportHandler = reportHandler,
            inconProvider = null,
            simulationEpoch = simulationEpoch,
            simulationStart = (simulationStart - simulationEpoch).toPyreDuration(),
            constructModel = constructModel,
            modelClass = modelClass,
        )
    }

    fun time() = sparkScope.simulationEpoch + state.time().toKotlinDuration()

    /**
     * Run the simulation until [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun runUntil(endTime: Instant) {
        val endDuration = (endTime - sparkScope.simulationEpoch).toPyreDuration()
        require(endDuration >= state.time()) {
            "Simulation time is currently ${sparkScope.simulationEpoch + state.time().toKotlinDuration()}, cannot step backwards to $endTime"
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
        val endDuration = (endTime - sparkScope.simulationEpoch).toPyreDuration()
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
        finconCollector.within("simulation", "epoch").report(sparkScope.simulationEpoch)
    }

    fun addActivities(activities: List<GroundedActivity<M>>) {
        InternalLogger.block({ "Loading ${activities.size} activities" }) {
            // TODO: Test this activityDirective trickery
            // TODO: If it works, consider formalizing it a bit more as a way to "safely" ingest info into the sim.
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
            state.addTask("activity loader", with (sparkScope) {
                coroutineTask {
                    if (activitiesToLoad.isEmpty()) {
                        activityLoaderActive = false
                        TaskScopeResult.Complete(Unit)
                    } else {
                        await(activityResource.isNull())
                        val a = activitiesToLoad.removeFirst()
                        InternalLogger.log { "Loading activity ${a.name} @ ${a.time}" }
                        activityResource.set(a)
                        TaskScopeResult.Restart()
                    }
                }
            })

            // Now, actually load the plan by cycling the simulation without advancing it.
            while (activityLoaderActive) state.stepTo(state.time())
        }
        InternalLogger.log { "Finished loading ${activities.size} activities" }
    }

    fun runPlan(plan: Plan<M>) {
        InternalLogger.block({ "Running plan" }) {
            val absoluteSimulationTime = sparkScope.simulationEpoch + state.time().toKotlinDuration()
            require(plan.startTime == absoluteSimulationTime) {
                "Cannot run plan starting at ${plan.startTime}. Simulation is at $absoluteSimulationTime"
            }
            addActivities(plan.activities)
            runUntil(plan.endTime)
        }
    }
}
