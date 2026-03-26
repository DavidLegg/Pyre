package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import kotlin.collections.plus
import kotlin.time.Instant

/**
 * Convenience overloads for common incremental simulation operations.
 */
object IncrementalSimulatorOperations {
    /*
     * "Single-operation" functions
     * These overloads allow scheduler code to concisely specify one operation, like
     * `simulator.move(activity1 to instant2)`
     */

    /** Add new activities */
    fun <M> IncrementalSimulator<M>.add(vararg activities: GroundedActivity<M>) =
        run(IncrementalSimulatorOperations.add(*activities))
    /** Remove existing activities */
    fun <M> IncrementalSimulator<M>.remove(vararg activities: GroundedActivity<M>) =
        run(IncrementalSimulatorOperations.remove(*activities))
    /** Edit activities' arguments */
    fun <M> IncrementalSimulator<M>.edit(vararg activities: Pair<GroundedActivity<M>, Activity<M>>) =
        run(IncrementalSimulatorOperations.edit(*activities))
    /** Change activities' start times */
    fun <M> IncrementalSimulator<M>.move(vararg activities: Pair<GroundedActivity<M>, Instant>) =
        run(IncrementalSimulatorOperations.move(*activities))

    /*
     * "Edit-composition" functions
     * These overloads provide the same kinds of operations as above, but return the [PlanEdits] rather than run them.
     * This allows scheduler code to group and combine operations to run in a single incremental update, like
     * `simulator.run(add(activity1).plus(remove(activity2)).plus(move(activity3 to instant4)))`
     *
     * Note that PlanEdits equipped with "+" is the free commutative group over GroundedActivity.
     * We can leverage this to provide natural addition/subtraction semantics for activities and edits.
     * Using those overloads, we can rewrite the example above to this:
     * `simulator.run(activity1 - activity2 + move(activity3 to instant4))`
     * The additive inverse is naturally thought of as the "undo" edit, giving rise to a pattern like this:
     * ```
     * val simulator: IncrementalSimulator<M> = ...
     * val edit: PlanEdit<M> = ...
     * // To try an edit:
     * simulator += edit
     * // To undo an edit:
     * simulator -= edit
     * ```
     */

    /** Combine two edits */
    operator fun <M> PlanEdits<M>.plus(other: PlanEdits<M>) =
        planEdits(this.additions + other.additions, this.removals + other.removals)
    operator fun <M> PlanEdits<M>.unaryPlus() = this

    /**
     * Construct the "undo" edit; add removals and remove additions.
     * If this.applyTo(P1) = P2, then (-this).applyTo(P2) = P1
     */
    operator fun <M> PlanEdits<M>.unaryMinus() = PlanEdits(removals, additions)
    operator fun <M> PlanEdits<M>.minus(other: PlanEdits<M>) = this + (-other)

    /** Build an edit to add activities */
    fun <M> add(vararg activities: GroundedActivity<M>) = PlanEdits(additions = activities.toList())
    operator fun <M> GroundedActivity<M>.unaryPlus() = add(this)
    operator fun <M> GroundedActivity<M>.plus(activity: GroundedActivity<M>) = add(this, activity)
    operator fun <M> GroundedActivity<M>.plus(edits: PlanEdits<M>) = add(this) + edits
    operator fun <M> PlanEdits<M>.plus(activity: GroundedActivity<M>) = this + add(activity)

    /** Build an edit to remove activities */
    fun <M> remove(vararg activities: GroundedActivity<M>) = PlanEdits(removals = activities.toList())
    operator fun <M> GroundedActivity<M>.unaryMinus() = remove(this)
    operator fun <M> GroundedActivity<M>.minus(activity: GroundedActivity<M>) = this + (-activity)
    operator fun <M> GroundedActivity<M>.minus(edits: PlanEdits<M>) = this + (-edits)
    operator fun <M> PlanEdits<M>.minus(activity: GroundedActivity<M>) = this + (-activity)

    /** Build an edit to change activities' arguments */
    fun <M> edit(vararg activities: Pair<GroundedActivity<M>, Activity<M>>) = planEdits(
        activities.map { it.first.copy(activity = it.second) },
        activities.map { it.first },
    )

    /** Build an edit to change activities' start times */
    fun <M> move(vararg activities: Pair<GroundedActivity<M>, Instant>) = planEdits(
        activities.map { it.first.copy(time = it.second) },
        activities.map { it.first },
    )

    /**
     * Normalize plan edits, cancelling out addition and removal of the same [GroundedActivity].
     */
    private fun <M> planEdits(
        additions: List<GroundedActivity<M>> = emptyList(),
        removals: List<GroundedActivity<M>> = emptyList(),
    ): PlanEdits<M> {
        val normalizedAdditions = additions.toMutableList()
        val normalizedRemovals = mutableListOf<GroundedActivity<M>>()
        for (removal in removals) {
            if (!normalizedAdditions.remove(removal)) {
                normalizedRemovals.add(removal)
            }
        }
        return PlanEdits(normalizedAdditions, normalizedRemovals)
    }

    /*
     * "Edit-application" overloads
     * These define how to apply edits to a plan, and give some convenience overloads for doing so.
     */

    /** Apply [this] edit to [plan] */
    fun <M> PlanEdits<M>.applyTo(plan: Plan<M>): Plan<M> {
        val remainingRemovals = removals.toMutableList()
        val newActivities = additions.toMutableList()
        for (activity in plan.activities) {
            if (!remainingRemovals.remove(activity)) {
                // Activity was not indicated for removal; add to newActivities
                newActivities += activity
            }
        }
        require(remainingRemovals.isEmpty()) {
            "Cannot remove ${remainingRemovals.size} activities which were not part of this plan: " +
                    remainingRemovals.joinToString(", ") { it.toString() }
        }
        return plan.copy(activities = plan.activities.filter { it !in removals } + additions)
    }
    operator fun <M> Plan<M>.plus(edits: PlanEdits<M>) = edits.applyTo(this)
    operator fun <M> PlanEdits<M>.plus(plan: Plan<M>) = plan + this
    operator fun <M> Plan<M>.minus(edits: PlanEdits<M>) = this + (-edits)
    operator fun <M> Plan<M>.plus(activity: GroundedActivity<M>) = this + (+activity)
    operator fun <M> GroundedActivity<M>.plus(plan: Plan<M>) = plan + this
    operator fun <M> Plan<M>.minus(activity: GroundedActivity<M>) = this + (-activity)

    operator fun <M> IncrementalSimulator<M>.plusAssign(edits: PlanEdits<M>) = run(edits)
    operator fun <M> IncrementalSimulator<M>.plusAssign(activity: GroundedActivity<M>) = add(activity)
    operator fun <M> IncrementalSimulator<M>.minusAssign(edits: PlanEdits<M>) = run(-edits)
    operator fun <M> IncrementalSimulator<M>.minusAssign(activity: GroundedActivity<M>) = remove(activity)
}