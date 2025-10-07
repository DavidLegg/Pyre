package gov.nasa.jpl.pyre.flame.scheduling

import gov.nasa.jpl.pyre.coals.andThen;
import gov.nasa.jpl.pyre.ember.InconProvider
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Plan
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.assumeType
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import kotlinx.serialization.json.Json
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
    private val config: C,
    private val constructModel: suspend InitScope.(C) -> M,
    private val modelClass: KType,
    private val jsonFormat: Json,
    incon: InconProvider?,
) {
    private val nominalActivities: MutableList<GroundedActivity<M>> = mutableListOf()
    private val injectedActivities: MutableSet<GroundedActivity<M>> = mutableSetOf()
    private val resources: MutableMap<String, MutableList<ChannelizedReport<*>>> = mutableMapOf()
    private val activitySpans: MutableMap<Activity<*>, ActivityEvent> = mutableMapOf()
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
        val activitiesToRun = nominalActivities
            .filter { it !in injectedActivities && it.time < endTime }
            .sortedBy { it.time }
        simulation.addActivities(activitiesToRun)
        injectedActivities += activitiesToRun
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
        if (activity !in nominalActivities) {
            this += activity
        }
        // Do a regular run until the activity begins
        runUntil(activity.time)
        // So long as we haven't recorded the end of this activity, keep asking the simulation to step forward.
        while (activitySpans[activity.activity]?.end == null && simulation.time() < Instant.DISTANT_FUTURE) {
            // First, look for any activities we need to inject right now:
            val activitiesToRun = nominalActivities.filter { it !in injectedActivities && it.time == simulation.time() }
            // Inject them into the simulation
            simulation.addActivities(activitiesToRun)
            injectedActivities += activitiesToRun
            // Look for when we would next need to inject activities, which is the latest we can safely advance:
            val nextActivityTime = nominalActivities.filter { it !in injectedActivities }.minOfOrNull { it.time } ?: Instant.DISTANT_FUTURE
            // Finally, step the simulation to (at most) that time
            simulation.stepTo(nextActivityTime)
        }
        return activitySpans[activity.activity]?.end ?: Instant.DISTANT_FUTURE
    }

    fun addActivity(activity: GroundedActivity<M>) {
        require(activity.time >= time()) {
            "System is at ${time()}, cannot add activity in the past at ${activity.time}"
        }
        nominalActivities += activity
    }
    fun addActivities(activities: Collection<GroundedActivity<M>>) = activities.forEach(::addActivity)
    fun addPlan(plan: Plan<M>) = addActivities(plan.activities)

    operator fun plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun plusAssign(activities: Collection<GroundedActivity<M>>) = addActivities(activities)
    operator fun plusAssign(plan: Plan<M>) = addPlan(plan)

    fun plan() = Plan(startTime, time(), nominalActivities.sortedBy { it.time })

    fun results() = SimulationResults(
        startTime,
        time(),
        resources.toMap(),
        activitySpans.toMap(),
    )

    fun copy(newConfig: C = config): SchedulingSystem<M, C> {
        // Collect the state of this simulation
        val incon = JsonConditions(jsonFormat).also(simulation::save)
        // Use that to initialize a new simulation, configured with newConfig as well.
        val result = SchedulingSystem(
            startTime,
            newConfig,
            constructModel,
            modelClass,
            jsonFormat,
            incon,
        )
        // Copy over all the other bookkeeping data
        // TODO: Consider using a reference back to these data instead of copying all of them
        result.nominalActivities.addAll(this.nominalActivities)
        result.injectedActivities.addAll(this.injectedActivities)
        result.resources.putAll(this.resources)
        result.activitySpans.putAll(this.activitySpans)
        return result
    }

    companion object {
        fun <M, C> withoutIncon(
            startTime: Instant,
            config: C,
            constructModel: suspend InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(startTime, config, constructModel, modelClass, jsonFormat, null)

        inline fun <reified M, C> withoutIncon(
            startTime: Instant,
            config: C,
            noinline constructModel: suspend InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withoutIncon(startTime, config, constructModel, typeOf<M>(), jsonFormat)

        fun <M, C> withIncon(
            incon: InconProvider,
            config: C,
            constructModel: suspend InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(null, config, constructModel, modelClass, jsonFormat, incon)

        inline fun <reified M, C> withIncon(
            incon: InconProvider,
            config: C,
            noinline constructModel: suspend InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withIncon(incon, config, constructModel, typeOf<M>(), jsonFormat)
    }
}