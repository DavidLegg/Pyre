package gov.nasa.jpl.parakeet.general.scheduling

import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.plans.GroundedActivity
import gov.nasa.jpl.parakeet.foundation.plans.Plan
import gov.nasa.jpl.parakeet.examples.scheduling.GroundedActivity
import gov.nasa.jpl.parakeet.foundation.Simulator
import gov.nasa.jpl.parakeet.foundation.incremental.IncrementalSimulator
import gov.nasa.jpl.parakeet.foundation.incremental.IncrementalSimulatorImpl
import gov.nasa.jpl.parakeet.foundation.incremental.IncrementalSimulatorOperations.applyTo
import gov.nasa.jpl.parakeet.foundation.incremental.PlanEdits
import gov.nasa.jpl.parakeet.foundation.plans.Checkpoint
import gov.nasa.jpl.parakeet.foundation.resources.Dynamics
import gov.nasa.jpl.parakeet.foundation.resources.Resource
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.ProfileOperations.asResource
import gov.nasa.jpl.parakeet.general.results.ProfileOperations.getProfile
import gov.nasa.jpl.parakeet.general.results.SimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.clear
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler

object SchedulingOperations {
    fun <M : Any> SchedulingSystem<M>.addActivities(activities: Collection<GroundedActivity<M>>) =
        activities.forEach(::addActivity)

    fun <M : Any> SchedulingSystem<M>.addPlan(plan: Plan<M>) =
        addActivities(plan.activities)

    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activity: Activity<M>) = addActivity(GroundedActivity(time(), activity))
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activities: Collection<GroundedActivity<M>>) = addActivities(activities)
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(plan: Plan<M>) = addPlan(plan)

    // TODO: Build up SchedulingContext, and port profile- and value-access operations to this, using a SchedulingContext param.
    // TODO: Port compute over here using a SchedulingContext param.

    /**
     * Look up the profile for [this] resource in [schedulingScope], then replay it as a resource.
     */
    context (schedulingScope: SchedulingScope<M>, _: InitScope)
    fun <M, D: Dynamics<*, D>> Resource<D>.replay(): Resource<D> =
        schedulingScope.results.getProfile<D>(this.name).asResource()

    /**
     * Starting from [initialPlan], construct a new plan with the aid of simulation.
     *
     * Whenever [SchedulingScope.edit] is called, the entire plan will be resimulated and [SchedulingScope.results] updated.
     *
     * This provides a somewhat simpler interface than [SchedulingSystem], at the cost of losing some control.
     * In particular, there's no possibility of dropping checkpoints in the middle of the scheduling window
     * and re-simulating small portions of the plan.
     */
    fun <M : Any> schedule(
        initialPlan: Plan<M>,
        constructModel: context (InitScope) () -> M,
        incon: Checkpoint<M>? = null,
        block: context (SchedulingScope<M>) () -> Unit,
    ): Plan<M> {
        var workingPlan = initialPlan
        val results = MutableSimulationResults(initialPlan.startTime, initialPlan.endTime)
        lateinit var model: M

        fun resimulate() {
            results.clear()
            val simulator = Simulator(
                results.reportHandler(),
                workingPlan.startTime,
                incon = incon,
                constructModel = {
                    // Capture the model as we build it
                    constructModel().also { model = it }
                },
            )
            simulator.runPlan(workingPlan)
        }

        // Do one simulation before calling block, so we have initial results to work with.
        resimulate()

        block(object : SchedulingScope<M> {
            override val plan: Plan<M> get() = workingPlan
            override val model: M get() = model
            override val results: SimulationResults get() = results

            override fun edit(edits: PlanEdits<M>) {
                workingPlan = edits.applyTo(workingPlan)
                resimulate()
            }
        })

        return workingPlan
    }

    /**
     * Starting from [initialPlan], construct a new plan with the aid of simulation.
     *
     * Whenever [SchedulingScope.edit] is called, the plan will be resimulated and [SchedulingScope.results] updated.
     * Resimulation is done with an [IncrementalSimulator] to reduce the time spent resimulating.
     *
     * This provides the simpler interface of [schedule] (as compared to [SchedulingSystem]) without the performance penalties
     * associated with running the entire plan start to finish for every edit.
     * However, models must be compatible with incremental simulation, which is less forgiving of non-compliant models
     * than single-shot simulation.
     */
    fun <M : Any> scheduleIncrementally(
        initialPlan: Plan<M>,
        constructModel: context (InitScope) () -> M,
        incon: Checkpoint<M>? = null,
        block: context (SchedulingScope<M>) () -> Unit,
    ): Plan<M> {
        lateinit var model: M

        val simulator = IncrementalSimulatorImpl(
            constructModel = { constructModel().also { model = it } },
            plan = initialPlan,
            incon = incon,
        )

        // Incremental simulators run automatically when constructed,
        // no need to explicitly ask for an initial simulation.

        block(object : SchedulingScope<M> {
            override val plan: Plan<M> get() = simulator.plan
            override val model: M get() = model
            override val results: SimulationResults get() = simulator.results

            override fun edit(edits: PlanEdits<M>) {
                simulator.run(edits)
            }
        })

        return simulator.plan
    }
}