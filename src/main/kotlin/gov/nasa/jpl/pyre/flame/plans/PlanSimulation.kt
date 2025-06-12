package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.FinconCollector
import gov.nasa.jpl.pyre.ember.InconProvider
import gov.nasa.jpl.pyre.ember.InternalLogger
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.Simulation
import gov.nasa.jpl.pyre.ember.SimulationState
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.nullable
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.resources.timer.Timer
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.TaskScopeResult
import gov.nasa.jpl.pyre.spark.tasks.await
import gov.nasa.jpl.pyre.spark.tasks.coroutineTask
import gov.nasa.jpl.pyre.spark.tasks.whenever

// TODO: Consider pulling PlanSimulation out of spark and into the next higher level (flame?)

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
class PlanSimulation<M : Model<M>>(setup: PlanSimulationSetup<M>) : Simulation {
    data class PlanSimulationSetup<M>(
        val reportHandler: (JsonValue) -> Unit,
        val inconProvider: InconProvider?,
        val constructModel: SparkInitContext.() -> M,
    )

    companion object {
        /**
         * The maximum number of iterations the simulation may perform without advancing in time,
         * before it is declared "stalled" and aborted.
         *
         * This provides protection against some kinds of infinitely looping tasks.
         */
        var SIMULATION_STALL_LIMIT: Int = 100
    }

    private val state: SimulationState
    private val activityResource: MutableDiscreteResource<GroundedActivity<M, *>?>

    init {
        with (setup) {
            state = SimulationState(reportHandler)

            val initContext = state.initContext()
            val sparkContext = object : SparkInitContext, SimulationInitContext by initContext {
                override val simulationClock = resource("simulation_clock", Timer(Duration.ZERO, 1), Timer.serializer())
            }
            with (sparkContext) {
                // Construct the model itself
                val model = constructModel()
                val activitySerializer = nullable(model.activitySerializer())

                // Construct the activity daemon
                // This reaction loop will build an activity whenever the directive resource is loaded.
                // Reacting to a resource like this plays nicely with the fincon - each iteration of the loop
                // is a function of the resource value (activity directive) read on that iteration.
                // If the activity is captured by a fincon, it will record the directive's serialization
                // in the task history, such that it can be re-launched when the simulation is restored.
                activityResource = discreteResource("activity_to_schedule", null, activitySerializer)
                spawn("activities", whenever(activityResource notEquals null) {
                    val groundedActivity = requireNotNull(activityResource.getValue())
                    activityResource.set(null)
                    InternalLogger.log("Scheduling activity ${groundedActivity.name} @ ${groundedActivity.time}")
                    spawn(groundedActivity, model)
                })
            }

            // Now that the root tasks are in place, we can restore the simulation
            inconProvider?.let(state::restore)
        }
    }

    override fun runUntil(time: Duration) {
        require(time >= state.time()) {
            "Simulation time is currently ${state.time()}, cannot step backwards to $time"
        }
        var n = 0
        while (state.time() < time) {
            if (++n > SIMULATION_STALL_LIMIT) {
                throw IllegalStateException("Simulation has stalled at ${state.time()} after $n iterations.")
            }
            state.stepTo(time)
        }
    }

    override fun save(finconCollector: FinconCollector) {
        state.save(finconCollector)
    }

    fun addActivities(activities: List<GroundedActivity<M, *>>) {
        InternalLogger.block("Loading ${activities.size} activities") {
            // TODO: Test this activityDirective trickery
            // TODO: If it works, consider formalizing it a bit more as a way to "safely" ingest info into the sim.
            val activitiesToLoad = activities.toMutableList()
            var activityLoaderActive = true

            // The directive loader will iteratively pull directives off the queue
            // and set them in the activityDirectiveResource.
            // The activity launcher will react to this by constructing and launching the activity.
            // That nulls out the resource, allowing this task to load the next resource.

            // Note that because this task depends on state not captured in a cell, it is not "safe" for simulation.
            // However, because it works in conjunction with the activity launcher, it will always complete
            // before the simulation advances in time.
            // Combined with the loop below to exercise this task to completion, thereby unloading this unsafe task,
            // the simulation is always in a safe state to save/restore when this function returns.
            state.addTask("activity loader", coroutineTask {
                if (activitiesToLoad.isEmpty()) {
                    activityLoaderActive = false
                    TaskScopeResult.Complete(Unit)
                } else {
                    await(activityResource equals null)
                    val a = activitiesToLoad.removeFirst()
                    InternalLogger.log("Loading activity ${a.name} @ ${a.time}")
                    activityResource.set(a)
                    TaskScopeResult.Restart()
                }
            })

            // Now, actually load the plan by cycling the simulation without advancing it.
            while (activityLoaderActive) state.stepTo(state.time())
        }
        InternalLogger.log("Finished loading ${activities.size} activities")
    }

    fun runPlan(plan: Plan<M>) {
        InternalLogger.block("Running plan ${plan.name}") {
            require(plan.startTime < plan.endTime) {
                "Mal-formed plan starts at ${plan.startTime}, after it ends at ${plan.endTime}"
            }
            require(plan.startTime == state.time()) {
                "Cannot run plan starting at ${plan.startTime}. Simulation is at ${state.time()}"
            }
            addActivities(plan.activities)
            runUntil(plan.endTime)
        }
    }
}