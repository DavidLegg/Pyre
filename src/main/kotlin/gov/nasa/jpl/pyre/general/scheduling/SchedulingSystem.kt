package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.kernel.InconProvider
import gov.nasa.jpl.pyre.kernel.JsonConditions
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.decrement
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delayUntil
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.Profile
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asProfile
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.general.results.ProfileOperations.computeProfile
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toMutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults
import gov.nasa.jpl.pyre.general.scheduling.SchedulingSystem.SchedulingReplayScope.Companion.replay
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.name
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.kernel.Name
import java.util.PriorityQueue
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.require
import kotlin.time.Instant

/**
 * Provides foundational scheduling capabilities.
 * Contains a nominal plan and an in-progress simulation.
 *
 * Activities may be added to the plan, and the simulation may be advanced in time.
 * These operations may be freely interleaved, to support incremental approaches to activity planning.
 * The current nominal plan and simulation results are available at all times.
 *
 * Additionally, provides [SchedulingSystem.copy] to duplicate the entire scheduling system.
 * This includes building a functionally identical but independent simulation, which allows for backtracking.
 * For example, it's common to advance the simulation to a time t1, make a copy, schedule an activity in that copy,
 * and advance the copy to run that newly-scheduled activity.
 * If that's unsatisfactory, we throw out the copy, make a new copy (which will be back at t1 again), and repeat.
 *
 * For more complex scheduling cases, see [SchedulingAlgorithms], which builds on the foundations provided here.
 *
 * @see SchedulingAlgorithms
 */
class SchedulingSystem<M, C> private constructor(
    startTime: Instant?,
    val config: C,
    private val constructModel: context (InitScope) (C) -> M,
    private val modelClass: KType,
    incon: InconProvider?,
    /** Activities not yet part of the simulation */
    private val futureActivities: PriorityQueue<GroundedActivity<M>>,
    /** Activities which have been incorporated into the simulation. */
    private val pastActivities: MutableList<GroundedActivity<M>>,
    private val results: MutableSimulationResults,
) {
    private var model: M? = null
    private val simulation: PlanSimulation<M> = PlanSimulation(
        results.reportHandler(),
        startTime,
        incon,
        { constructModel(config).also { model = it } },
        modelClass,
    )
    init {
        // Get the start time from the simulation, regardless of how the simulation was initialized, to keep the two in sync.
        results.startTime = simulation.time()
    }
    // Defer to the results object for the start time, so we don't duplicate and potentially disagree on start time.
    val startTime: Instant get() = results.startTime

    constructor(
        startTime: Instant?,
        config: C,
        constructModel: context (InitScope) (C) -> M,
        modelClass: KType,
        incon: InconProvider?,
    ) : this(
        startTime,
        config,
        constructModel,
        modelClass,
        incon,
        PriorityQueue(compareBy { it.time }),
        mutableListOf(),
        MutableSimulationResults(),
    )

    fun time() = simulation.time()

    fun runUntil(endTime: Instant) {
        // Inject only the activities that we're about to run.
        // This way, we can adjust the plan that's still in the future when we're done.
        val activitiesToRun = mutableListOf<GroundedActivity<M>>()
        while (futureActivities.peek()?.run { time < endTime } ?: false) {
            activitiesToRun += futureActivities.remove()
        }
        simulation.addActivities(activitiesToRun)
        pastActivities += activitiesToRun
        simulation.runUntil(endTime)
        results.endTime = time()
    }

    /**
     * Run until [activity] completes, and return that time.
     *
     * If [activity] is not part of this scheduler's plan already, adds it to the plan.
     * If [activity] doesn't complete, returns [Instant.DISTANT_FUTURE] instead.
     * In that case, also advances the simulation to [Instant.DISTANT_FUTURE].
     */
    fun runUntil(activity: GroundedActivity<M>): Instant {
        addActivity(activity)
        // Do a regular run until the activity begins
        runUntil(activity.time)
        // So long as we haven't recorded the end of this activity, keep asking the simulation to step forward.
        while (results.activities[activity.activity]?.end == null && simulation.time() < Instant.DISTANT_FUTURE) {
            // First, look for any activities we need to inject right now:
            val activitiesToRun = mutableListOf<GroundedActivity<M>>()
            while (futureActivities.peek()?.run { time == simulation.time() } ?: false) {
                activitiesToRun += futureActivities.remove()
            }
            // Inject them into the simulation
            simulation.addActivities(activitiesToRun)
            pastActivities += activitiesToRun
            // Look for when we would next need to inject activities, which is the latest we can safely advance:
            val nextActivityTime = futureActivities.minOfOrNull { it.time } ?: Instant.DISTANT_FUTURE
            // Finally, step the simulation to (at most) that time
            // Since the activity must run a task step to end, the simulation will not advance past the activity end.
            simulation.stepTo(nextActivityTime)
        }
        results.endTime = time()
        return results.activities[activity.activity]?.end ?: Instant.DISTANT_FUTURE
    }

    fun addActivity(activity: GroundedActivity<M>) {
        require(activity.time >= time()) {
            "System is at ${time()}, cannot add activity in the past at ${activity.time}"
        }
        futureActivities += activity
    }
    fun addActivities(activities: Collection<GroundedActivity<M>>) = activities.forEach(::addActivity)
    fun addPlan(plan: Plan<M>) = addActivities(plan.activities)

    operator fun plusAssign(activity: Activity<M>) = addActivity(GroundedActivity(time(), activity))
    operator fun plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun plusAssign(activities: Collection<GroundedActivity<M>>) = addActivities(activities)
    operator fun plusAssign(plan: Plan<M>) = addPlan(plan)

    fun plan() = Plan(results.startTime, time(), pastActivities + futureActivities)

    fun results(): SimulationResults {
        results.endTime = simulation.time()
        return results.toSimulationResults()
    }

    /** Get a single profile, selected by the resource object itself. */
    fun <D : Dynamics<*, D>> profile(selector: M.() -> Resource<D>): Profile<D> {
        val name = model!!.selector().name
        return results.resources.getValue(name).data.asProfile(name, time())
    }

    /** Get the last value of a registered resource, selected by the resource object itself. */
    fun <V, D : Dynamics<V, D>> lastValue(selector: M.() -> Resource<D>): V {
        val name = model!!.selector().name
        @Suppress("UNCHECKED_CAST")
        val report = results.resources.getValue(name).data.last() as ChannelData<D>
        return report.data.step((time() - report.time).toPyreDuration()).value()
    }

    /** Get the last value of a registered resource, selected by the resource object itself. */
    fun <V, D : Dynamics<V, D>> lastQuantity(selector: M.() -> UnitAware<Resource<D>>): UnitAware<V> {
        // TODO: Use channel metadata to do unit conversion rather than resource.unit
        val resourceResults = results.resources.getValue(model!!.selector().name)
        @Suppress("UNCHECKED_CAST")
        val report = resourceResults.data.last() as ChannelData<D>
        val unit = resourceResults.metadata.metadata.getValue("unit").value as Unit
        return UnitAware(
            report.data.step((time() - report.time).toPyreDuration()).value(),
            unit,
        )
    }

    interface SchedulingReplayScope {
        context (_: InitScope)
        fun <D : Dynamics<*, D>> replay(name: Name): Resource<D>

        context (_: InitScope)
        fun countActivities(predicate: (ActivityEvent) -> Boolean = { true }): IntResource

        companion object {
            context (_: InitScope, scope: SchedulingReplayScope)
            fun <D : Dynamics<*, D>> replay(name: Name): Resource<D> = scope.replay(name)

            context (_: InitScope, scope: SchedulingReplayScope)
            fun <D : Dynamics<*, D>> Resource<D>.replay(): Resource<D> = replay(name)

            context (_: InitScope, scope: SchedulingReplayScope)
            fun countActivities(predicate: (ActivityEvent) -> Boolean = { true }): IntResource = scope.countActivities(predicate)
        }
    }

    fun <V, D : Dynamics<V, D>> compute(
        start: Instant = startTime,
        end: Instant = time(),
        derivation: context (InitScope, SchedulingReplayScope) M.() -> Resource<D>,
        dynamicsType: KType
    ): Profile<D> {
        val scope = object : SchedulingReplayScope {
            context (_: InitScope)
            override fun <D : Dynamics<*, D>> replay(name: Name): Resource<D> =
                this@SchedulingSystem.results.resources.getValue(name)
                    .data
                    .asProfile<D>(name, this@SchedulingSystem.time())
                    .asResource()

            // TODO: Refactor this to reduce duplicated code
            context(_: InitScope)
            override fun countActivities(predicate: (ActivityEvent) -> Boolean): IntResource {
                val counter = discreteResource("counted activities", 0)
                results.activities.values
                    // Restrict to activities that haven't already ended and satisfy the predicate
                    .filter { (it.end?.let { it >= now() } ?: true) && predicate(it) }
                    .forEach {
                        // If the activity satisfies predicate, spawn a task for it
                        spawn(it.name, task {
                            // Increment when the activity starts
                            delayUntil(it.start)
                            counter.increment()
                            if (it.end != null) {
                                // Decrement when it ends, if it ends
                                delayUntil(it.end)
                                counter.decrement()
                            }
                        })
                    }
                return counter
            }
        }
        return computeProfile(
            start,
            end,
            { context(scope) { model!!.derivation() } },
            dynamicsType,
        )
    }

    fun fincon() = JsonConditions().also(simulation::save)

    // Initialize a new simulation, configured with newConfig and this sim's fincon
    fun copy(newConfig: C = config): SchedulingSystem<M, C> = SchedulingSystem(
        time(),
        newConfig,
        constructModel,
        modelClass,
        fincon(),
        // Copy over all the other bookkeeping data
        futureActivities = PriorityQueue(futureActivities),
        pastActivities = pastActivities.toMutableList(),
        results = results.toMutableSimulationResults(),
    )

    companion object {
        /**
         * Compute a resource derived from the results collected by this [SchedulingSystem] so far.
         *
         * Access a registered resource through the model and call [replay] to use it in the derivation.
         */
        inline fun <V, reified D: Dynamics<V, D>, M> SchedulingSystem<M, *>.compute(
            start: Instant = startTime,
            end: Instant = time(),
            noinline derivation: context (InitScope, SchedulingReplayScope) M.() -> Resource<D>,
        ) = compute(start, end, derivation, typeOf<D>())
    }
}

inline fun <reified M, C> SchedulingSystem(
    config: C,
    noinline constructModel: context (InitScope) (C) -> M,
    startTime: Instant? = null,
    incon: InconProvider? = null,
) = SchedulingSystem(startTime, config, constructModel, typeOf<M>(), incon)
