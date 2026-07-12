package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
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
class SchedulingSystem<M : Any> private constructor(
    startTime: Instant?,
    private val constructModel: context (InitScope) () -> M,
    incon: Checkpoint<M>?,
    /** Activities not yet part of the simulation */
    private val futureActivities: PriorityQueue<GroundedActivity<M>>,
    /** Activities which have been incorporated into the simulation. */
    private val pastActivities: MutableList<GroundedActivity<M>>,
    private val results: MutableSimulationResults,
) {
    private var model: M? = null
    private val simulation: Simulator<M> = Simulator(
        results.reportHandler(),
        startTime,
        incon,
        { constructModel().also { model = it } },
    )
    init {
        // Get the start time from the simulation, regardless of how the simulation was initialized, to keep the two in sync.
        results.startTime = simulation.time()
    }
    // Defer to the results object for the start time, so we don't duplicate and potentially disagree on start time.
    val startTime: Instant get() = results.startTime

    constructor(
        constructModel: context (InitScope) () -> M,
        startTime: Instant? = null,
        incon: Checkpoint<M>? = null,
    ) : this(
        startTime,
        constructModel,
        incon,
        PriorityQueue(compareBy { it.time }),
        mutableListOf(),
        MutableSimulationResults(),
    )

    fun time() = simulation.time()

    fun runUntil(endTime: Instant) {
        // Inject only the activities that we're about to run.
        // This way, we can adjust the plan that's still in the future when we're done.
        while (futureActivities.peek()?.run { time < endTime } ?: false) {
            val activity = futureActivities.remove()
            simulation.addActivity(activity)
            pastActivities += activity
        }
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
        // Keep track of activity events we don't need to check
        var numberOfActivitiesSeen = results.activities.size
        // So long as we haven't recorded the end of this activity, keep asking the simulation to step forward.
        while (
            results.activities.takeLast(results.activities.size - numberOfActivitiesSeen)
                .none { it.activity === activity.activity && it.end != null }
            // Add a fallback condition to stop if the simulation hits the end of time without completing the activity
            && simulation.time() < Instant.DISTANT_FUTURE
        ) {
            numberOfActivitiesSeen = results.activities.size
            // First, look for any activities we need to inject right now:
            while (futureActivities.peek()?.run { time == simulation.time() } ?: false) {
                simulation.addActivity(activity)
                pastActivities += activity
            }
            // Look for when we would next need to inject activities, which is the latest we can safely advance:
            val nextActivityTime = futureActivities.minOfOrNull { it.time } ?: Instant.DISTANT_FUTURE
            // Finally, step the simulation to (at most) that time
            // Since the activity must run a task step to end, the simulation will not advance past the activity end.
            simulation.stepTo(nextActivityTime)
        }
        results.endTime = time()
        return results.activities.last { it.activity === activity.activity }.end ?: Instant.DISTANT_FUTURE
    }

    fun addActivity(activity: GroundedActivity<M>) {
        require(activity.time >= time()) {
            "System is at ${time()}, cannot add activity in the past at ${activity.time}"
        }
        futureActivities += activity
    }

    fun plan() = Plan(results.startTime, time(), pastActivities + futureActivities)

    // TODO: What's the right way to expose the results?
    //   I want to expose them for reading, but without needing to copy the results explicitly.
    //   I also want to expose the model enough that users can access a resource in it, to call "resource.replay()"
    //   in an init context (e.g. for compute() or stubbing sub-models or such).
    //   Note that doing that requires a context param that knows which sim results to use...
    //   So either the sim results are the context param, or this scheduling system can produce a context param
    //   that points back to this scheduling system...

    fun save() = simulation.save()

    fun copy(): SchedulingSystem<M> = SchedulingSystem(
        time(),
        constructModel,
        save(),
        // Copy over all the other bookkeeping data
        futureActivities = PriorityQueue(futureActivities),
        pastActivities = pastActivities.toMutableList(),
        results = results.toMutableSimulationResults(),
    )
}
