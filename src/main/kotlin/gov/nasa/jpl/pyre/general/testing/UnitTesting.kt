package gov.nasa.jpl.pyre.general.testing

import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.assumeType
import gov.nasa.jpl.pyre.general.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.time.Instant

object UnitTesting {
    /**
     * Run a simulation as a unit test.
     * Test ends when [testTask] completes.
     *
     * Activity spans and resource reports are collected in memory.
     *
     * [testTask] may spawn activities to simulate a plan, or produce effects directly.
     * Assertions may be made directly by [testTask] during simulation, or afterward on the results.
     */
    inline fun <reified M> runUnitTest(
        simulationStart: Instant,
        noinline constructModel: InitScope.() -> M,
        noinline testTask: suspend context (TaskScope) (M) -> Unit
    ): SimulationResults {
        val resources: MutableMap<Name, MutableList<ChannelizedReport<*>>> = mutableMapOf()
        val activitySpans: MutableMap<Activity<*>, ActivityEvent> = mutableMapOf()
        val reportHandler: ReportHandler = channels(
            Name("activities") to (assumeType<ActivityEvent>() andThen { (value, type) ->
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
        var testTaskComplete = false
        val simulation = PlanSimulation.withoutIncon(
            reportHandler,
            simulationStart,
            simulationStart,
            {
                val model = constructModel()
                spawn("Test Task", task {
                    testTask(model)
                    testTaskComplete = true
                })
            }
        )
        while (!testTaskComplete) simulation.stepTo(Instant.DISTANT_FUTURE)
        return SimulationResults(
            simulationStart,
            simulation.time(),
            resources,
            activitySpans,
        )
    }
}