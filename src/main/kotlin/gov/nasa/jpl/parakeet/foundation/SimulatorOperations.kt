package gov.nasa.jpl.parakeet.foundation

import gov.nasa.jpl.parakeet.foundation.plans.Checkpoint
import gov.nasa.jpl.parakeet.foundation.plans.GroundedActivity
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler
import kotlin.time.Instant

object SimulatorOperations {
    operator fun <M : Any> Simulator<M>.plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun <M : Any> Simulator<M>.plusAssign(activities: Collection<GroundedActivity<M>>) = activities.forEach { this += it }
}