package gov.nasa.jpl.pyre.foundation.plans

object SimulatorOperations {
    operator fun <M : Any> Simulator<M>.plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun <M : Any> Simulator<M>.plusAssign(activities: Collection<GroundedActivity<M>>) = activities.forEach { this += it }
}