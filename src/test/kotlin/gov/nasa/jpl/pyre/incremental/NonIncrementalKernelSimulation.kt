package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.incremental.KernelPlanEditOperations.applyTo
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.allocate
import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.spawn
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.SatisfiedAt
import gov.nasa.jpl.pyre.kernel.SimpleSimulation
import gov.nasa.jpl.pyre.kernel.SimpleSimulation.SimulationSetup
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.kernel.plus
import kotlin.reflect.typeOf

/**
 * Not-actually-incremental implementation of [IncrementalKernelSimulation].
 * This is the baseline correct behavior for an incremental simulator, without the complexity of actually being incremental.
 */
class NonIncrementalKernelSimulation(
    val endTime: Duration,
    val initializeModel: context (BasicInitScope) () -> Unit,
) : IncrementalKernelSimulation {
    override var plan: KernelPlan = KernelPlan(emptyList())
        private set

    override fun run(planEdits: KernelPlanEdits) {
        plan = planEdits.applyTo(plan)
        _reports.clear()
        val simulation = SimpleSimulation(SimulationSetup(_reports::add) {
            initializeModel()
            // Allocate a simple clock cell.
            // Since we're working down at the kernel level, we don't have access to the foundation level simulationClock.
            val clock = allocate(
                Name("activityClock"),
                ZERO,
                typeOf<Duration>(),
                Duration::plus,
                { e1, e2 ->
                    throw IllegalStateException("activityClock should not have effects")
                }
            )
            for (activity in plan.activities) {
                spawn(activity.name) {
                    // Await the designated activity time
                    Task.PureStepResult.Await({
                        SatisfiedAt(activity.time - it.read(clock))
                    }) {
                        // And run the activity
                        activity.task(it)
                    }
                }
            }
        })
        simulation.runUntil(endTime)
    }

    private val _reports: MutableList<Any?> = mutableListOf()
    override val reports: List<Any?>
        get() = _reports
}