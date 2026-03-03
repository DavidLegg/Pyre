package gov.nasa.jpl.pyre.foundation

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions
import gov.nasa.jpl.pyre.foundation.plans.ActivityTaskCheckpoint
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.plans.float
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.kernel.KernelSimulator
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.coroutineTask
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.spawn
import gov.nasa.jpl.pyre.kernel.KernelCheckpoint
import gov.nasa.jpl.pyre.kernel.KernelTaskCheckpoint
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.kernel.tasks.PureTaskStep
import kotlin.collections.iterator
import kotlin.time.Instant

/**
 * Factors a simulation into a model and activities.
 *
 * The model is constructed irrespective of the plan, and defines the world the activities interact with.
 *
 * Activities are atomic units of simulation behavior, which may interact directly with the model but not each other.
 * Activities are listed in a plan, indicating what activity to perform and when to perform it.
 * A plan can be loaded into a running simulation, augmenting its behavior with additional activities.
 *
 * A standard workflow involves
 * 1. restoring a simulation from a fincon (checkpoint) taken at the end of the last planning cycle,
 * 2. loading a plan with the next cycle's activities,
 * 3. simulating until the end of this cycle, and
 * 4. cutting a fincon (checkpoint) in preparation for the next cycle.
 */
class Simulator<M : Any>(
    reportHandler: ChannelizedReportHandler,
    startTime: Instant? = null,
    incon: Checkpoint<M>? = null,
    constructModel: context (InitScope) () -> M,
) {
    private lateinit var simulationScope: SimulationScope
    private lateinit var model: M
    private val kernelSimulator: KernelSimulator
    // TODO: Is there a way to know when we can safely remove entries from loadedActivities?
    private val loadedActivities: MutableMap<Name, GroundedActivity<M>> = mutableMapOf()

    init {
        // Load activities in from the incon
        incon?.activities?.forEach {
            val activityName = it.activity.kernelName()
            val priorActivity = loadedActivities.put(activityName, it.activity)
            require(priorActivity == null || priorActivity == it.activity) {
                "Malformed incon: Activity name $activityName references two different activities: $priorActivity and ${it.activity}"
            }
        }
        // Build a kernel checkpoint by combining daemon and activity tasks
        val kernelIncon = incon?.run {
            KernelCheckpoint(
                time,
                cells,
                daemons + activities.map { KernelTaskCheckpoint(it.name, it.activity.kernelName(), it.time, it.history) }
            )
        }
        kernelSimulator = KernelSimulator(
            reportHandler,
            incon = kernelIncon,
            startTime = startTime,
            initialize = {
                // Wrap it in a foundation-level init scope
                val initScope = InitScope(requireNotNull(incon?.time ?: startTime))
                // Use the foundation-level init scope to build the model
                model = constructModel(initScope)
                // Also squirrel away that init scope, as just a simulation scope, to be re-used later
                simulationScope = initScope
                // Finally, restart all the activities that we loaded from the incon.
                // This won't actually start these activities now; instead, it'll provide the root tasks necessary
                // to restore these activities to however they were when the incon was produced.
                for ((name, activity) in loadedActivities) {
                    // For maximum control, bypass the foundation-level init scope and directly start the kernel task.
                    spawn(name, activity.toPureTaskStep(name))
                }
            },
        )
    }

    fun time() = kernelSimulator.time()

    /**
     * Run the simulation until [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun runUntil(endTime: Instant) {
        require(endTime >= time()) {
            "Simulation time is currently ${time()}, cannot step backwards to $endTime"
        }
        while (time() < endTime) stepTo(endTime)
    }

    private var stepsWithoutAdvancingTime = 0

    /**
     * Advance the simulation by one step, no further than [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun stepTo(endTime: Instant) {
        val timeBeforeStep = time()
        kernelSimulator.stepTo(endTime)
        if (time() > timeBeforeStep) {
            stepsWithoutAdvancingTime = 0
        } else if (++stepsWithoutAdvancingTime > SIMULATION_STALL_LIMIT) {
            kernelSimulator.dump()
            throw IllegalStateException("Simulation has stalled at ${time()} after $stepsWithoutAdvancingTime iterations.")
        }
    }

    fun save(): Checkpoint<M> {
        val kernelCheckpoint = kernelSimulator.save()
        val daemons = mutableListOf<KernelTaskCheckpoint>()
        val activities = mutableListOf<ActivityTaskCheckpoint<M>>()
        for (taskCheckpoint in kernelCheckpoint.tasks) {
            // This task is, or is spawned by, an activity
            if (taskCheckpoint.root.asSequence().first() == "activity") {
                if (taskCheckpoint.history != null) {
                    // This task is still loaded in the simulator
                    activities += taskCheckpoint.run {
                        ActivityTaskCheckpoint(
                            time,
                            name,
                            loadedActivities.getValue(root),
                            history,
                        )
                    }
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

    fun addActivity(activity: GroundedActivity<M>) {
        val name = activity.kernelName()
        require(loadedActivities.put(name, activity) == null) {
            "$name is already loaded. All concurrently loaded activities must have unique names."
        }
        kernelSimulator.addTask(activity.time, name, activity.toPureTaskStep(name))
    }

    fun runPlan(plan: Plan<M>) {
        require(plan.startTime == time()) {
            "Cannot run plan starting at ${plan.startTime}. Simulation is at ${time()}"
        }
        plan.activities.forEach(this::addActivity)
        runUntil(plan.endTime)
    }

    /** The name of the root kernel task for this activity */
    private fun GroundedActivity<M>.kernelName(): Name = Name("activity") / name

    /** The kernel task that will run this activity under the given name */
    private fun GroundedActivity<M>.toPureTaskStep(kernelName: Name): PureTaskStep = context(simulationScope) {
        // Build a simple task which calls the activity and records when the activity is finished
        coroutineTask(task {
            // Call the "floating" version of the activity, to preserve everything but the time.
            // The timing is done directly when adding this to the simulator.
            ActivityActions.call(float(), model)
            // Record that we've unloaded the activity when we're done.
            loadedActivities.remove(kernelName)
        })
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
}
