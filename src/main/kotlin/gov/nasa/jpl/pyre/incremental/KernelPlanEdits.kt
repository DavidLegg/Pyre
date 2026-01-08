package gov.nasa.jpl.pyre.incremental

data class KernelPlanEdits(
    val removals: List<KernelActivity> = emptyList(),
    val additions: List<KernelActivity> = emptyList(),
)

object KernelPlanEditOperations {
    fun KernelPlanEdits.applyTo(plan: KernelPlan): KernelPlan =
        plan.copy(activities = plan.activities.filter { it !in removals } + additions)
}
