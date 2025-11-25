package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.utilities.andThen;
import gov.nasa.jpl.pyre.kernel.InconProvider
import gov.nasa.jpl.pyre.kernel.JsonConditions
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.assumeType
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import kotlinx.serialization.json.Json
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
    private val constructModel: InitScope.(C) -> M,
    private val modelClass: KType,
    private val jsonFormat: Json,
    incon: InconProvider?,
    /** Activities not yet part of the simulation */
    private val futureActivities: PriorityQueue<GroundedActivity<M>> = PriorityQueue(compareBy { it.time }),
    /** Activities which have been incorporated into the simulation. */
    private val pastActivities: MutableList<GroundedActivity<M>> = mutableListOf(),
    private val resources: MutableMap<String, MutableList<ChannelizedReport<*>>> = mutableMapOf(),
    private val activitySpans: MutableMap<Activity<*>, ActivityEvent> = mutableMapOf(),
) {
    private val reportHandler: ReportHandler = channels(
        "activities" to (assumeType<ActivityEvent>() andThen { (value, type) ->
            // The event coming straight out of the simulator will have a non-null activity.
            // It's only when deserializing ActivityEvents that we lose the activity object reference.
            // Additionally, ActivityEvents are cumulative - we only want to keep the last one for any given activity.
            activitySpans[requireNotNull(value.data.activity)] = value.data
        }),
        miscHandler = { value, type ->
            if (value is ChannelizedReport<*>) {
                resources.getOrPut(value.channel, ::mutableListOf) += value
            }
        }
    )
    private val simulation: PlanSimulation<M> = if (incon == null) {
        PlanSimulation.withoutIncon(
            reportHandler,
            requireNotNull(startTime),
            startTime,
            { constructModel(config) },
            modelClass,
        )
    } else {
        PlanSimulation.withIncon(
            reportHandler,
            incon,
            { constructModel(config) },
            modelClass,
        )
    }
    // Get the start time from the simulation, regardless of how the simulation was initialized, to keep the two in sync.
    private val startTime: Instant = simulation.time()

    fun time() = simulation.time()

    fun runUntil(endTime: Instant) {
        val startTime = time()
        // Inject only the activities that we're about to run.
        // This way, we can adjust the plan that's still in the future when we're done.
        val activitiesToRun = mutableListOf<GroundedActivity<M>>()
        while (futureActivities.peek()?.run { time < endTime } ?: false) {
            activitiesToRun += futureActivities.remove()
        }
        simulation.addActivities(activitiesToRun)
        pastActivities += activitiesToRun
        simulation.runUntil(endTime)
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
        while (activitySpans[activity.activity]?.end == null && simulation.time() < Instant.DISTANT_FUTURE) {
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
        return activitySpans[activity.activity]?.end ?: Instant.DISTANT_FUTURE
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

    fun plan() = Plan(startTime, time(), pastActivities + futureActivities)

    fun results() = SimulationResults(
        startTime,
        time(),
        resources.toMap(),
        activitySpans.toMap(),
    )

    fun fincon() = JsonConditions(jsonFormat).also(simulation::save)

    // Initialize a new simulation, configured with newConfig and this sim's fincon
    fun copy(newConfig: C = config): SchedulingSystem<M, C> = SchedulingSystem(
        startTime,
        newConfig,
        constructModel,
        modelClass,
        jsonFormat,
        fincon(),
        // Copy over all the other bookkeeping data
        futureActivities = PriorityQueue(futureActivities),
        pastActivities = pastActivities.toMutableList(),
        resources = resources.toMutableMap(),
        activitySpans = activitySpans.toMutableMap(),
    )

    companion object {
        fun <M, C> withoutIncon(
            startTime: Instant,
            config: C,
            constructModel: InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(startTime, config, constructModel, modelClass, jsonFormat, null)

        inline fun <reified M, C> withoutIncon(
            startTime: Instant,
            config: C,
            noinline constructModel: InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withoutIncon(startTime, config, constructModel, typeOf<M>(), jsonFormat)

        fun <M, C> withIncon(
            incon: InconProvider,
            config: C,
            constructModel: InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(null, config, constructModel, modelClass, jsonFormat, incon)

        inline fun <reified M, C> withIncon(
            incon: InconProvider,
            config: C,
            noinline constructModel: InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withIncon(incon, config, constructModel, typeOf<M>(), jsonFormat)
    }
}